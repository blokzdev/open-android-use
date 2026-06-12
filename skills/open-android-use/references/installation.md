# Installation

## Host requirements

1. **Android platform-tools** (provides `adb`):
   - macOS: `brew install android-platform-tools`
   - Debian/Ubuntu: `sudo apt install android-tools-adb`
   - Or download from https://developer.android.com/tools/releases/platform-tools
   Verify with `adb version`.
2. **The bridge binary** `open-android-use`:
   - From a repo checkout: `make android-build` →
     `dist/android-bridge/<os>/<arch>/open-android-use` (requires Go 1.22+), or
     grab the CI artifact from the `android-runtime` workflow.
   - Put it on PATH or reference it by absolute path in MCP configs.

## Device setup

1. On the phone: Settings → About phone → tap "Build number" 7× to unlock
   Developer options, then enable **USB debugging**.
2. Connect over USB and accept the "Allow USB debugging?" prompt on the device.
3. `adb devices` must list the device as `device` (not `unauthorized`).
4. Emulators work out of the box (`adb devices` shows `emulator-5554` etc.).
5. Multiple devices: export `OPEN_ANDROID_USE_SERIAL=<serial>`.

## Companion app (optional, unlocks Unicode input and faster snapshots)

1. Build: `ANDROID_HOME=<sdk> make companion-build` →
   `dist/companion/open-android-use-companion.apk` (or use the CI artifact).
2. Install: `adb install dist/companion/open-android-use-companion.apk`
3. On the device: open "Open Android Use Companion" → "Open Accessibility
   Settings" → enable the service. The status screen should read
   "Service: running".
4. On the host: export `OPEN_ANDROID_USE_COMPANION=1`. Confirm with
   `open-android-use doctor` (look for the `companion:` line).

## Agent runtime install

- Claude Code: `claude mcp add open-android-use -- /path/to/open-android-use mcp`
- Any MCP-over-stdio runtime: command `open-android-use`, args `["mcp"]`.

## Verify

```sh
open-android-use doctor
open-android-use call list_apps
```
