# Phase 3.1a — on-device agent loop (chat UI + Claude API + in-process tools)

> Status: 3.1a implemented and unit-tested in container; awaiting hardware
> verification (V33–V37).
> Parent design: `docs/design-docs/phase3-agent-loop.md`. Phase 1/2 runtime plan:
> `20260612-android-use-runtime.md` (bridge + companion shipped, PR #1 merged,
> emulator CI green).

## Goal

The companion app (`apps/OpenAndroidUseCompanion`) gains an on-device agent:
a chat Activity where the user types a task, Claude sees the screen via the
existing SnapshotBuilder/ActionExecutor in-process, acts with narration, and
the user can pause at any time. No computer, no cable: the phone is the
complete Second Pair of Hands.

## Scope

- Included (3.1a):
  - `agent/` package in the companion: Anthropic client, agent loop service,
    tool executor bridging the 9-tool schema to in-process calls, conversation
    Activity (programmatic UI, message list + input + stop button), API-key
    entry stored via Android Keystore-encrypted prefs.
  - Screenshot downscale port (the Go `image.go` budget model) so snapshots fit
    model-friendly sizes.
  - VERIFICATION.md additions (V33+: on-device agent run).
- Not included (later slices): voice (3.1c), confirmation sheets and gesture
  trail (3.1b), task memory, Play distribution.

## Decisions (binding for the implementing session)

- 2026-06-12: **Use the official Anthropic Java SDK from Kotlin** (per the
  claude-api skill: Kotlin → Java SDK; never raw HTTP when an SDK exists).
  This amends the companion's zero-third-party-dependency rule: the rule now
  scopes to the *control surface* (accessibility service + loopback endpoint
  stay dependency-free); the agent feature is a separate Gradle module where
  the first-party SDK is justified. Record the dependency in
  `docs/SUPPLY_CHAIN_SECURITY.md` terms when adding it.
- 2026-06-12: **Model default `claude-opus-4-8`**, user-selectable in settings
  (Opus 4.8 / Sonnet 4.6 / Haiku 4.5 list fetched via the models API later;
  hardcoded list in 3.1a). Use exact IDs; no date suffixes.
- 2026-06-12: **Adaptive thinking** (`thinking: {type: "adaptive"}`); no
  `temperature`/`top_p`/`top_k` (removed on Opus 4.8 — would 400); effort via
  `output_config.effort` default `high`.
- 2026-06-12: **Streaming** for all turns (SDK streaming helpers +
  `finalMessage()`-equivalent); UI renders text deltas live.
- 2026-06-12: **Handle `stop_reason == "refusal"`** explicitly before reading
  content (surface to the user, never auto-retry the same prompt).
- 2026-06-12: **Prompt caching**: tools array and system prompt are frozen
  byte-identical across turns with a `cache_control` breakpoint on the last
  system block; per-turn screenshots go in user turns only. No timestamps or
  IDs in the system prompt.
- 2026-06-12: **Tool surface = the existing 9-tool schema** (port the JSON
  schemas from the Go bridge's `toolDefinitions()` verbatim), executed
  in-process: `get_app_state` → SnapshotBuilder + takeScreenshot + downscale;
  actions → ActionExecutor. `app` resolution simplifies on-device (single
  foreground app; launch via PackageManager intents).
- 2026-06-12: **Manual agentic loop** (not the SDK tool runner): we need the
  pause/consent gate between tool batches — touch-to-pause and per-action
  narration require loop control.
- 2026-06-12: **API key in Android Keystore** (AES key in Keystore encrypting
  the pref value); key never leaves the device except to api.anthropic.com.

## Risks

- Anthropic Java SDK on Android (minSdk 26): verify desugaring/OkHttp
  compatibility in a spike before building the UI around it. Fallback
  decision if incompatible: raw HTTPS implementation, documented as a
  deviation with reasons.
- Image tokens: screenshots at device resolution are token-expensive; the
  downscale port (1280px long edge default) is required from the first turn.
- Long turns at high effort: keep the stop button responsive (loop checks a
  cancel flag between stream events and before each tool execution).

## Milestones

1. Spike: SDK dependency builds in the companion, one streamed Hello-World
   message round trip on the emulator. (Gate for everything else.)
2. Tool executor + downscale port, unit-testable without a model.
3. Agent loop service + chat UI + Keystore key storage.
4. Emulator smoke extension: scripted agent turn against a stub… (defer if
   API-key handling in CI is awkward; manual V33 acceptable).
5. Docs sync (ARCHITECTURE §8, QUALITY_SCORE, README, VERIFICATION V33+),
   history record, push.

## Verification

- `make companion-build` stays green; new module's unit tests run in CI.
- V33 (to add in VERIFICATION.md): install, enter API key, ask the agent to
  open Settings and report the Android version — watch it narrate, snapshot,
  tap, and answer.

## Progress

- [x] Decisions captured from the claude-api skill (this doc).
- [x] Milestone 1 spike: `anthropic-java` 2.40.1 resolves and `assembleDebug`
  passes in the container (Android SDK 35; META-INF merge excludes needed for
  the SDK's transitive license metadata — recorded in build.gradle.kts).
- [x] Milestone 2: `agent` package — Snapshot model/flattener, ImageBudget,
  ToolExecutor, KeyMapper; 17 JVM unit tests green; companion build script now
  runs them in CI.
- [x] Milestone 3: AgentController (manual streaming loop), AgentSettings
  (Keystore), ChatActivity; wired from MainActivity.
- [ ] Milestone 4: emulator smoke extension — deferred (API key handling in CI;
  manual V33–V37 acceptable per plan).
- [x] Milestone 5: docs sync (ARCHITECTURE §8, QUALITY_SCORE, VERIFICATION
  V33–V37, supply-chain register), history record.

## Decisions (amended during implementation)

- 2026-06-12: **Package, not separate Gradle module.** The agent lives in the
  `agent` package of the existing `app` module; isolation from the control
  surface is by convention (no `com.anthropic` imports outside `agent/`),
  recorded in build.gradle.kts and the supply-chain register. A second module
  bought no real isolation for one APK and doubled the build plumbing.
- 2026-06-12: **No foreground service.** The agent loop thread lives in the
  companion process, which the bound accessibility service keeps alive — no
  new permission or service type needed for 3.1a.
- 2026-06-12: **press_key surface on-device** = IME enter, Back/Escape, Home,
  Recents, Notifications, BackSpace/Delete; everything else fails loud with
  guidance to use type_text/set_value (accessibility services cannot inject
  raw key events).
- 2026-06-12: **Screenshot pruning**: tool-result screenshots outside the two
  most recent results are swapped for a stable text placeholder (the
  computer-use reference pattern) to bound context growth.
