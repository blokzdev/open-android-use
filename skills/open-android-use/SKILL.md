---
name: open-android-use
description: Guidance for using Open Android Use, the open-source Computer Use MCP server and CLI that drives Android devices and emulators over ADB, optionally accelerated by an on-device companion app. Use when an agent needs to install, verify, troubleshoot, configure, or operate Open Android Use through its CLI, stdio MCP server, or direct Computer Use tool calls.
---

# Open Android Use

## Overview

Open Android Use exposes Computer Use for Android as a local CLI and stdio MCP
server: a host-side bridge that drives any ADB-connected device or emulator. It
speaks the same 9-tool surface as Open Computer Use on desktop:
`list_apps`, `get_app_state`, `click`, `perform_secondary_action`, `scroll`,
`drag`, `type_text`, `press_key`, and `set_value`.

Android specifics are mapped inside that surface:

- The phone has a **single foreground app**: `get_app_state` brings the target
  app to the foreground automatically; `app: "foreground"` targets whatever is
  on screen.
- `mouse_button: "right"` performs a **long-press**.
- `press_key` accepts xdotool names plus Android keys: `"Back"`, `"Menu"`,
  `"app_switch"` (and `"android_home"` for the home button).
- Coordinates are **screenshot pixel space**; the bridge maps them to device
  pixels. Always take coordinates from the latest screenshot.
- Over plain ADB, `type_text`/`set_value` are **ASCII-only** and fail loudly on
  other text. Full Unicode requires the companion app (below).

## Core Workflow

1. Check the CLI: `open-android-use -h`. Setup details: [references/installation.md](references/installation.md).
2. Run `open-android-use doctor` before the first task. It checks adb, device
   selection, the foreground app, and whether the on-device companion is
   available.
3. List apps: `open-android-use call list_apps`.
4. Capture state: `open-android-use call get_app_state --args '{"app":"settings"}'`
   (app queries match package names loosely; `"foreground"` works too).
5. Prefer element-targeted actions using `element_index` from the latest
   `get_app_state`.
6. For multi-step CLI work, use `open-android-use call --calls '<json-array>'`
   so one process reuses the element index mapping.
7. For MCP runtimes, configure `open-android-use mcp`. See [references/usage.md](references/usage.md).
8. On failures (no device, unauthorized, flaky snapshots, companion unreachable),
   read [references/troubleshooting.md](references/troubleshooting.md).

## Companion mode (recommended when available)

If the user has the companion app installed (an accessibility service on the
device), set `OPEN_ANDROID_USE_COMPANION=1`: snapshots come from the live
accessibility tree (faster, works while a keyboard is open), gestures use the
OS gesture API, and `type_text`/`set_value` carry full Unicode. Everything
degrades back to plain ADB automatically if the companion is off. `doctor`
tells you whether it is available — suggest it to the user when a task needs
non-ASCII text.

## Operating Rules

- Treat the device as the user's real phone. Do not open password managers,
  banking apps, private messages, or other sensitive surfaces unless the task
  explicitly requires it.
- Ask before sending messages, posting, purchasing, deleting, calling, or any
  other externally visible or irreversible action.
- Always run `get_app_state` before using `element_index`; indexes are only
  valid for the latest snapshot of that app.
- Prefer element-targeted `click`/`set_value` over coordinate taps; use
  coordinates only when the tree exposes no safer target, and take them from
  the current screenshot.
- Launching an app changes what's on the user's screen — that is normal and
  required on Android (single foreground app), but don't bounce between apps
  more than the task needs.
- If text input fails with an ASCII error, do not retry with transliterated
  text unless the user agrees; recommend companion mode instead.
- With multiple devices attached, never guess: ask which serial to use and set
  `OPEN_ANDROID_USE_SERIAL`.

## Common CLI Actions

```sh
open-android-use doctor
open-android-use devices
open-android-use call list_apps
open-android-use call get_app_state --args '{"app":"settings"}'
open-android-use call click --args '{"app":"settings","element_index":"3"}'
open-android-use call press_key --args '{"app":"foreground","key":"Back"}'
```

A sequence that reuses state in one process:

```sh
open-android-use call --calls '[
  {"tool":"get_app_state","args":{"app":"settings"}},
  {"tool":"scroll","args":{"app":"settings","element_index":"1","direction":"down"}},
  {"tool":"click","args":{"app":"settings","element_index":"5"}}
]'
```

## MCP Usage

```toml
[mcp_servers.open_android_use]
command = "open-android-use"
args = ["mcp"]
```

JSON config examples and tool-call patterns: [references/usage.md](references/usage.md).

## References

- [references/installation.md](references/installation.md): platform-tools, USB debugging, building/installing the bridge and companion APK.
- [references/usage.md](references/usage.md): MCP config, CLI sequencing, environment variables, companion mode.
- [references/troubleshooting.md](references/troubleshooting.md): device/authorization, snapshot, input, and companion failures.
