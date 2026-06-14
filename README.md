# open-android-use

[![English](https://img.shields.io/badge/English-Click-yellow)](./README.md)
[![简体中文](https://img.shields.io/badge/简体中文-点击查看-orange)](./README.zh-CN.md)

---

**A second pair of hands for your Android device.** `open-android-use` is a fork of
`open-computer-use` that brings the same MCP-based Computer Use tool surface to
Android: any MCP client can see a connected phone or emulator (accessibility tree +
screenshot) and act on it (tap, type, scroll, drag, keys) through the same 9 tools
the desktop runtimes expose. Vision: [docs/design-docs/second-pair-of-hands.md](docs/design-docs/second-pair-of-hands.md).

## Android Quick Start

Requires [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) (`adb`) and a device with USB debugging enabled (or an emulator).

**npm package (staged):** `make android-npm` assembles the standalone
[`open-android-use`](scripts/npm/build-android-package.mjs) npm package —
bridge binaries for macOS/Linux/Windows (arm64 + x64) behind a platform
launcher (`open-android-use mcp|doctor|...`). CI builds it on every push as
the `open-android-use-npm-package` artifact; publishing to the registry is a
manual `npm publish dist/npm/open-android-use` by a maintainer.

```bash
# Build the Android bridge (Go 1.22+)
make android-build

# Diagnose: adb, connected devices, foreground app
dist/android-bridge/*/*/open-android-use doctor

# Inspect and act
dist/android-bridge/*/*/open-android-use call list_apps
dist/android-bridge/*/*/open-android-use call --calls '[
  {"tool":"get_app_state","args":{"app":"settings"}},
  {"tool":"click","args":{"app":"settings","element_index":"1"}}
]'
```

MCP client config:

```json
{
  "mcpServers": {
    "open-android-use": {
      "command": "/path/to/open-android-use",
      "args": ["mcp"]
    }
  }
}
```

Environment: `OPEN_ANDROID_USE_SERIAL` selects a device when several are attached;
`OPEN_ANDROID_USE_ADB` overrides the adb path; `OPEN_ANDROID_USE_IMAGE_MAX_BYTES` /
`_MAX_DIMENSION` / `_MIN_SCALE` tune screenshots (same model as macOS below).
Notable mappings: `mouse_button: "right"` → long-press, `press_key: "Back"` /
`"Menu"` → Android navigation keys, `app: "foreground"` → whatever is on screen.

**On-device companion (optional, recommended):** `make companion-build` produces
`dist/companion/open-android-use-companion.apk` — a zero-dependency
AccessibilityService that exposes a loopback-only control endpoint
([design](docs/design-docs/on-device-companion.md)). Install it, enable the
service, and set `OPEN_ANDROID_USE_COMPANION=1`: snapshots come from the live
accessibility tree (faster than uiautomator, works during IME focus), gestures
go through `dispatchGesture`, and `type_text`/`set_value` carry full Unicode
(plain ADB is ASCII-only). Everything degrades back to the ADB path if the
companion is disabled mid-session. Hardware verification steps:
[VERIFICATION.md](VERIFICATION.md).

### Install on your phone

You don't need a tagged release to try it — install the debug APK directly.

1. **Get the APK.** Either build it (`make companion-build` →
   `dist/companion/open-android-use-companion.apk`) or download the
   `open-android-use-companion-apk` artifact from the latest **android-runtime**
   CI run.
2. **Install** over USB: `adb install -r open-android-use-companion.apk`.
3. **Enable accessibility.** Open the app → *Open Accessibility Settings* →
   enable "Open Android Use Companion".
   - **Android 13+ "Restricted setting":** a sideloaded app is blocked from
     gaining accessibility until you allow it. Go *Settings → Apps → Open
     Android Use Companion → ⋮ (top-right) → Allow restricted settings*, then
     return and enable the service. This is an **install-source** gate that
     affects any sideloaded app — it is *not* about debug vs. release signing,
     and it disappears once the app is installed from the Play Store (or a Play
     testing track), not by signing a release APK.
4. **Run the agent:** *Open Agent Chat* → add your Anthropic API key → ask
   "Open Settings and tell me the Android version".

**On-device agent (Phase 3.1 — no computer, no cable):** the companion app
also ships a complete agent. Open the app → *Open Agent Chat*, add your
Anthropic API key (stored Keystore-encrypted, sent only to
api.anthropic.com), type or dictate a task, and Claude operates the phone
through the same 9-tool surface — narrating before it acts, drawing a visible
gesture trail, pausing the moment you touch the screen, and (optionally)
asking for confirmation before every action batch and speaking its narration
aloud. Stop is one tap; disabling the accessibility service remains the hard
kill switch. Requires Android 11+. Design:
[phase3-agent-loop](docs/design-docs/phase3-agent-loop.md).

---

The inherited desktop runtimes below still work — macOS, Linux, and Windows via
accessibility APIs, published to npm as [`@qwen-code/open-computer-use`](https://www.npmjs.com/package/@qwen-code/open-computer-use).

## Demo

https://github.com/user-attachments/assets/cd0d1644-99e5-47fc-b998-c1eb3c1aabff

## Quick Start

```bash
npm i -g @qwen-code/open-computer-use
```

**On macOS, run it once and grant `Accessibility` and `Screen Recording`. Windows and Linux do not need this step.**

```bash
open-computer-use
```

Add it to your MCP client config:

```json
{
  "mcpServers": {
    "open-computer-use": {
      "command": "open-computer-use",
      "args": ["mcp"]
    }
  }
}
```

## CLI Usage

```bash
# Call a single Computer Use tool and print the MCP-style JSON result
open-computer-use call list_apps
open-computer-use call get_app_state --args '{"app":"TextEdit"}'

# Run a sequence in one process so element_index state can be reused
open-computer-use call --calls '[{"tool":"get_app_state","args":{"app":"TextEdit"}},{"tool":"press_key","args":{"app":"TextEdit","key":"Return"}}]'
open-computer-use call --calls-file examples/textedit-overlay-seq.json --sleep 0.5

# Check permissions; onboarding only opens when something is missing
open-computer-use doctor

# Show help
open-computer-use -h
```

## Configuration

### Image capture (macOS)

The `get_app_state` screenshot and the post-action screenshots attached to every action tool can be tuned through environment variables read at capture time. All variables are optional; unset / non-numeric / out-of-range values fall back to the built-in defaults.

| Variable | Default | Meaning |
|---|---|---|
| `OPEN_COMPUTER_USE_IMAGE_CAPTURE_TIMEOUT` | `5` | Seconds to wait for `SCScreenshotManager.captureImage` before giving up. The MCP result still includes the accessibility tree on timeout; only the `image` block is dropped. Positive float. |
| `OPEN_COMPUTER_USE_IMAGE_MAX_BYTES` | `900000` | Byte budget for the encoded PNG. The downsampler iterates `scale *= 0.85` until the encoded data fits this budget OR `OPEN_COMPUTER_USE_IMAGE_MIN_SCALE` is reached. Positive integer. |
| `OPEN_COMPUTER_USE_IMAGE_MAX_DIMENSION` | `1280` | Long-edge pixel cap for the returned PNG. Initial scale is `min(1, OPEN_COMPUTER_USE_IMAGE_MAX_DIMENSION / largestNativeDimension)`, then clamped up to `OPEN_COMPUTER_USE_IMAGE_MIN_SCALE`. Positive float. |
| `OPEN_COMPUTER_USE_IMAGE_MIN_SCALE` | `0.25` | Floor on the downsample ratio. Neither `MAX_DIMENSION` nor `MAX_BYTES` will shrink below `MIN_SCALE × native`; a `MAX_DIMENSION` that would require less is clamped to this floor (it does **not** fall back to the full-size original). Lower it for more aggressive sizes. Float in `(0, 1]`. |

Coordinate accuracy is preserved across any downsampling — coordinate tools (`click`, `drag`, `scroll`) read the actual pixel dimensions back from the returned PNG and rescale model-supplied coordinates against the live window bounds.

These variables only affect macOS today. The Windows and Linux runtimes return native-size PNGs without downsampling.

See [docs/IMAGE_CAPTURE.md](docs/IMAGE_CAPTURE.md) for the full capture → downsample → encode pipeline, the constraint interaction (maxDimension / maxBytes / minScale), coordinate-mapping details, and worked examples.

## License (open-core)

This repository is open-core:

- **Engine — MIT.** The host-side Go bridge (`apps/OpenAndroidUse`), the
  inherited desktop runtimes, and the npm packaging are licensed under the
  [MIT License](LICENSE), preserving the upstream copyright (© 2026 Leo /
  iFurySt).
- **Android app — PolyForm Perimeter 1.0.0.** The on-device companion and agent
  (`apps/OpenAndroidUseCompanion`) — the product destined for the Play Store —
  is source-available but **no-compete**: read, run, modify, and share it, but
  not to ship a competing product. © 2026 Blokz Development Co. See
  [`apps/OpenAndroidUseCompanion/LICENSE`](apps/OpenAndroidUseCompanion/LICENSE).

Upstream credit and third-party attribution (the bundled Anthropic Java SDK,
Apache-2.0) are in [`NOTICE`](NOTICE).

## Acknowledge

This project is a [QwenLM](https://github.com/QwenLM) fork of [`iFurySt/open-codex-computer-use`](https://github.com/iFurySt/open-codex-computer-use). We thank the original author for the foundational work on macOS accessibility-driven computer-use patterns.

## Differences from upstream

- **Cross-platform**: Added Windows (Go + PowerShell UI Automation) and Linux (Go + Python AT-SPI) runtimes
- **npm distribution**: Published as [`@qwen-code/open-computer-use`](https://www.npmjs.com/package/@qwen-code/open-computer-use) for easy installation
- **MCP server**: Full MCP stdio transport with 9 Computer Use tools
- **CLI tools**: Added `doctor`, `call`, `snapshot`, `list-apps` commands for diagnostics and scripting
- **Image capture tuning**: Environment variables for screenshot size/quality control
- **Qwen Code skill**: Installable skill for Qwen Code agent integration
- **Cursor Motion**: Retained in `experiments/` but not built or released in CI
