## [2026-06-14 10:06] | Task: Harden the bridge's get_app_state (uiautomator dump flakiness)

### 🤖 Execution Context
* **Agent ID**: `claude-code`
* **Base Model**: `claude-opus-4-8`
* **Runtime**: Claude Code on the web (Go IS buildable/testable here)

### 📥 User Query
> What's the issue with get_app_state? Research best practices and fix it before
> merging PR #9 (and land it as a commit on PR #9).

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUse` (Go host-side bridge), smoke script, docs.

**Diagnosis:** the CI emulator-smoke step 5 intermittently failed on a freshly
booted emulator. Root cause: the bridge captures the UI via `adb shell uiautomator
dump`, which is intermittently unreliable while the UI is still initializing /
animating — it times out waiting for idle ("could not get idle state") or the
accessibility bridge returns a null root node, and it's more prevalent on Android
11+ (the emulator is API 30). The bridge retried only once with a flat 500ms.
Web research (Appium/AOSP/testing-samples threads) confirms: retry with backoff +
let the UI settle/idle; the robust alternative is the accessibility tree (the
companion path already uses it). The no-companion bridge must use uiautomator
dump, so it needs proper retries.

**Key Actions:**
- `device.go`: `uiautomatorDump()` now retries with **escalating backoff**
  (4 attempts; waits 500ms/1s/2s between them) and returns a clearer
  "failed after N attempts" error. Bumped `launchWaitTimeout` 6s → 10s for cold
  boot. (`uiautomatorDumpOnce` unchanged — it already detects command failure and
  empty/stale `<hierarchy>`.)
- `device_test.go`: added `TestUIAutomatorDumpEscalatingRetries` (3 fails →
  success, attempts = 4) and `TestUIAutomatorDumpExhaustsRetries` (always fails →
  error mentions "attempts"); kept `TestUIAutomatorDumpRetriesOnce`.
- `scripts/run-android-smoke-tests.sh`: step 5 now echoes the tool output on
  failure so the error JSON is visible in CI (was swallowed).
- Docs: roadmap risk note, QUALITY_SCORE Android-runtime row.

### 🧠 Design Intent (Why)
Fix it in the bridge so every real user benefits, not just CI; a cold-boot/busy UI
usually settles within a second or two, so escalating retries resolve the common
case without slowing the happy path (attempt 1 is immediate). Kept the change
small and avoided `--compressed` (changes tree content) and an accessibility-based
bridge rewrite (large; the companion path already covers that need).

### ✅ Verification
Locally (Go available): `gofmt -l` empty, `go vet ./...` clean, `go test ./...`
green (incl. the 3 dump tests), `make android-build` succeeds. CI `test` job
re-runs these; `emulator-smoke` step 5 should now survive cold boot.

### 📁 Files Modified
- `apps/OpenAndroidUse/device.go`, `apps/OpenAndroidUse/device_test.go`
- `scripts/run-android-smoke-tests.sh`
- `docs/exec-plans/active/20260612-android-use-runtime.md`, `docs/QUALITY_SCORE.md`
