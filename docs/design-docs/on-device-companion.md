# On-device companion — Phase 2 design

The companion is a zero-dependency Kotlin app (`apps/OpenAndroidUseCompanion`)
that moves the "hands" onto the phone. It hosts an `AccessibilityService` and a
loopback HTTP server; the Phase 1 bridge (or any local peer) talks to it over
`adb forward`. It exists to fix what ADB transport cannot do cleanly:

| ADB-path weakness | Companion fix |
|---|---|
| `input text` is ASCII-only | `ACTION_SET_TEXT` on the focused node — full Unicode |
| `uiautomator dump` is slow, file-based, flaky, blind during IME focus | live `rootInActiveWindow` tree, in-memory |
| no gesture while dump runs; coarse `input swipe` | `dispatchGesture` paths with precise timing |
| no consent surface | on-phone UI: status, enable flow, kill switch |

## Security posture

- Server binds `127.0.0.1` only; reachable from a host exclusively through
  `adb forward tcp:8355 tcp:8355` (i.e. only with USB debugging trust) or by apps
  on the same device. No network exposure, no cleartext off-device.
- The accessibility service must be enabled manually by the user in system
  settings — that is the consent gate, by OS design.
- Kill switch: disabling the service (UI shortcut provided) tears the server down;
  clients fail fast.

## Protocol v1 (JSON over HTTP, port 8355)

- `GET /health` →
  `{"ok":true,"service":"open-android-use-companion","version":"<app>","protocol":1,"screenshot":<bool>}`
- `GET /snapshot` →
  `{"ok":true,"protocol":1,"package":"<foreground pkg>","tree":<node>}` where
  `node = {"className","text","contentDesc","resourceId","bounds":[l,t,r,b],
  "clickable","longClickable","scrollable","editable","focusable","focused",
  "checkable","checked","selected","enabled","password","children":[node...]}`
  (boolean flags omitted when false, except `enabled` which is omitted when true
  and emitted as `"enabled":false` when disabled; bounds are device pixels in
  screen coordinates).
- `GET /screenshot` → `image/png` body (API 30+; `501` with JSON error below 30).
- `POST /action` with one of:
  - `{"type":"tap","x":N,"y":N}` / `{"type":"longPress","x":N,"y":N}`
  - `{"type":"swipe","fromX":N,"fromY":N,"toX":N,"toY":N,"durationMs":N}`
  - `{"type":"setText","text":"..."}` (targets the focused editable node)
  - `{"type":"global","action":"back|home|recents|notifications"}`
  → `{"ok":true}` or `{"ok":false,"error":"..."}` (HTTP 200 either way; transport
  errors are transport errors).

Versioning: `protocol` is bumped on breaking changes; the bridge refuses to talk
to a protocol it does not know.

## Implementation notes

- Zero third-party dependencies: `org.json` from the platform, a hand-rolled
  ~150-line HTTP/1.1 server over `ServerSocket`, programmatic UI (no androidx).
  Smallest possible supply-chain and review surface.
- Accessibility calls (`rootInActiveWindow`, `dispatchGesture`) run on the main
  looper; HTTP threads block on a latch with a 5s timeout.
- minSdk 26 (Android 8.0, `dispatchGesture` and `ACTION_SET_TEXT` both safe),
  compile/target SDK 35.
- Build: `make companion-build` → `dist/companion/open-android-use-companion.apk`
  (debug-signed for now; release signing is a release-pipeline task).

## Bridge integration (Phase 2.0 — minimal)

The Go bridge gains a companion client used when `OPEN_ANDROID_USE_COMPANION=1`:

- `doctor` always probes (`adb forward` + `/health`) and reports availability.
- `type_text` routes through `setText` — removing the ASCII limit — and falls
  back to the ADB path (with its ASCII guard) when the companion is unreachable.
- Port override: `OPEN_ANDROID_USE_COMPANION_PORT` (default 8355).

Companion-backed snapshots, gestures, and screenshots through the bridge are
Phase 2.1 (see the execution plan); the protocol above already carries them.
