# Usage

## MCP configuration

TOML (Codex/Qwen style):

```toml
[mcp_servers.open_android_use]
command = "open-android-use"
args = ["mcp"]
```

JSON (Claude/Gemini style):

```json
{
  "mcpServers": {
    "open-android-use": {
      "command": "open-android-use",
      "args": ["mcp"],
      "env": { "OPEN_ANDROID_USE_COMPANION": "1" }
    }
  }
}
```

## Tool surface

Identical schema to desktop Open Computer Use: `list_apps`, `get_app_state`,
`click`, `perform_secondary_action`, `scroll`, `drag`, `type_text`,
`press_key`, `set_value`. `get_app_state` returns the rendered accessibility
tree (indexed `[n]` lines are actionable elements) plus a PNG screenshot;
element frames and coordinates are in screenshot pixel space.

Android mappings:

| Desktop concept | Android behavior |
|---|---|
| `app` name | loose package match (`"settings"` → `com.android.settings`); `"foreground"` = current screen |
| `mouse_button: "right"` | long-press (600ms hold) |
| `perform_secondary_action` | `long-click` on elements that expose it |
| `press_key` | xdotool names + `Back`, `Menu`, `app_switch`, `android_home`; combos (`ctrl+a`) need Android 13+ |
| `scroll` `pages` | swipe gestures inside the element frame; fractions shorten the swipe |
| `type_text` / `set_value` | ASCII-only over plain ADB; full Unicode in companion mode |

## CLI sequencing

```sh
open-android-use call --calls '[
  {"tool":"get_app_state","args":{"app":"chrome"}},
  {"tool":"click","args":{"app":"chrome","element_index":"2"}},
  {"tool":"type_text","args":{"app":"chrome","text":"weather today"}},
  {"tool":"press_key","args":{"app":"chrome","key":"Return"}}
]'
```

Element indexes are only valid within the latest snapshot; every action tool
returns a fresh post-action snapshot, so chained calls stay current.

## Environment variables

| Variable | Default | Meaning |
|---|---|---|
| `OPEN_ANDROID_USE_SERIAL` | auto (single device) | Device serial when several are attached |
| `OPEN_ANDROID_USE_ADB` | `adb` on PATH | adb binary override |
| `OPEN_ANDROID_USE_COMPANION` | off | `1` enables companion-first mode |
| `OPEN_ANDROID_USE_COMPANION_PORT` | `8355` | Companion endpoint port |
| `OPEN_ANDROID_USE_IMAGE_MAX_BYTES` | `900000` | Screenshot PNG byte budget |
| `OPEN_ANDROID_USE_IMAGE_MAX_DIMENSION` | `1280` | Screenshot long-edge cap (px) |
| `OPEN_ANDROID_USE_IMAGE_MIN_SCALE` | `0.25` | Downscale floor |
