#!/usr/bin/env bash

# End-to-end smoke for the Android bridge (and optionally the companion)
# against a booted device or emulator reachable over adb. Automates the
# scriptable subset of VERIFICATION.md; safe-by-design: it only opens
# Settings, presses Back, and reads state — no data is changed on the device.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
bridge=""
apk=""
with_companion=0
agent_test_apk=""
companion_port="${OPEN_ANDROID_USE_COMPANION_PORT:-8355}"

print_help() {
  cat <<'EOF'
Usage:
  scripts/run-android-smoke-tests.sh [options]

Requires adb on PATH and a single booted device/emulator (or
OPEN_ANDROID_USE_SERIAL set). Only performs read-only-ish actions:
opens Settings, takes snapshots, presses Back.

Options:
  --bridge <path>      Bridge binary. Defaults to building it via
                       scripts/build-open-android-use.sh.
  --with-companion     Also install + enable the companion (emulator/CI only:
                       enables the accessibility service via adb settings,
                       which skips the human consent screen) and smoke
                       companion mode.
  --apk <path>         Companion APK for --with-companion. Defaults to
                       dist/companion/open-android-use-companion.apk.
  --agent-test-apk <path>
                       Instrumentation APK (gradle assembleDebugAndroidTest)
                       for the on-device agent-loop smoke. Implies
                       --with-companion. Installed as its own package and run
                       via `am instrument`, so the companion app is never
                       reinstalled — reinstalling would unbind the enabled
                       accessibility service.
  -h, --help           Show help.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --bridge) bridge="${2:?--bridge requires a value}"; shift 2 ;;
    --apk) apk="${2:?--apk requires a value}"; shift 2 ;;
    --with-companion) with_companion=1; shift ;;
    --agent-test-apk) agent_test_apk="${2:?--agent-test-apk requires a value}"; with_companion=1; shift 2 ;;
    -h|--help) print_help; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; print_help >&2; exit 1 ;;
  esac
done

step=0
pass() { echo "PASS  $1"; }
fail() { echo "FAIL  $1" >&2; exit 1; }
run_step() {
  step=$((step + 1))
  echo "--- [smoke $step] $1"
}

command -v adb >/dev/null || fail "adb not on PATH"

if [[ -z "${bridge}" ]]; then
  run_step "build bridge"
  "${repo_root}/scripts/build-open-android-use.sh" >/dev/null
  bridge="$(ls "${repo_root}"/dist/android-bridge/*/*/open-android-use | head -1)"
  pass "built ${bridge}"
fi
[[ -x "${bridge}" ]] || fail "bridge binary not executable: ${bridge}"

run_step "wait for device boot"
adb wait-for-device
for _ in $(seq 1 60); do
  if [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
    break
  fi
  sleep 2
done
[[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == "1" ]] || fail "device did not finish booting"
adb shell input keyevent 82 >/dev/null 2>&1 || true  # dismiss lock screen on emulators
pass "device booted"

run_step "doctor"
doctor_output="$("${bridge}" doctor)"
echo "${doctor_output}"
grep -q "adb: " <<<"${doctor_output}" || fail "doctor did not report adb"
grep -q "selected: " <<<"${doctor_output}" || fail "doctor did not select a device"
pass "doctor"

run_step "devices"
"${bridge}" devices | grep -q . || fail "devices output empty"
pass "devices"

run_step "list_apps"
apps_output="$("${bridge}" call list_apps)"
grep -q "Launchable apps:" <<<"${apps_output}" || fail "list_apps missing app list"
grep -q "com.android.settings" <<<"${apps_output}" || fail "list_apps missing Settings"
pass "list_apps"

run_step "get_app_state launches Settings and returns tree + screenshot"
state_output="$("${bridge}" call get_app_state --args '{"app":"com.android.settings"}')"
grep -q "App=com.android.settings" <<<"${state_output}" || fail "snapshot missing App= line"
grep -q '"type": "image"' <<<"${state_output}" || fail "snapshot missing screenshot block"
grep -Eq '\[1\] ' <<<"${state_output}" || fail "snapshot has no indexed elements"
pass "get_app_state"

run_step "call sequence: snapshot foreground + press Back"
"${bridge}" call --calls '[
  {"tool":"get_app_state","args":{"app":"foreground"}},
  {"tool":"press_key","args":{"app":"foreground","key":"Back"}}
]' >/dev/null || fail "call sequence failed"
pass "sequence + press_key Back"

if [[ "${with_companion}" == "1" ]]; then
  [[ -n "${apk}" ]] || apk="${repo_root}/dist/companion/open-android-use-companion.apk"
  [[ -f "${apk}" ]] || fail "companion APK not found: ${apk} (run make companion-build)"

  run_step "install companion APK"
  adb install -r "${apk}" >/dev/null
  pass "installed"

  run_step "enable accessibility service via adb settings"
  adb shell settings put secure enabled_accessibility_services \
    dev.openandroiduse.companion/dev.openandroiduse.companion.CompanionService
  adb shell settings put secure accessibility_enabled 1
  sleep 4
  pass "service enabled"

  run_step "companion /health over adb forward"
  adb forward "tcp:${companion_port}" "tcp:${companion_port}"
  health="$(curl -fsS --max-time 10 "http://127.0.0.1:${companion_port}/health")" \
    || fail "companion health endpoint unreachable"
  echo "${health}"
  grep -q '"service":"open-android-use-companion"' <<<"${health}" || fail "unexpected health payload"
  pass "health"

  run_step "doctor reports companion"
  "${bridge}" doctor | grep -q "companion: available" || fail "doctor did not detect companion"
  pass "doctor companion line"

  run_step "companion-mode snapshot through the bridge"
  OPEN_ANDROID_USE_COMPANION=1 "${bridge}" call get_app_state \
    --args '{"app":"com.android.settings"}' | grep -q "App=com.android.settings" \
    || fail "companion-mode snapshot failed"
  pass "companion snapshot"

  run_step "companion-mode Unicode type_text guard path"
  # No focused text field: the companion must return its actionable error
  # rather than the ASCII guard (which would mean the companion was bypassed).
  set +e
  unicode_output="$(OPEN_ANDROID_USE_COMPANION=1 "${bridge}" call --calls '[
    {"tool":"get_app_state","args":{"app":"com.android.settings"}},
    {"tool":"type_text","args":{"app":"com.android.settings","text":"héllo"}}
  ]' 2>&1)"
  set -e
  grep -q "ASCII" <<<"${unicode_output}" && fail "companion mode fell back to the ASCII guard"
  pass "unicode routed to companion"
fi

if [[ -n "${agent_test_apk}" ]]; then
  [[ -f "${agent_test_apk}" ]] || fail "agent test APK not found: ${agent_test_apk} (run gradle assembleDebugAndroidTest)"

  run_step "install agent instrumentation APK (companion app untouched)"
  adb install -r "${agent_test_apk}" >/dev/null
  pass "test APK installed"

  run_step "agent loop smoke via am instrument (stub model server, no API key)"
  # `am instrument` restarts the companion process; re-assert the secure
  # settings so the accessibility manager re-binds the service into the
  # instrumented process (the test itself also polls up to 30s for it).
  adb shell settings put secure enabled_accessibility_services \
    dev.openandroiduse.companion/dev.openandroiduse.companion.CompanionService
  adb shell settings put secure accessibility_enabled 1
  sleep 2
  # `am instrument -w` exits 0 even when tests fail; assert on the output.
  instrument_output="$(adb shell am instrument -w -e requireCompanion true \
    dev.openandroiduse.companion.test/androidx.test.runner.AndroidJUnitRunner 2>&1)"
  echo "${instrument_output}"
  grep -q "FAILURES!!!" <<<"${instrument_output}" && fail "agent loop instrumentation reported failures"
  grep -q "INSTRUMENTATION_FAILED" <<<"${instrument_output}" && fail "instrumentation did not run"
  grep -Eq "OK \([0-9]+ test" <<<"${instrument_output}" || fail "instrumentation output missing OK marker"
  pass "agent loop smoke"
fi

echo
echo "Android smoke: all ${step} steps passed."
