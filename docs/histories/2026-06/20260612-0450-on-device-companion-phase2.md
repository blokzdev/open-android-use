## [2026-06-12 04:50] | Task: Phase 2.0 — on-device companion app, bridge integration, verification ledger

### 🤖 Execution Context
* **Agent ID**: Claude Code (cloud session, continuation of the Android pivot)
* **Base Model**: claude-fable-5
* **Runtime**: Claude Code remote execution container (Linux; Android SDK 35 + Gradle 8.14 installed in-session; no device attached)

### 📥 User Query
> Keep going autonomously; log all on-device verification steps in a
> VERIFICATION.md (cleared at the very end); give it a long-running build.

### 🛠 Changes Overview
**Scope:** new `apps/OpenAndroidUseCompanion` (Kotlin); companion client in
`apps/OpenAndroidUse` (Go); repo docs/CI/build entries; `VERIFICATION.md`.

**Key Actions:**
- **[Verification ledger]**: Added root `VERIFICATION.md` — V1–V19 cover the
  Phase 1 ADB bridge on hardware (incl. the CoordinateScale invariant check),
  V20–V28 cover the companion; Phase 2.1 items explicitly marked "not built yet".
  File is deleted once hardware-verified, results archived to histories.
- **[Companion app]**: `apps/OpenAndroidUseCompanion` — zero-third-party-
  dependency Kotlin app: `AccessibilityService` + loopback-only HTTP server
  (port 8355) speaking protocol v1 (`/health`, `/snapshot` live tree,
  `/screenshot` on Android 11+, `/action` tap/longPress/swipe/setText/global),
  consent UI + kill switch via system settings. Design doc:
  `docs/design-docs/on-device-companion.md`. APK built successfully in-container
  (832KB debug) after installing SDK 35; `make companion-build` stages it to
  `dist/companion/open-android-use-companion.apk`.
- **[Bridge integration]**: `companion.go` — protocol-checked `/health` probe
  (auto `adb forward`), `doctor` reports availability, and
  `OPEN_ANDROID_USE_COMPANION=1` routes `type_text` through `ACTION_SET_TEXT`
  for full Unicode, falling back to ADB only for ASCII-representable text.
  7 new httptest-backed unit tests; suite green, gofmt/vet clean.
- **[CI]**: `android-runtime.yml` gains a `companion` job (ubuntu runner SDK,
  uploads the APK as an artifact); path filters extended.

### 🧠 Design Intent (Why)
The companion removes exactly what ADB transport cannot do (Unicode typing,
fast live trees, gestures during IME focus) while keeping the smallest possible
trust surface: no third-party deps, loopback-only socket, OS-gated consent.
Bridge usage is opt-in until hardware verification so default behavior stays
predictable; doctor advertises it so it gets discovered. VERIFICATION.md exists
because container CI cannot substitute for hardware — every claim the docs make
about device behavior is listed there as a falsifiable step.

### 📁 Files Modified
- `apps/OpenAndroidUseCompanion/**` (new Gradle project, 5 Kotlin files, manifest/res)
- `apps/OpenAndroidUse/{companion.go,companion_test.go,device.go,device_test.go}`
- `scripts/build-open-android-use-companion.sh`, `Makefile`, `.gitignore`
- `.github/workflows/android-runtime.yml`
- `VERIFICATION.md`
- `docs/design-docs/on-device-companion.md`
- `docs/ARCHITECTURE.md`, `docs/QUALITY_SCORE.md`, `README.md`
- `docs/exec-plans/active/20260612-android-use-runtime.md`
