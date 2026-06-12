# Android Use runtime — Second Pair of Hands foundation

> Language note: this fork (`open-android-use`) is led by an English-speaking founder.
> New docs are written in English; inherited Chinese docs stay valid until individually migrated.

## Goal

Turn this fork into `open-android-use`: the same 9-tool Computer Use MCP surface the
macOS / Windows / Linux runtimes expose, implemented for Android devices, and grow it
into the "Second Pair of Hands" product described in
`docs/design-docs/second-pair-of-hands.md`. End state for this plan: a host-side Go
runtime (`apps/OpenAndroidUse`) that drives any ADB-connected Android device or
emulator — accessibility snapshot, screenshot, and input synthesis — with unit tests,
build script, and docs synced.

## Scope

- Included:
  - `apps/OpenAndroidUse`: Go CLI + stdio MCP server, 9-tool schema parity with the
    other runtimes, ADB bridge (uiautomator dump, screencap, input synthesis).
  - Screenshot downsampling with the same budget model as the macOS pipeline
    (`OPEN_ANDROID_USE_IMAGE_MAX_BYTES` / `_MAX_DIMENSION` / `_MIN_SCALE`).
  - Unit tests with a mocked ADB layer; `make android-test` / `make android-build`.
  - Build script `scripts/build-open-android-use.sh` (host binaries; adb runs on host).
  - Docs: vision doc, ARCHITECTURE section 8, QUALITY_SCORE row, README section,
    CLAUDE.md / AGENTS.md refresh, history record.
- Not included (next plans):
  - On-device companion app (Kotlin `AccessibilityService` + MediaProjection) that
    removes the ADB cable and unlocks IME-quality text input. (Phase 2)
  - On-device agent loop / chat-voice UI talking to a model API. (Phase 3)
  - npm distribution of the Android binary, smoke fixture app, CI wiring. (Phase 1.x)

## Background

- Related docs: `docs/ARCHITECTURE.md`, `docs/design-docs/second-pair-of-hands.md`,
  `docs/design-docs/core-beliefs.md`.
- Related code: `apps/OpenComputerUseLinux/main.go` is the structural blueprint
  (CLI surface, MCP framing, snapshot cache, tool schemas).
- Known constraints:
  - Android is a single-foreground-app OS: `get_app_state` must be allowed to bring
    the target app to the foreground (unlike the Windows runtime's no-launch default).
  - `adb shell input text` is ASCII-only in practice; non-ASCII typing needs the
    Phase 2 on-device IME. The runtime must fail loudly, not silently mangle.
  - `uiautomator dump` cannot capture secure flags / some surfaces; best-effort.
  - This dev container has Go 1.24 + network to `dl.google.com`, but no device, so
    verification here is unit-test-level; device smoke runs on founder hardware.

## Risks

- Risk: coordinate drift between screenshot pixel space and device input space when
  the screenshot is downsampled.
  Mitigation: snapshot carries a single scale factor; all element frames are emitted
  in screenshot pixel space and every action divides by the same factor; unit tests
  pin the round trip.
- Risk: `uiautomator dump` flakiness (returns stale or empty tree on some OEM ROMs).
  Mitigation: retry once, fail with actionable error text; Phase 2 replaces it.
- Risk: protocol drift from the other three runtimes.
  Mitigation: tool names/schemas copied from the Linux runtime verbatim; tests assert
  the 9-tool surface and schema fields.

## Milestones

1. Research and design convergence (this doc + vision doc). ✅
2. Go runtime slice: device layer, snapshot pipeline, 9 tools, MCP/CLI parity, tests.
3. Build script, Makefile targets, docs sync, history record, push.
4. (Next plan) Device smoke on real hardware; npm packaging; emulator CI.
5. (Next plan) Phase 2 on-device companion design doc + scaffold.

## Verification

- Commands:
  - `make android-test` → `(cd apps/OpenAndroidUse && go test ./...)`
  - `make android-build` → `./scripts/build-open-android-use.sh`
  - `gofmt -l apps/OpenAndroidUse` is empty; `go vet ./...` clean.
- Manual checks (founder, with a device):
  - `open-android-use doctor`, `open-android-use devices`
  - `open-android-use call list_apps`
  - `open-android-use call --calls '[{"tool":"get_app_state","args":{"app":"settings"}},{"tool":"click","args":{"app":"settings","element_index":"1"}}]'`
- Observation checks: screenshot stays under the byte budget; element frames visually
  line up with the returned PNG.

## Progress

- [x] Survey fork, extract runtime blueprint and repo conventions.
- [x] Vision doc + this execution plan.
- [x] `apps/OpenAndroidUse` runtime implemented with tests passing.
- [x] Build script + Makefile targets.
- [x] Docs synced (ARCHITECTURE, QUALITY_SCORE, README, CLAUDE.md, AGENTS.md).
- [x] History record + push to `claude/open-android-use-arch-c1xqtu`.
- [ ] Device smoke on real hardware (needs founder's device; blocked in container).

## Decisions

- 2026-06-12: Phase 1 is a host-side ADB bridge in Go, not an on-device app, because
  it reuses the proven runtime pattern (Windows/Linux), is fully unit-testable in CI,
  and works against any device/emulator today. The on-device companion is Phase 2.
- 2026-06-12: Keep exact 9-tool schema parity instead of inventing Android-flavored
  tools (e.g. `swipe`, `back_button`). Android semantics are mapped inside the
  existing surface (`mouse_button: right` → long-press, `press_key: "Back"` →
  KEYCODE_BACK) so every MCP host that already speaks Computer Use works unchanged.
- 2026-06-12: New documentation is English-first; inherited Chinese docs remain
  authoritative until migrated. Recorded in CLAUDE.md.
