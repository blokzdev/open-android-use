## [2026-06-15 19:03] | Task: Phase 5.5b — on-device inference (LiteRT-LM) + function calling

### 🤖 Execution Context
* **Agent ID**: `claude-code/session_013vN6M9RAYBQnDAybhdYE7T`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote execution environment)`

### 📥 User Query
> Continue the loop into 5.5b: wire the on-device Gemma inference + function calling.
> (Founder approved bumping the project toolchain to Kotlin 2.3 to consume LiteRT-LM.)

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` — toolchain + `agent/llm` (OnDeviceBackend,
GemmaToolPrompt). New native dependency LiteRT-LM. No Go / control-surface changes.

**Key Actions:**
- **Toolchain bump**: Kotlin 2.0.21 → **2.3.20** (+ Compose compiler 2.3.20), AGP 8.5.2 →
  **8.13.2** — required because LiteRT-LM 0.13.1 ships Kotlin 2.3 metadata. Migrated the
  removed `kotlinOptions` DSL → `compilerOptions`; fixed one Kotlin-2.3 stricter-null in
  `GeminiBackend` (`chunk.parts()`).
- **LiteRT-LM runtime**: `com.google.ai.edge.litertlm:litertlm-android:0.13.1` (agent/llm
  only), `arm64-v8a` `abiFilters`, R8 keep-rules for `com.google.ai.edge.**`, optional
  `uses-native-library`. Registered in SUPPLY_CHAIN + NOTICE.
- **`OnDeviceBackend`** (real): lazy `Engine(EngineConfig(modelPath, CPU))` reused per task,
  per-turn `Conversation`, streaming `sendMessageAsync(): Flow<Message>` → sink (via
  `Content.Text`), force-close cancel/close. Replaces the 5.5a placeholder.
- **`GemmaToolPrompt`** (pure, unit-tested): the function-calling layer — render the 9 tools
  + history into a structured prompt; parse the model's fenced `tool_call` JSON into neutral
  `AgentContent.ToolUse`. Provider-portable and device-free testable.

### 🧠 Design Intent (Why)
Completes the on-device tier so the agent can run with no key and no egress. The API was
reverse-engineered from the LiteRT-LM 0.13.1 AAR via `javap` (the docs were thin):
`Engine`/`Conversation`/`Content.Text`/streaming `Flow<Message>` are all confirmed; the FC
is done as **structured prompting** (pure, fully unit-tested) rather than the
under-documented native `ToolProvider`, keeping it portable and verifiable. The bump to
Kotlin 2.3 was a deliberate, founder-approved cost to consume the runtime; the whole build
(incl. `assembleRelease` + R8 + arm64 native libs) is green. The **live inference loop is
hardware-only-verifiable** (no device / 2.6 GB model in CI), so its runtime is staged in the
VERIFICATION ledger; everything CI can prove (toolchain, native integration, R8, the FC
format contract) is green here. Release APK grew to ~31 M (the native runtime; the model is
still downloaded, not bundled) — flagged for the Phase 6 size pass.

### 📁 Files Modified
- `apps/OpenAndroidUseCompanion/build.gradle.kts`, `app/build.gradle.kts`,
  `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`
- New: `agent/llm/GemmaToolPrompt.kt`, `agent/llm/GemmaToolPromptTest.kt`
- `agent/llm/OnDeviceBackend.kt` (placeholder → real), `agent/llm/GeminiBackend.kt` (null fix),
  `agent/llm/OnDeviceProviderTest.kt`
- `NOTICE`, `docs/SUPPLY_CHAIN_SECURITY.md`, and the exec-plan / ARCHITECTURE / QUALITY_SCORE /
  BACKLOG / VERIFICATION updates
