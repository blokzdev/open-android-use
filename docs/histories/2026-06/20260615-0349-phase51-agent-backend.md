## [2026-06-15 03:49] | Task: Phase 5.1 — introduce the AgentBackend provider seam

### 🤖 Execution Context
* **Agent ID**: `claude-code/session_013vN6M9RAYBQnDAybhdYE7T`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote execution environment)`

### 📥 User Query
> Proceed with thorough planning of Phase 5.1, then implement it. (Phase 5.1 =
> the pure, behavior-preserving provider abstraction that decouples the agent
> loop from the Anthropic SDK; Gemini + on-device land in 5.2+.) Capture the
> deferred Flow alternative in the backlog with reasoning.

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` — new `agent.llm` package; `agent`
package (`AgentController`, `AgentTools`, `SessionHistory`) refactored onto it.
No control surface, no Go bridge, no new dependency.

**Key Actions:**
- **New `agent.llm` seam**: `AgentBackend` (blocking `streamTurn`/`cancel`/`close`),
  `BackendRequest`/`BackendSink`/`CompletedTurn`/`AgentStopReason`/`BackendStreamException`,
  and the neutral history model `AgentMessage`/`AgentContent`/`ToolImage` plus `ToolSpec`.
  None of these import `com.anthropic`.
- **`AnthropicBackend` + `AnthropicMessageMapping`**: the existing SDK path
  extracted behind the seam — verbatim `buildParams` (prompt-cache + adaptive
  thinking + effort high), accumulate-and-stream loop, force-close cancel, and
  the tool/message translation. The only place (with `ModelCatalog`) that still
  imports `com.anthropic`.
- **Hybrid `replayPayload`**: assistant turns carry their original `MessageParam`
  opaquely for byte-exact replay, so extended-thinking `signature` data is never
  lost; the loop reads only neutral text/tool_use blocks.
- **Loop refactor**: `AgentController` history is `AgentMessage`; `runLoop` drives
  `backend.streamTurn` with a `BackendSink`; stop button → `backend.cancel()`;
  switch over `AgentStopReason`. `buildParams`/`emitDelta` moved into the backend.
  `SessionHistory.rebuild` returns neutral messages. `AgentTools.specs()` is the
  sole tool source; `definitions()` removed.
- **Tests**: added `AnthropicToolMappingTest`, `AnthropicMessageMappingTest`
  (thinking-signature round-trip), `ToolResultMappingTest`; migrated
  `SessionHistoryTest`/`AgentToolsTest` to the neutral types. Keyless emulator
  smoke (`AgentLoopEmulatorTest` + `StubModelServer`) unchanged.

### 🧠 Design Intent (Why)
Phase 5 needs a provider-agnostic seam so Gemini (5.2) and on-device Gemma (5.4+)
plug in without re-touching the agent loop, its safety gates, or screenshot
pruning. 5.1 establishes that seam as a pure refactor: same Anthropic wire, so
the unchanged keyless smoke is the behavior-preservation proof. A blocking sink
(not Flow) was chosen because the loop is a single sequential consumer on a
dedicated worker thread that cancels by force-closing the stream — Flow's
machinery would be unused; the deferred Flow alternative is recorded in
`docs/BACKLOG.md`. The hybrid `replayPayload` keeps thinking signatures lossless
without the neutral model having to represent them.

### 📁 Files Modified
- `apps/OpenAndroidUseCompanion/app/src/main/kotlin/dev/openandroiduse/companion/agent/llm/AgentBackend.kt` (new)
- `apps/OpenAndroidUseCompanion/app/src/main/kotlin/dev/openandroiduse/companion/agent/llm/AgentMessage.kt` (new)
- `apps/OpenAndroidUseCompanion/app/src/main/kotlin/dev/openandroiduse/companion/agent/llm/ToolSpec.kt` (new)
- `apps/OpenAndroidUseCompanion/app/src/main/kotlin/dev/openandroiduse/companion/agent/llm/AnthropicBackend.kt` (new)
- `apps/OpenAndroidUseCompanion/app/src/main/kotlin/dev/openandroiduse/companion/agent/llm/AnthropicMessageMapping.kt` (new)
- `apps/OpenAndroidUseCompanion/app/src/main/kotlin/dev/openandroiduse/companion/agent/AgentController.kt`
- `apps/OpenAndroidUseCompanion/app/src/main/kotlin/dev/openandroiduse/companion/agent/AgentTools.kt`
- `apps/OpenAndroidUseCompanion/app/src/main/kotlin/dev/openandroiduse/companion/agent/SessionHistory.kt`
- `apps/OpenAndroidUseCompanion/app/src/test/kotlin/dev/openandroiduse/companion/agent/llm/AnthropicToolMappingTest.kt` (new)
- `apps/OpenAndroidUseCompanion/app/src/test/kotlin/dev/openandroiduse/companion/agent/llm/AnthropicMessageMappingTest.kt` (new)
- `apps/OpenAndroidUseCompanion/app/src/test/kotlin/dev/openandroiduse/companion/agent/llm/ToolResultMappingTest.kt` (new)
- `apps/OpenAndroidUseCompanion/app/src/test/kotlin/dev/openandroiduse/companion/agent/AgentToolsTest.kt`
- `apps/OpenAndroidUseCompanion/app/src/test/kotlin/dev/openandroiduse/companion/agent/SessionHistoryTest.kt`
- `docs/exec-plans/active/20260615-phase5-pluggable-models.md` (new)
- `docs/design-docs/phase5-multi-provider-byok.md`, `docs/ARCHITECTURE.md`, `docs/QUALITY_SCORE.md`, `docs/BACKLOG.md`, `VERIFICATION.md`
