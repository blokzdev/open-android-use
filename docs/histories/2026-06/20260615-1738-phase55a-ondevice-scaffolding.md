## [2026-06-15 17:38] | Task: Phase 5.5a — on-device provider scaffolding + model management

### 🤖 Execution Context
* **Agent ID**: `claude-code/session_013vN6M9RAYBQnDAybhdYE7T`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote execution environment)`

### 📥 User Query
> Plan + implement Phase 5.5 (on-device Gemma tier). Full feature, split across
> sub-PRs; runtime = LiteRT-LM + Gemma 4 E2B. (This is 5.5a: the CI-verifiable
> scaffolding + model management; 5.5b wires inference on hardware.)

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` `agent` + `agent/llm` + Settings/Onboarding.
New dependency `androidx.work`. No Go / control-surface changes.

**Key Actions:**
- **`LlmProvider.ON_DEVICE`** arm + `requiresApiKey`; `createBackend(credential, baseUrl)`
  now takes an API key (cloud) **or** a local model path (on-device).
- **`OnDeviceModelManager`** + **`OnDeviceModelDownloadWorker`**: pinned,
  content-addressed (SHA-256 = git-LFS oid) Gemma 4 E2B source
  (`litert-community/gemma-4-E2B-it-litert-lm`, 2.58 GB, Apache-2.0, ungated);
  WorkManager download to `filesDir/models` with atomic `.part`→final rename,
  integrity verify, free-space guard, progress, cancel, delete.
- **`OnDeviceModels`** (no key; single local model) completes the `ProviderModels` seam.
- **`AgentController`** gates an on-device task on a *ready model* (not a key).
- **Settings**: a download card (size, tier suitability, download/cancel/delete,
  progress) replaces the key field for the on-device provider; **Onboarding** shows a
  "download later in Settings" note. Both gate via `DeviceTier`.
- **`OnDeviceBackend`** is a placeholder that ends the turn with a clear note — 5.5b
  replaces it with LiteRT-LM streaming inference + function calling.
- **Tests**: integrity verifier (SHA-256 vector, match/mismatch/pin sanity) + provider
  dispatch/placeholder behavior. Supply-chain: `androidx.work` registered + the
  pinned-sha256 model-integrity policy.

### 🧠 Design Intent (Why)
On-device is the product's "key never leaves the device" north star. The seam from
5.1–5.4 made adding it a contained change: one provider arm + a `ProviderModels` + a
backend. The whole feature can't be runtime-verified in CI (no device; 2.6 GB model;
no on-device inference), so 5.5a carries everything that *is* CI-verifiable
(download/integrity/gating/UI/dispatch — all green incl. `assembleRelease`), and the
inference (5.5b) is isolated for hardware validation. Model trust is content-addressed:
a pinned SHA-256 (the LFS oid) is verified before the `.part` is promoted to the real
file, so a corrupt/substituted download is refused. On-device is framed as an
experimental, capable-tier-gated, opt-in tier; cloud stays the default.

### 📁 Files Modified
- New: `agent/OnDeviceModelManager.kt`, `agent/OnDeviceModelDownloadWorker.kt`,
  `agent/llm/OnDeviceModels.kt`, `agent/llm/OnDeviceBackend.kt`,
  `agent/OnDeviceModelManagerTest.kt`, `agent/llm/OnDeviceProviderTest.kt`
- Edit: `agent/llm/LlmProvider.kt`, `agent/AgentController.kt`, `agent/SettingsActivity.kt`,
  `OnboardingActivity.kt`, `app/build.gradle.kts`, `res/values/strings.xml`
- Docs: this record, `docs/exec-plans/active/20260615-phase5-pluggable-models.md`,
  `docs/ARCHITECTURE.md`, `docs/QUALITY_SCORE.md`, `docs/SUPPLY_CHAIN_SECURITY.md`,
  `docs/BACKLOG.md`, `VERIFICATION.md`
