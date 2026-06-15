## [2026-06-15 20:52] | Task: Phase 5.6 — adaptive perception (vision vs text-first)

### 🤖 Execution Context
* **Agent ID**: `claude-code/session_013vN6M9RAYBQnDAybhdYE7T`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote execution environment)`

### 📥 User Query
> Plan + implement Phase 5.6 (adaptive perception). Make on-device vision-on by default
> with a user toggle; extend the vision on/off control to the cloud models too; confirm
> text-only is effective enough.

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` `agent` + `agent/llm`. No Go / control-surface changes.

**Key Actions:**
- **Per-provider `sendScreenshots` toggle** (`AgentSettings`, `slotSuffix` pattern, **default
  ON for every provider**) + a pure **`PerceptionMode`** (`VISION`/`TEXT_ONLY`).
- **`AgentController`** computes `captureScreenshots` from the selected provider's setting and
  threads it to **`ToolExecutor`**; in text-only mode `capture()` builds the a11y tree at
  **scale 1.0** and **skips `ScreenCapture`/`ImageBudget`** (no image — faster/cheaper/more
  private). All 9 tools still work (the tree carries `[index]` + bounds).
- **`OnDeviceBackend`** now sends the latest screenshot to Gemma as `Content.ImageBytes` when
  present (vision-capable `EngineConfig`: `visionBackend` + `maxNumImages = 1`; LiteRT-LM loads
  the image encoder only when an image is actually sent, so text-only stays lightweight).
- **Settings**: a "Send screenshots (vision)" Behavior toggle bound to the selected provider.
- **Tests**: `PerceptionModeTest` + a per-provider `sendScreenshots` settings test.

### 🧠 Design Intent (Why)
On-device shipped (5.5) without text-first, so a 2B Gemma + screenshots wasn't usable; and
cloud users may want to never send screenshots (privacy/cost). Review corrected two
assumptions: **all three providers are vision-capable** (Claude/Gemini already receive
screenshots; **Gemma 4 E2B is multimodal** and LiteRT-LM lazy-loads the vision encoder), so
perception is a **per-provider preference, not a capability gate**. Per the founder, **vision
defaults ON everywhere** (max capability, incl. showcasing Gemma multimodal), with text-only as
a deliberate opt-out. Text-only is fully functional because the a11y tree gives exact element
indices + bounds (often more reliable than visual grounding for native apps); vision is the
fallback for a11y-poor surfaces (custom Canvas, WebViews, image content) + visual verification.
Cloud stays vision-default, so the emulator smoke is unchanged. The on-device image path is
hardware-validated (no device/2.6 GB model in CI); the perception core is fully CI-green.

### 📁 Files Modified
- New: `agent/PerceptionMode.kt`, `agent/PerceptionModeTest.kt`
- `agent/AgentSettings.kt`, `agent/AgentController.kt`, `agent/ToolExecutor.kt`,
  `agent/llm/OnDeviceBackend.kt`, `agent/SettingsActivity.kt`, `res/values/strings.xml`,
  `androidTest/.../AgentSettingsInstrumentedTest.kt`
- Docs: this record, exec-plan, `ARCHITECTURE.md`, `QUALITY_SCORE.md`, `BACKLOG.md`, `VERIFICATION.md`
