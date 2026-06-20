## [2026-06-19 07:40] | Task: 修复 Gemini 多轮工具调用 400（缺少 thought_signature）

### 🤖 Execution Context
* **Agent ID**: `claude-code (session 01SteoxNdFSThg2ULfifResK)`
* **Base Model**: `Opus 4.8 (1M)`
* **Runtime**: `Claude Code on the web (remote)`

### 📥 User Query
> Claude works but Gemini is buggy: ClientException 400 "Function call is missing a
> thought_signature in functionCall parts … get_app_state, position 3". Investigate + fix.

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion/agent/llm` — GeminiBackend + GeminiMessageMapping + test.

**Key Actions:**
- **Root cause**: Gemini 2.5 thinking (`includeThoughts(true)`) attaches a `thoughtSignature` to each
  `functionCall` part that the API requires echoed back on later requests. `GeminiBackend` set
  `replayPayload = null` and `toContents` rebuilt assistant turns via `Part.fromFunctionCall(name,args)`,
  dropping it → 400 on the 2nd/3rd tool turn (turn 1 works because there's no history).
- **Fix (reuse the existing `replayPayload` mechanism Anthropic uses)**: `streamTurn` now keeps the raw
  functionCall `Part`s (which carry the signature inside them) and stashes the model `Content` —
  `GeminiMessageMapping.replayContent(text, parts)` — in `replayPayload`; `toContents` returns
  `replayPayload as? Content` verbatim for assistant turns before any rebuild. The signature round-trips
  by identity; our code never reads or sets it (robust to SDK accessor details). User/resumed-text
  history still rebuilds (no function calls → no signature needed).
- **Hardening (from a full-path audit)**: `mapStopReason` now maps `MALFORMED_FUNCTION_CALL` →
  `REFUSAL` so a malformed call surfaces in the transcript instead of ending silently. Noted the
  parallel-same-tool functionResponse name-matching limitation in BACKLOG (rare; not fixed).
- Tests: `GeminiMessageMappingTest` — replay `Content` returned `assertSame` (signature preserved by
  identity) + the no-payload rebuild path still works. V160 added.

### 🧠 Design Intent (Why)
The provider-neutral seam already had the right tool: `replayPayload` (opaque, backend-private) exists
precisely so a provider can replay its exact prior turn and preserve signatures the neutral model doesn't
represent — Anthropic uses it for extended-thinking signatures. Gemini was wrongly told it had nothing to
round-trip. Replaying the raw SDK `Part`s is the minimal, robust fix: no neutral-model pollution, no
dependence on the exact `thoughtSignature` accessor/builder name, and symmetric with the Claude path.

### 📁 Files Modified
- `.../agent/llm/GeminiBackend.kt`, `.../agent/llm/GeminiMessageMapping.kt`
- `.../test/.../llm/GeminiMessageMappingTest.kt`
- `docs/BACKLOG.md`, `VERIFICATION.md` (V160)
