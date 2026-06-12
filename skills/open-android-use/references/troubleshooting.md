# Troubleshooting

## Device and adb

- **"adb: NOT FOUND" in doctor** — install platform-tools or set
  `OPEN_ANDROID_USE_ADB=/path/to/adb`.
- **"No Android device is connected"** — check the USB cable/port, that USB
  debugging is enabled, and that the device shows as `device` in `adb devices`.
  `unauthorized` means the trust prompt on the phone was not accepted; replug
  and accept it.
- **"Multiple Android devices are connected"** — set
  `OPEN_ANDROID_USE_SERIAL=<serial>` (serials come from `adb devices`).

## Snapshots

- **`appNotFound(...)`** — the query didn't match an installed package. Run
  `open-android-use call list_apps` and use a package id or a distinctive
  substring.
- **"Launched X but it did not reach the foreground"** — the app may show a
  splash/login flow or be disabled. Unlock the screen, launch it manually once,
  then retry with `app: "foreground"`.
- **"uiautomator returned an empty accessibility hierarchy"** — transient on
  some OEM ROMs (the bridge already retries once). Retry the call; if it
  persists, the foreground surface may be secure (FLAG_SECURE) and cannot be
  inspected — tell the user instead of looping.
- **Screenshot block missing** — secure surfaces also block `screencap`; the
  tree still works. Proceed element-targeted.

## Input

- **"type_text over ADB supports printable ASCII only"** — expected for
  non-ASCII text without the companion. Recommend installing/enabling the
  companion app and `OPEN_ANDROID_USE_COMPANION=1`; do not transliterate
  silently.
- **"Key combination ... needs Android 13+"** — `input keycombination` is not
  available on older Android; use single keys or companion `set_value`.
- **set_value appends instead of replacing** — the ctrl+a clear path needs
  Android 13+; companion mode replaces correctly on any version.
- **Taps land in the wrong place** — make sure coordinates come from the
  *latest* screenshot (the bridge scales screenshot pixels to device pixels per
  snapshot; stale coordinates from an earlier, differently-scaled screenshot
  will miss).

## Companion

- **"Companion is not reachable"** — confirm the APK is installed, the
  accessibility service is enabled (companion app shows "Service: running"),
  and nothing else occupies port 8355 (`OPEN_ANDROID_USE_COMPANION_PORT` to
  change). The bridge creates the `adb forward` itself.
- **"Companion speaks protocol X but this bridge requires Y"** — version skew;
  rebuild/reinstall both from the same checkout.
- **Gestures cancelled ("is the screen interactive?")** — the screen may be
  off or locked; wake and unlock the device.
- **Companion died mid-task** — snapshots and gestures degrade to the ADB path
  automatically; only non-ASCII typing hard-fails. Re-enable the service to
  restore companion mode.
