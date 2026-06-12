# VERIFICATION.md — on-device verification ledger

> Working checklist of everything that must be verified on real hardware (or an
> emulator) because the dev container has no Android device attached. Each item
> says exactly what to run and what "pass" looks like. Check items off as you go;
> this file is deleted once everything passes and the results are recorded in
> `docs/histories/`.

> **CI automation:** `scripts/run-android-smoke-tests.sh` (CI: the
> `emulator-smoke` job in `.github/workflows/android-runtime.yml`, which boots a
> real API-30 emulator) automates the scriptable subset — roughly V1–V5, V7,
> V14, V20–V23, V26, V29, and the V27 routing guard. A green emulator-smoke run
> is strong evidence, but the real-hardware pass below remains authoritative
> (OEM ROMs, IMEs, secure surfaces, and touch behavior differ).

## How to set up

1. Install [Android platform-tools](https://developer.android.com/tools/releases/platform-tools); `adb version` should work.
2. Enable Developer Options + USB debugging on the device (or start an emulator:
   `emulator -avd <name>`).
3. `adb devices` shows the device as `device` (not `unauthorized`).
4. Build the bridge: `make android-build` → `dist/android-bridge/<os>/<arch>/open-android-use`.

Set `OAU=dist/android-bridge/<os>/<arch>/open-android-use` for the steps below.

## Phase 1 — Android bridge (ADB runtime)

- [ ] **V1. Doctor**: `$OAU doctor`
  Pass: prints adb version, lists the device serial, shows `selected:` and a
  plausible `foreground:` package/activity.
- [ ] **V2. Device listing**: `$OAU devices`
  Pass: one line per connected device.
- [ ] **V3. App listing**: `$OAU call list_apps`
  Pass: `Running in foreground:` matches what's on screen; launcher apps listed.
- [ ] **V4. Snapshot of the foreground app**: `$OAU snapshot foreground`
  Pass: tree lines with indexed `[n]` actionable elements; labels match the screen.
- [ ] **V5. Snapshot launches a backgrounded app**: pick an app not on screen,
  e.g. `$OAU call get_app_state --args '{"app":"settings"}'`
  Pass: the app visibly comes to the foreground; result contains tree + screenshot
  (base64 PNG block in the JSON).
- [ ] **V6. Screenshot budget & coordinate alignment**: decode the base64 PNG from
  V5 (`jq -r '.content[1].data' | base64 -d > shot.png`).
  Pass: PNG ≤ ~900KB, long edge ≤ 1280px, and element frames from the tree line up
  with what's drawn at those pixel positions in the PNG (spot-check 2–3 elements).
- [ ] **V7. Element click**: in Settings, click an indexed row via
  `$OAU call --calls '[{"tool":"get_app_state","args":{"app":"settings"}},{"tool":"click","args":{"app":"settings","element_index":"<idx>"}}]'`
  Pass: the right row opens; post-action snapshot reflects the new screen.
- [ ] **V8. Coordinate click**: click by `x`/`y` taken from a screenshot pixel
  position. Pass: tap lands on the element at that position in the PNG (this is
  the CoordinateScale invariant on real hardware — the critical check).
- [ ] **V9. Long-press**: `click` with `"mouse_button":"right"` on a home-screen
  icon or list item. Pass: context menu / long-press behavior triggers.
- [ ] **V10. Scroll**: `scroll` with `direction: "down"` on a scrollable element
  (e.g. Settings list), then `pages: 0.5`. Pass: list scrolls; half page moves
  visibly less than a full page.
- [ ] **V11. Drag**: `drag` a home-screen icon between positions.
  Pass: icon moves (draganddrop) — or at minimum the swipe-fallback drag occurs.
- [ ] **V12. type_text (ASCII)**: focus a search box (click it first), then
  `type_text` with `"hello world 123"`. Pass: exact text appears, spaces intact.
- [ ] **V13. type_text non-ASCII guard**: `type_text` with `"héllo"`.
  Pass: clean error mentioning ASCII + on-device companion; nothing typed.
- [ ] **V14. press_key basics**: `press_key` with `"Back"`, then `"Return"` in a
  text field, then `"BackSpace"`. Pass: each key has its expected effect.
- [ ] **V15. press_key combination** (Android 13+): `press_key` with `"ctrl+a"` in
  a focused text field. Pass: text selects (or a clear error on older Android).
- [ ] **V16. set_value**: on an EditText element index, `set_value` with
  `"replaced"`. Pass: prior text replaced on Android 13+; on older devices the
  value may be appended (known limitation, note the OS version).
- [ ] **V17. MCP end-to-end**: add the bridge to a real MCP client (Claude Code:
  `claude mcp add open-android-use -- $OAU mcp`) and ask the agent to open
  Settings and toggle something benign. Pass: full agent loop works.
- [ ] **V18. Multi-device selection**: with two devices/emulators attached, verify
  the bridge refuses without `OPEN_ANDROID_USE_SERIAL` and obeys it when set.
- [ ] **V19. uiautomator flake handling**: hammer `$OAU snapshot foreground` ~10x
  in a row. Pass: no hard failures (the one-retry path absorbs transient empties).

## Phase 2 — On-device companion

Build the APK first: `ANDROID_HOME=<sdk> make companion-build`
→ `dist/companion/open-android-use-companion.apk` (also published as a CI
artifact by `.github/workflows/android-runtime.yml`).

- [ ] **V20. Companion APK installs**: `adb install dist/companion/open-android-use-companion.apk`
  Pass: installs on Android 8.0+ (minSdk 26); "Open Android Use Companion"
  appears in the launcher.
- [ ] **V21. Accessibility service enable flow**: open the companion app, tap
  "Open Accessibility Settings", enable "Open Android Use Companion", return.
  Pass: status shows "Service: running — endpoint live on 127.0.0.1:8355".
- [ ] **V22. Companion endpoint over adb forward**:
  `adb forward tcp:8355 tcp:8355` then `curl http://127.0.0.1:8355/health`
  Pass: `{"ok":true,"service":"open-android-use-companion","version":"0.2.3","protocol":1,"screenshot":<bool>}`
  (`screenshot` true on Android 11+).
- [ ] **V23. Companion snapshot**: `curl http://127.0.0.1:8355/snapshot`
  Pass: `{"ok":true,"protocol":1,"package":"<foreground>","tree":{...}}` — spot
  check that `tree` labels/bounds match the screen.
- [ ] **V24. Companion gesture via HTTP**: pick a visible button's center from
  V23 bounds, then
  `curl -X POST -d '{"type":"tap","x":<x>,"y":<y>}' http://127.0.0.1:8355/action`
  Pass: `{"ok":true}` and the tap visibly lands. Repeat with `longPress` and a
  `swipe`.
- [ ] **V25. Companion screenshot** (Android 11+):
  `curl http://127.0.0.1:8355/screenshot -o companion.png`
  Pass: valid full-resolution PNG of the current screen.
- [ ] **V26. Bridge detects companion**: with the service enabled, run
  `$OAU doctor`. Pass: a `companion: available ... (v0.2.3, protocol 1, via
  127.0.0.1:8355)` line (the bridge sets up the adb forward itself).
- [ ] **V27. Unicode typing through the bridge** (headline Phase 2 capability):
  focus a text field (click it via the bridge), then
  `OPEN_ANDROID_USE_COMPANION=1 $OAU call --calls '[{"tool":"get_app_state","args":{"app":"foreground"}},{"tool":"type_text","args":{"app":"foreground","text":"héllo 🚀 你好"}}]'`
  Pass: the exact text appears in the field (ACTION_SET_TEXT path).
- [ ] **V28. Kill switch**: disable the accessibility service mid-session.
  Pass: companion-mode `type_text` with non-ASCII fails fast with a clear
  "Companion is not reachable" error; ASCII text still works (silent fallback to
  the ADB path); `$OAU doctor` reports `companion: not available`.

### Phase 2.1 — companion mode through the bridge

With the service enabled and `OPEN_ANDROID_USE_COMPANION=1` exported:

- [ ] **V29. Companion-backed snapshot through the bridge**:
  `OPEN_ANDROID_USE_COMPANION=1 $OAU snapshot foreground`
  Pass: same output shape as V4 but sourced from the companion's live tree — no
  `uiautomator dump` lag, and it works while a text field has IME focus
  (uiautomator's weakness). Confirm via `adb logcat -s OpenAndroidUse` that the
  snapshot came from the companion.
- [ ] **V30. Companion-backed gestures through the bridge**: repeat V7 (element
  click), V9 (long-press), V10 (scroll), and V11 (drag) with companion mode on.
  Pass: identical or better behavior; gestures land mid-animation too.
- [ ] **V31. Companion set_value with Unicode**: on an EditText element index,
  `set_value` with `"héllo 🚀 你好"`.
  Pass: prior text fully replaced (ACTION_SET_TEXT) with the exact Unicode value
  on any Android version — no ctrl+a dependency.
- [ ] **V32. Degradation matrix**: kill the companion mid-session and repeat
  V29/V30. Pass: snapshots and gestures silently fall back to the
  uiautomator/ADB path (slower but correct); only non-ASCII typing errors out.

## Results log

| Date | Device / Android version | Items run | Notes |
|---|---|---|---|
| _(fill in)_ | | | |
