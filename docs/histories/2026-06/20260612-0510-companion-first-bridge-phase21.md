## [2026-06-12 05:10] | Task: Phase 2.1 — companion-first snapshots, gestures, and set_value through the bridge

### 🤖 Execution Context
* **Agent ID**: Claude Code (cloud session, continuation of the Android pivot)
* **Base Model**: claude-fable-5
* **Runtime**: Claude Code remote execution container (Linux; no device attached)

### 📥 User Query
> Continue autonomously (long-running build); on-device verification steps go
> into VERIFICATION.md.

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUse` (Go bridge); docs and verification ledger.

**Key Actions:**
- **[Shared tree renderer]**: Extracted `snapshotTreeBuilder` so the uiautomator
  XML walker and the new companion-JSON walker (`flattenCompanionTree`) emit
  byte-identical tree lines / element records; existing tests pinned the format
  through the refactor.
- **[Companion-first snapshots]**: With `OPEN_ANDROID_USE_COMPANION=1`,
  `get_app_state` uses the companion's live `rootInActiveWindow` tree plus its
  screenshot endpoint (adb `screencap` fallback below Android 11); any companion
  failure silently degrades to the uiautomator path.
- **[Companion-first gestures]**: `click`/long-press/`scroll`/`drag` route
  through `dispatchGesture` (`/action` tap, longPress, swipe) with ADB `input`
  as fallback; `set_value` becomes tap + `ACTION_SET_TEXT` — whole-value
  replace, full Unicode, no ctrl+a dependency.
- **[Tests + ledger]**: 7 new httptest-backed tests (format parity, coordinate
  pass-through, screenshot fallback, uiautomator degradation, no-ADB-when-
  companion-handles assertions); `VERIFICATION.md` gains V29–V32 and drops the
  "Phase 2.1 not built" notice.

### 🧠 Design Intent (Why)
The companion's value is only real if the model cannot tell which source served
the snapshot — hence the shared renderer rather than two formats. Degradation is
deliberately asymmetric: snapshots/gestures fall back silently (ADB covers the
same surface, slower), but non-ASCII typing surfaces the companion error because
the ADB path cannot represent the text and silent mangling is worse than failing.

### 📁 Files Modified
- `apps/OpenAndroidUse/{companion.go,device.go,companion_test.go}`
- `VERIFICATION.md`
- `docs/design-docs/on-device-companion.md`
- `docs/ARCHITECTURE.md`, `docs/QUALITY_SCORE.md`, `README.md`
- `docs/exec-plans/active/20260612-android-use-runtime.md`
