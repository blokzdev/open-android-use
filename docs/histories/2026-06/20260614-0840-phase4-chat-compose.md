## [2026-06-14 08:40] | Task: Phase 4.3 — world-class agent chat (ChatActivity → Compose)

### 🤖 Execution Context
* **Agent ID**: `claude-code`
* **Base Model**: `claude-opus-4-8`
* **Runtime**: Claude Code on the web (remote container, no Android SDK)

### 📥 User Query
> Proceed with Phase 4.3 — rebuild the agent chat in Compose into an intuitive,
> world-class experience. Approved enrichments: header polish + haptics,
> select/copy/share, markdown in answers, error/refusal card + fix. Capture the
> richer deferred ideas on the roadmap. (The user loved the "Agent's view" — the
> agent is their second pair of hands; they are the agent's second pair of eyes.)

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` chat surface (presentation layer) + one
additive agent-core hook, docs.

**Key Actions:**
- **ChatActivity → Compose** (`ComponentActivity`, still `AgentController.Listener`):
  LazyColumn transcript with user/assistant bubbles, collapsible thinking, tool
  chips, and note cards; live streaming coalesced to ~30fps; auto-scroll;
  suggested-prompt empty state; readiness banner + graceful send-path; Compose
  settings dialog (key/model/toggles); push-to-talk preserved; IME-aware composer;
  always-visible Stop; haptics on send/stop.
- **Agent's view (marquee)**: a card showing the latest screenshot the agent
  captured, updating live, tap-to-expand — "you're the agent's second pair of
  eyes". Backed by an **additive, smoke-safe** hook:
  `AgentController.latestScreenshotBase64` (+ `Listener.onScreenshotCaptured`
  default no-op); set where the screenshot is already produced; in-memory only,
  cleared on `resetConversation`. No transcript strings changed.
- **Pure, unit-tested helpers** (no Robolectric): `ToolChipLabel` (raw KIND_TOOL →
  friendly chip), `ChatMarkdown` (bold/italic/code + bullet/numbered → framework-
  agnostic model), `NoteClassifier` (KIND_NOTE → styled card + fix).
- **Enrichments**: header model chip (→ settings) + New conversation + Share
  (last answer); select/copy on assistant bubbles (`SelectionContainer`);
  markdown rendering; error/needs-key cards with one-tap fix.
- **Build/docs**: added `androidx.compose.foundation`; VERIFICATION V48–V56;
  roadmap (4.3 done + 4.4/4.5/4.6 deferred ideas captured); QUALITY_SCORE.

### 🧠 Design Intent (Why)
The chat is where users spend their time; it must feel alive and trustworthy.
The agent core stays the single source of truth (the UI only renders its
transcript + the new screenshot hook), and transcript strings are unchanged so
the emulator-smoke's assertions hold. The screenshot hook is additive and bounded
(one in-memory reference) — the highest-value trust feature at low risk.

### ⚠️ Verification note
No Android SDK in this container → the Compose compile + the three new unit tests
are gated by the CI `companion` job. `emulator-smoke` drives `AgentController`
directly and never launches ChatActivity; the screenshot callback is a default
no-op and no transcript strings changed, so its assertions hold. Settings use a
Compose `Dialog` (not `ModalBottomSheet`) to avoid experimental sheet APIs; full
reduce-motion/large-font a11y is deferred to 4.6 (tracked).

### 📁 Files Modified
- Rewrite: `agent/ChatActivity.kt` (Compose)
- New: `agent/ToolChipLabel.kt`, `agent/ChatMarkdown.kt`, `agent/NoteClassifier.kt`
  (+ `app/src/test/.../{ToolChipLabel,ChatMarkdown,NoteClassifier}Test.kt`)
- Edit (additive): `agent/AgentController.kt` (screenshot field + callback)
- `app/build.gradle.kts` (compose-foundation)
- Docs: `VERIFICATION.md`, `docs/exec-plans/active/20260612-android-use-runtime.md`,
  `docs/QUALITY_SCORE.md`
