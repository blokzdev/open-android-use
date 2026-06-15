## [2026-06-15 06:50] | Task: Phase 5.3 — model lists behind the provider + begin R8

### 🤖 Execution Context
* **Agent ID**: `claude-code/session_013vN6M9RAYBQnDAybhdYE7T`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote execution environment)`

### 📥 User Query
> Proceed with Phase 5.3: fold per-provider model-listing + key-validation behind
> a capability so `ModelCatalog` stops importing provider SDKs, and begin R8/minify
> keep-rules for the release build.

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` — `agent/llm` provider capability +
registry; `ModelCatalog`/`AgentController` cleanup; release R8 build config + CI gate.

**Key Actions:**
- **`ProviderModels` capability** (`agent/llm`): `validateKey`/`listModels`, with
  `AnthropicModels` (moved verbatim out of `ModelCatalog`) and `GeminiModels` (now
  implements the interface).
- **`LlmProvider` is the single registry**: adds `models: ProviderModels` and
  `createBackend(apiKey, baseUrl): AgentBackend`. `AgentController` builds its
  backend via `provider.createBackend(...)`; `ModelCatalog` delegates listing/
  validation to `provider.models`.
- **Invariant achieved**: `com.anthropic` / `com.google.genai` are now imported
  **only** under `agent/llm` — `ModelCatalog` (in `agent/`) is SDK-free.
- **R8 begun**: `isMinifyEnabled = true` for release + `proguard-rules.pro` keeping
  the LLM SDKs and their reflective JSON/transport stacks whole (and `-dontwarn` for
  optional desktop-only deps: victools jsonschema, apache-http auth →
  javax.naming/jgss). Release APK shrinks ~32M → ~8.5M.
- **CI gate**: the companion job now runs `assembleRelease` so the keep-rules are
  validated build-time on every PR.

### 🧠 Design Intent (Why)
5.1/5.2 made the loop provider-neutral but `ModelCatalog` still reached into the
Anthropic SDK directly. Folding key-validation + model-listing into a per-provider
`ProviderModels`, and backend construction into `LlmProvider.createBackend`, makes
the provider enum the **one** extension point (5.5's on-device tier = one enum arm +
one backend + one `ProviderModels`, nothing else) and confines provider SDKs to
`agent/llm`. The refactor is a pure relocation (same SDK calls), so existing tests +
compile are the guard. R8 is enabled now (roadmap folds R8-enable into Phase 5) with
conservative keep-everything-SDK rules; the big size win (unused
material-icons-extended/androidx) still lands. On-device validation of the shrunk
build and rule-tightening + resource-shrink are the Phase 6 final-shrink gate.

### 📁 Files Modified
- `app/.../agent/llm/ProviderModels.kt`, `AnthropicModels.kt` (new)
- `app/.../agent/llm/GeminiModels.kt`, `LlmProvider.kt`
- `app/.../agent/ModelCatalog.kt`, `AgentController.kt`
- `app/proguard-rules.pro` (new), `app/build.gradle.kts`
- `.github/workflows/android-runtime.yml`
- docs: this record, `docs/exec-plans/active/20260615-phase5-pluggable-models.md`, `docs/ARCHITECTURE.md`, `docs/QUALITY_SCORE.md`, `VERIFICATION.md`
