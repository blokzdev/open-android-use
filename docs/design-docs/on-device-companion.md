# On-device companion — Phase 2 design

The companion is a Kotlin app (`apps/OpenAndroidUseCompanion`) that moves the
"hands" onto the phone. Its **control surface** — the AccessibilityService,
loopback HTTP server, snapshot/action code — is deliberately dependency-free;
the on-device agent feature added in Phase 3 is the one registered exception
(the first-party Anthropic SDK, scoped to the `agent` package — see
`docs/SUPPLY_CHAIN_SECURITY.md`). It hosts an `AccessibilityService` and a
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

- Control surface has zero third-party dependencies: `org.json` from the
  platform, a hand-rolled ~150-line HTTP/1.1 server over `ServerSocket`,
  programmatic UI (no androidx in the shipped APK). Smallest possible
  supply-chain and review surface. The Phase 3 `agent` package adds the
  first-party Anthropic SDK (and androidx only in the instrumentation-test
  APK); nothing under the control surface imports either.
- Accessibility calls (`rootInActiveWindow`, `dispatchGesture`) run on the main
  looper; HTTP threads block on a latch with a 5s timeout.
- minSdk 26 (Android 8.0, `dispatchGesture` and `ACTION_SET_TEXT` both safe),
  compile/target SDK 35.
- Build: `make companion-build` → `dist/companion/open-android-use-companion.apk`
  (debug-signed for now; release signing is a release-pipeline task).

## Bridge integration

The Go bridge holds a companion client (auto `adb forward` + protocol-checked
`/health`). `doctor` always probes and reports availability. With
`OPEN_ANDROID_USE_COMPANION=1` (port override: `OPEN_ANDROID_USE_COMPANION_PORT`),
the bridge goes companion-first across the board, degrading to the ADB path on
any companion failure:

- `get_app_state` → `/snapshot` live tree (+ `/screenshot`, falling back to adb
  `screencap` below Android 11), rendered in exactly the uiautomator format via
  a shared tree builder, so the model sees no difference between sources.
- `click` / long-press / `scroll` / `drag` → `/action` tap/longPress/swipe
  gestures; ADB `input` remains the fallback.
- `type_text` and `set_value` → `setText` (full Unicode, whole-value replace).
  Fallback to ADB only when the text is ASCII-representable; otherwise the
  companion error (which names the fix) is surfaced.
