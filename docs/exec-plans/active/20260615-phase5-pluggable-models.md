# Phase 5 — Pluggable models + on-device edge tier

> English-first per `CLAUDE.md`. Formalizes the Phase 5 sub-phases sketched in
> the roadmap (`docs/exec-plans/active/20260612-android-use-runtime.md`) and the
> design doc (`docs/design-docs/phase5-multi-provider-byok.md`). Runs after
> Phase 4.7, before Phase 6 (launch readiness & hardening).

## Goal

Let the on-device agent run on more than one model — Claude (existing), Gemini
(BYOK, first), and a downloaded on-device model (Gemma 4 E2B) — behind one
provider-neutral seam, with device-capability tiering and adaptive perception,
while keeping the on-device, no-server, key-stays-local posture intact.

## Scope

- Included: a neutral `agent/llm` port + per-provider adapters; per-provider key
  + model in settings; device-capability diagnostics + tier-based degradation;
  adaptive perception (text-first via the a11y tree, vision on capable devices);
  runtime model download (never bundled); incremental R8/minify keep-rules.
- Not included: providers beyond Claude + Gemini + the on-device tier; routing/
  fallback between providers; per-task provider switching mid-conversation. Final
  shrink/verify, Play packaging, and the security/perf hardening pass land in
  Phase 6.

## Background

- Design: `docs/design-docs/phase5-multi-provider-byok.md` (the `AgentBackend`
  interface, the GenKit/Vercel rejection, settings/state, supply-chain rules).
- Code: `apps/OpenAndroidUseCompanion` `agent` + new `agent.llm` packages.
- Constraint: only the `agent` tree may import provider SDKs; the control surface
  stays dependency-free. First-party SDKs only (`anthropic-java`, add
  `com.google.genai`); no backend proxy.

## Risks

- ~2B on-device multi-step reliability — mitigated by text-first perception and
  device-tier gating.
- New native libs + runtime model download reshape size/egress/perf/security —
  deliberately sequenced before Phase 6 so hardening targets the final surface.
- Gemini model identifiers must be confirmed against Google's live models
  endpoint at build time (assistant knowledge cutoff cannot guarantee strings).

## Milestones (sub-phases)

1. **5.1 — `AgentBackend` seam + `AnthropicBackend` (pure refactor).** ✅ Done.
   Neutral `agent.llm` model (AgentBackend/AgentMessage/ToolSpec/events); the
   Anthropic path extracted behind it with zero observable change; hybrid
   `replayPayload` keeps thinking signatures lossless. Guarded by new mapping
   unit tests + the unchanged keyless smoke.
2. **5.2 — `GeminiBackend` (BYOK) + provider settings/UI.** Add `com.google.genai`;
   map the 9 tools to Gemini function declarations; per-provider key (own
   Keystore alias) + per-provider model; Compose provider toggle + model picker.
3. **5.3 — Model lists behind the backend.** Per-provider `validateKey`/`listModels`
   capability (fold `ModelCatalog`); curated fallback per provider. Begin
   R8/minify keep-rules incrementally.
4. **5.4 — Device-capability diagnostics + tier-based degradation.** Extend
   `Readiness` with a capability tier that gates perception + on-device options.
5. **5.5 — On-device backend (Gemma 4 E2B).** MediaPipe LLM Inference / LiteRT-LM;
   runtime model download (never bundled), source-trust + integrity check;
   native function calling.
6. **5.6 — Adaptive perception.** Text-first via the `get_app_state` a11y tree;
   vision/screenshots only on capable tiers.
7. **5.7 — Phase-5 hardening.** Supply-chain registration (`com.google.genai`,
   model source), egress review of new providers, cross-backend test matrix.

## Verification

- Per slice: `gradle :app:assembleDebug testDebugUnitTest assembleDebugAndroidTest`
  green; `gofmt -l apps/OpenAndroidUse` empty.
- Behavior preservation (5.1): keyless emulator smoke (`AgentLoopEmulatorTest`)
  passes with no smoke-code change.
- Cross-backend: extend the smoke to drive each backend against a stub endpoint.
- Hardware: append `VERIFICATION.md` items per slice (e.g. V122 below).

## Progress

- [x] 5.1 — `AgentBackend` seam + `AnthropicBackend` (this PR).
- [ ] 5.2 — Gemini BYOK + provider settings/UI.
- [ ] 5.3 — model lists behind the backend; R8 keep-rules begin.
- [ ] 5.4 — device-capability diagnostics + tiering.
- [ ] 5.5 — on-device Gemma 4 E2B backend + runtime download.
- [ ] 5.6 — adaptive perception.
- [ ] 5.7 — Phase-5 hardening.

## Decisions

- 2026-06-15: **5.1 streaming interface = blocking sink, not `Flow`.** The loop
  is a single sequential consumer on a dedicated worker thread that cancels by
  force-closing the in-flight stream, so Flow's backpressure/composition/
  multi-collector machinery would be unused and a `runBlocking` bridge would add
  subtler cancellation semantics for zero behavioral gain. Deviates from the
  design doc's `Flow<BackendEvent>` sketch; the deferred Flow alternative is
  recorded in `docs/BACKLOG.md`.
- 2026-06-15: **5.1 thinking-block round-trip = hybrid `replayPayload`.** Assistant
  turns carry their original `MessageParam` opaquely for byte-exact replay;
  neutral content exposes only text/tool_use. Avoids the neutral model having to
  represent `signature`/`redacted_thinking`, which would risk a silent 400.
- 2026-06-15: **`ModelCatalog` left Anthropic-specific in 5.1**, folded behind a
  per-provider capability in 5.3.
