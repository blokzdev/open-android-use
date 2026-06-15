## [2026-06-15 04:49] | Task: Phase 5.2 — Gemini BYOK + provider/model switcher

### 🤖 Execution Context
* **Agent ID**: `claude-code/session_013vN6M9RAYBQnDAybhdYE7T`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote execution environment)`

### 📥 User Query
> Continue Phase 5.2: add Google Gemini as a second BYOK provider behind the 5.1
> `AgentBackend` seam, with a provider toggle + per-provider key + model picker.
> Make the Gemini model picker behave like Claude (live key-test + live model
> list); default Gemini to `gemini-2.5-pro`; surface the current 3.x lineup.

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` — new Gemini backend in `agent/llm`;
per-provider `AgentSettings`; provider-aware `ModelCatalog`; provider dispatch in
`AgentController`; provider selector in Settings + Onboarding. New dependency
`com.google.genai`. No Go bridge / control-surface changes.

**Key Actions:**
- **Dependency**: pinned `com.google.genai:google-genai:1.58.0` (agent-package
  only); packaging `pickFirsts` for the transitive public-suffix list; registered
  in `SUPPLY_CHAIN_SECURITY.md` + `NOTICE` (Apache-2.0 incl. transitive gson/
  http-client/protobuf).
- **`LlmProvider`** enum (ANTHROPIC, GEMINI): display name, default model, key-help
  URL, fallback model list.
- **`AgentSettings`** per-provider + zero-migration: Anthropic keeps its legacy
  pref/Keystore-alias slots; Gemini uses `_gemini` slots. `selectedProvider` drives
  the no-arg accessors.
- **`GeminiBackend` + `GeminiMessageMapping` + `GeminiModels`**: stream via
  `generateContentStream` (deltas to the sink, thought parts → thinking), 9 tools →
  function declarations, neutral history ↔ Gemini Contents (functionResponse +
  inline image), function calls → neutral tool_use (name encoded in the id, since
  Gemini matches responses by name). Live key validation + model listing.
- **`ModelCatalog`** is provider-aware (validate + live list); **`AgentController`**
  dispatches the backend by provider — the only provider-specific line in the loop.
- **UI**: Settings + Onboarding gain a Claude/Gemini selector; all key/model
  controls re-scope to the selected provider; provider-named copy + per-provider
  key-help link.
- **Tests**: `GeminiToolMappingTest`, `GeminiMessageMappingTest`,
  `GeminiBackendStreamingTest` (real genai decode vs a loopback Gemini-SSE stub);
  per-provider key slots in `AgentSettingsInstrumentedTest`; provider selector in
  `SettingsScreenTest`; a Gemini full-loop path in the emulator smoke.

### 🧠 Design Intent (Why)
5.1 made the loop provider-neutral; 5.2 proves it by adding a real second provider
with **zero loop changes** beyond one backend-construction line. Gemini has no
signed-thinking round-trip, so its `replayPayload` is null and it reconstructs from
neutral blocks. Live model listing (not a static list) keeps the picker honest as
Google ships models (the current lineup already includes 3.1-pro-preview /
3.5-flash / 3.1-flash-lite); we default to stable `gemini-2.5-pro` because Google
retires `-preview` ids. SDK builder spellings were pinned against the 1.58.0 jar
with `javap` (as 5.1 did for Anthropic), and the Gemini SSE wire was de-risked with
a JVM streaming test before wiring the loop.

### 📁 Files Modified
- `app/.../agent/llm/LlmProvider.kt`, `GeminiBackend.kt`, `GeminiMessageMapping.kt`, `GeminiModels.kt` (new)
- `app/.../agent/AgentSettings.kt`, `ModelCatalog.kt`, `AgentController.kt`
- `app/.../agent/SettingsActivity.kt`, `OnboardingActivity.kt`, `res/values/strings.xml`
- `app/build.gradle.kts`, `NOTICE`, `docs/SUPPLY_CHAIN_SECURITY.md`
- tests: `agent/llm/Gemini*Test.kt` (new), `AgentSettingsInstrumentedTest.kt`, `SettingsScreenTest.kt`, `StubModelServer.kt`, `AgentLoopEmulatorTest.kt`
- docs: this record, `docs/exec-plans/active/20260615-phase5-pluggable-models.md`, `docs/ARCHITECTURE.md`, `docs/QUALITY_SCORE.md`, `docs/design-docs/phase5-multi-provider-byok.md`, `VERIFICATION.md`
