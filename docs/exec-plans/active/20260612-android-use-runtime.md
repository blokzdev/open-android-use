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
  - npm distribution of the Android binary, smoke fixture app. (Phase 1.x)
  - (Pulled into this plan after all: `.github/workflows/android-runtime.yml`
    runs gofmt/vet/tests and cross-compiles all three host targets.)

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
- Risk: `uiautomator dump` flakiness (stale/empty tree, "could not get idle
  state", null root node — esp. right after boot and on Android 11+).
  Mitigation: escalating-backoff retries (4 attempts: 0/500ms/1s/2s) + a 10s
  foreground wait, with an "after N attempts" error; the companion path (live
  AccessibilityService) avoids it entirely when enabled.
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
- [x] Phase 2.0: companion design doc + zero-dependency Kotlin app
  (`apps/OpenAndroidUseCompanion`, protocol v1: health/snapshot/screenshot/action);
  APK builds in container and CI (`make companion-build`, artifact uploaded).
- [x] Phase 2.0 bridge integration: companion detection in `doctor`, Unicode
  `type_text` via `OPEN_ANDROID_USE_COMPANION=1` with ASCII-safe ADB fallback;
  covered by httptest-backed unit tests.
- [x] `VERIFICATION.md` ledger created (V1–V19 bridge, V20–V28 companion) — to be
  cleared after hardware verification.
- [x] Phase 2.1: companion-first `get_app_state` (live tree + companion
  screenshot with screencap fallback, shared tree renderer), companion-first
  tap/long-press/swipe/drag, Unicode `set_value`; all with ADB degradation and
  httptest-pinned tests (V29–V32 added to `VERIFICATION.md`).
- [x] Agent skill `skills/open-android-use` + parameterized skill packaging.
- [x] Emulator smoke: `scripts/run-android-smoke-tests.sh` (`make android-smoke`)
  + CI `emulator-smoke` job on a real API-30 emulator with companion
  install/enable via adb settings. First green run = first true e2e validation.
- [x] Phase 3.1a–c: on-device agent (chat UI + Anthropic SDK loop + in-process
  9-tool executor), safety surfaces (touch-to-pause, gesture trail,
  confirmation sheet), voice (TTS narration + push-to-talk), code-review
  hardening, and a keyless emulator agent-loop smoke in CI. Merged in PR #2.
  Sub-plan: `docs/exec-plans/completed/20260612-phase3-on-device-agent.md`.
- [~] Device smoke on real hardware: first agent turn verified end-to-end on a
  real device (Samsung, Android 13+) — "Open Settings and tell me the Android
  version" succeeded. Full `VERIFICATION.md` V1–V41 ledger still in progress.
- [x] Open-core licensing + onboarding docs: engine stays MIT, the Android app
  (`apps/OpenAndroidUseCompanion`) is PolyForm Perimeter 1.0.0 © Blokz
  Development Co.; root `NOTICE` (upstream + Apache-2.0 Anthropic SDK); README
  "Install on your phone" + restricted-settings docs; in-app restricted-settings
  hint + dependency-free About sheet.
- [x] npm distribution of the Android bridge binary, staged: standalone
  `open-android-use` package (six host targets + Node launcher) assembled by
  `make android-npm` / `scripts/npm/build-android-package.mjs`, built and
  uploaded as a CI artifact on every push. Registry publish remains a manual
  maintainer step (`npm publish dist/npm/open-android-use`).
- [~] **Phase 4 — Product UI/UX** (`docs/design-docs/phase4-product-ui.md`):
  Compose/Material 3 presentation layer; 4.1 guided onboarding wizard → 4.2
  design-system foundation → 4.3 chat polish → 4.4 trust/control surface → 4.5
  settings & privacy → 4.5.1 hardening → 4.6 a11y/i18n/responsive → 4.7 Play readiness.
  - [x] PR-A (foundation): Compose + Material 3 enabled (Kotlin 2.0 compose
    plugin), `OpenAndroidUseTheme` on the brand "Aurora" palette, designed brand
    icon — the agent's hand tapping out an AI sparkle (+ gradient background +
    monochrome themed variant), and `MainActivity`/`AboutActivity` migrated to
    Compose. `ChatActivity` stays Views (chunk 4.3). Merged in PR #6.
  - [x] PR-B (onboarding wizard 4.1): Compose first-run wizard (welcome → enable
    accessibility w/ restricted-settings handling + auto-advance → privacy →
    skippable API key + model → preferences → ready), gated by
    `AgentSettings.onboardingCompleted`. Plus an agent-readiness model
    (`Readiness.kt`): home + chat surface what's missing (accessibility / key)
    and degrade gracefully instead of failing silently.
  - [x] PR-C (chat polish 4.3): `ChatActivity` rebuilt in Compose — live
    streaming, humanized tool chips (`ToolChipLabel`), collapsible thinking,
    light markdown answers (`ChatMarkdown`, select/copy/share), suggested-prompt
    empty state, error/needs-key cards (`NoteClassifier`), header model chip +
    New conversation + haptics + IME-aware composer, always-visible Stop, and the
    marquee **"Agent's view"** (an additive `AgentController.latestScreenshotBase64`
    + `onScreenshotCaptured` default-no-op hook; in-memory only). Pure helpers
    unit-tested.
  - [x] 4.4 trust/control surface: persistent "agent in control" badge over
    other apps (touchable accessibility overlay, `InControlOverlay`) with a
    Stop-from-anywhere button; an ongoing notification with a Stop action
    (`AgentNotification` + `StopAgentReceiver`, POST_NOTIFICATIONS-gated, not a
    foreground service); and a tap-location highlight on the Agent's-view (pure
    `TapHighlight`, additive `AgentController.latestTapNormalized`). True FGS type
    deferred to 4.6/Play.
  - [x] 4.5 settings & privacy + multi-session conversations
    (`docs/exec-plans/active/20260614-phase45-settings-sessions.md`): real
    Settings screen (key + Clear, model, confirm/voice toggles, Material You)
    and a browsable Privacy & data screen (trust story + clear key/conversation/
    delete-all controls); the cramped in-chat dialog is gone. Recent prompts were
    **superseded by full sessions** — a persistent, auto-named History list
    (`SessionStore`, text-only JSON in filesDir) the user can revisit, **resume
    with context**, rename, archive, delete; resume rebuilds the model history
    from the transcript (`SessionHistory`) rather than serializing SDK types
    (screenshots never persist). Whole-conversation export to Markdown via
    FileProvider. New JVM tests (codec, history rebuild, title, export) + an
    instrumented `SessionStore` test; APK + instrumentation build green.
  - [x] 4.5.1 hardening + 4.6 enablers
    (`docs/exec-plans/active/20260614-phase451-hardening.md`): fixed the 4.5
    regressions — Material You now re-themes the back-stack Chat/Home on resume,
    `ChatActivity` is `singleTask` + `onNewIntent` so resuming from History doesn't
    stack duplicates, a11y `contentDescription`s for the mic/overflow/in-control
    chips, `onShare`→`onExport` rename; a session-save churn guard
    (`AgentController.transcriptRevision`) so routine pauses don't reshuffle History;
    and **Compose UI test infrastructure** (`ui-test-junit4`) with smoke tests for
    Settings/History plus an instrumented Keystore round-trip — all riding the
    existing `emulator-smoke` pass.
  - [ ] 4.6 a11y/i18n/responsive: richer markdown (links/tables), full
    content-description/large-font/reduce-motion pass, migrate inline copy to
    `strings.xml` (~140 strings across the Activities), foldable/tablet layouts
    (`WindowSizeClass`).
- [ ] **Phase 4.7 — Distribution & Play readiness**: (a) tagged GitHub release of
  the signed APK; (b) Play Store **signed AAB** (the Android 13+ "Restricted
  setting" prompt is an install-source gate removed by a Play install, *not* by
  release signing); (c) release signing keystore/config (founder-held); (d) the
  `QUERY_ALL_PACKAGES` Play-policy decision for `list_apps` (justified `<queries>`
  vs. a narrower approach); (e) the true foreground-service type deferred from 4.4;
  (f) store-listing assets + a privacy policy.
- [ ] **Phase 5 — Multi-provider BYOK** (`docs/design-docs/phase5-multi-provider-byok.md`):
  Claude + Gemini via an in-house Kotlin `AgentBackend` interface + official
  per-provider SDKs (keep `anthropic-java`; add `com.google.genai` for Gemini);
  per-provider key + model in settings. Built after the Phase 4 foundation.
- [ ] Phase 3.x backlog (agent intelligence): models-API-driven live model list
  (per provider), per-action consent granularity, task memory (opt-in).

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
- 2026-06-12: Companion is zero-third-party-dependency Kotlin (platform org.json,
  hand-rolled loopback HTTP server, programmatic UI, no androidx) to minimize the
  supply-chain/review surface; server binds 127.0.0.1 only so the sole remote
  path is `adb forward` behind USB-debugging trust.
- 2026-06-12: Bridge treats the companion as opt-in (`OPEN_ANDROID_USE_COMPANION=1`)
  rather than auto-on: predictable behavior until hardware verification; `doctor`
  always reports availability so users discover it.
- 2026-06-14: Open-core licensing. The engine (Go bridge, desktop runtimes, npm
  packaging) stays MIT, retaining the upstream © Leo notice; the Android app
  (`apps/OpenAndroidUseCompanion`) — the Play-Store product — is PolyForm
  Perimeter 1.0.0 (source-available, no-compete) © Blokz Development Co. The
  bundled Anthropic SDK (Apache-2.0) is attributed in root `NOTICE` because the
  build strips its `META-INF` license files.
- 2026-06-14: The Android 13+ "Restricted setting" prompt is an install-source
  gate (every sideloaded app hits it), not a debug-vs-release-signing issue; it
  is removed by installing from the Play Store, not by signing a release APK.
  Documented in README + in-app hint.
- 2026-06-14: Phase 4 refines (does not discard) the "no-androidx UI" decision
  above: the presentation layer may adopt Compose/Material 3 for a world-class
  product UI, while the control surface (AccessibilityService, loopback server,
  snapshot/action, agent loop) stays dependency-free. See
  `docs/design-docs/phase4-product-ui.md`. PR-A enables Compose and migrates the
  two simple screens; the control-surface-stays-androidx-free rule is the
  guardrail (packages `CompanionService`/`HttpServer`/snapshot/action must not
  import androidx).
- 2026-06-14: Multi-provider BYOK (Phase 5) is implemented via a small in-house
  Kotlin `AgentBackend` interface + the official first-party SDK per provider
  (keep `anthropic-java`; add `com.google.genai` for Gemini). GenKit and the
  Vercel AI SDK are rejected as JS/server-side: both would force a backend proxy
  and break the on-device, key-stays-local model. See
  `docs/design-docs/phase5-multi-provider-byok.md`.
- 2026-06-14: Phase 4.5 resume rebuilds the model history from the saved
  transcript (`SessionHistory.rebuild`) instead of serializing the Anthropic
  `MessageParam` blocks. The SDK's configured Jackson mapper is Kotlin-`internal`
  and its modules aren't on the compile classpath; replicating it would couple us
  to SDK internals and add dependencies. Rebuilding from text is dependency-free,
  SDK-version-stable, and matches the privacy invariant — conversations persist
  text-only (filesDir JSON), screenshots never touch disk, and the agent
  re-observes the device live on resume. Recent prompts are superseded by this
  full multi-session model. See the 4.5 sub-plan.
- 2026-06-14: Pre-4.6 hardening (4.5.1) precedes the big i18n/a11y phase: fix the
  4.5 regressions and stand up Compose UI test infrastructure first, so 4.6's
  every-screen string externalization has a safety net. `ChatActivity` is
  `singleTask` (single workspace instance; History resumes route in via
  `onNewIntent`); Material You re-themes back-stack screens via a recreate-on-resume
  check. **Play-readiness work is split into a dedicated Phase 4.7** (signing/AAB,
  `QUERY_ALL_PACKAGES` policy, FGS type, listing/privacy policy) so 4.6 stays a pure
  UX/i18n phase. Hardware verification + the signing keystore remain founder-gated.
- 2026-06-14: Brand identity — the app icon is a designed adaptive vector: the
  agent's hand (touch-gesture, blue→violet gradient) tapping out an AI sparkle
  (open mint "sparkle crown" with gaps so it stays distinct from the hand, incl.
  in monochrome), in the brand "Aurora" palette (indigo gradient ground),
  replacing the borrowed system drawable; a monochrome layer supports Android 13+
  themed icons, and the same palette drives the Compose theme.
- 2026-06-14: Unscheduled "someday/maybe" ideas now live in `docs/BACKLOG.md`
  (wired into the CLAUDE.md memory harness), keeping this plan for scheduled work.
  The robust no-companion bridge snapshot (deferred during the get_app_state
  hardening) is recorded there.
