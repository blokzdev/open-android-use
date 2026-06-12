## [2026-06-12 05:40] | Task: Emulator smoke runner — automated end-to-end verification in CI

### 🤖 Execution Context
* **Agent ID**: Claude Code (cloud session, continuation of the Android pivot)
* **Base Model**: claude-fable-5
* **Runtime**: Claude Code remote execution container (no KVM — emulator runs in CI, not locally)

### 📥 User Query
> Loop on (continue autonomously per the approved plan).

### 🛠 Changes Overview
**Scope:** `scripts/run-android-smoke-tests.sh` (new), CI workflow, Makefile, docs.

**Key Actions:**
- **[Smoke runner]**: `scripts/run-android-smoke-tests.sh` — device-agnostic
  end-to-end smoke against any booted device/emulator. Deliberately
  non-destructive (opens Settings, snapshots, presses Back). Steps: boot wait,
  doctor, devices, list_apps, get_app_state (tree + screenshot + indexed
  elements asserted), call sequence with press_key Back; with
  `--with-companion`: APK install, accessibility service enabled via
  `adb shell settings put secure` (no consent screen on emulators), /health,
  doctor companion line, companion-mode snapshot, and a Unicode-routing guard
  (asserts the companion path, not the ASCII guard, handles non-ASCII).
- **[CI]**: `emulator-smoke` job in `android-runtime.yml` — enables KVM on the
  GitHub-hosted runner, builds bridge + companion, boots a headless API-30
  x86_64 emulator (reactivecircus/android-emulator-runner@v2), runs the smoke
  with companion. 35-minute timeout.
- **[Harness]**: `make android-smoke`; VERIFICATION.md notes which checklist
  items the CI smoke automates (hardware run remains authoritative); exec plan
  and QUALITY_SCORE updated.

### 🧠 Design Intent (Why)
The dev container has no KVM, so true end-to-end validation moves to CI where
emulators are first-class. One script serves three audiences: CI (emulator),
the founder (real hardware, same command), and future contributors. The
companion's accessibility service can be enabled via adb settings on
emulators, which makes the *full* companion path CI-testable — including the
headline Unicode capability — without a human consent tap.

### 📁 Files Modified
- `scripts/run-android-smoke-tests.sh`
- `.github/workflows/android-runtime.yml`
- `Makefile`
- `VERIFICATION.md`
- `docs/ARCHITECTURE.md`, `docs/QUALITY_SCORE.md`
- `docs/exec-plans/active/20260612-android-use-runtime.md`
