## [2026-06-16 00:01] | Task: Phase 5.7 — Privacy / Local-Only Mode + working loop

### 🤖 Execution Context
* **Agent ID**: `claude-code/session_013vN6M9RAYBQnDAybhdYE7T`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote execution environment)`

### 📥 User Query
> Plan + implement Phase 5.7. Originally "Phase-5 hardening" (egress review + cross-backend test
> matrix); the user reframed it: give users a single **Privacy / Local-Only Mode** umbrella toggle
> so the strong "nothing uploaded" guarantee is real, tier-gated to devices that can run Gemma 4
> E2B, with a confirmation (switch to the local model) when capable-but-not-downloaded. Also fold
> the new merge-then-plan working loop into the harness docs.

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` (`agent`, `agent/llm`, Privacy/Chat UI) + harness/docs.

**Key Actions:**
- **Pure core**: new `agent/PrivacyMode.kt` — `localOnlyAvailability(tier, modelReady)` state
  machine (`UNSUPPORTED`/`NEEDS_MODEL`/`READY`) + `effectiveProvider(localOnly, selected)`; global
  `AgentSettings.localOnlyMode`. `PrivacyModeTest` covers every branch.
- **Enforcement chokepoint**: `AgentController.startTask` resolves the provider via
  `effectiveProvider`, so **no cloud backend is ever constructed** while Local-only is on —
  structural, not advisory.
- **Settings UI**: umbrella toggle (disabled+explained on LOW tier; immediate when READY; confirm
  dialog → switch + `enqueueDownload` when NEEDS_MODEL); `ProviderSelector` locks cloud segments;
  key Save/Test guarded; `applyProvider` factored.
- **Status surfaces**: a "Local-only" badge by the chat `ModelChip`; a status row + link on the
  Privacy & data screen.
- **Egress docs**: corrected the now-false `SECURITY.md` "no upload" claim; added an English
  two-mode "Egress & data flow (Phase 5)" section (cloud BYOK vs zero-egress local-only).
- **Working loop**: CLAUDE.md Memory-harness item 7 (merge on green = CI-clean/hardware-pending →
  plan next subphase for approval), AGENTS.md pointer, REPO_COLLAB_GUIDE cadence bullet.

### 🧠 Design Intent (Why)
Phase 5's cloud BYOK quietly made the repo's "nothing leaves the device" claim false. Rather than
just document the regression, ship a real, user-controllable guarantee: force the zero-egress
on-device engine. The guarantee is made trustworthy by a **single CI-verifiable chokepoint**
(pure helper + `effectiveProvider`) even though the on-device *runtime* stays hardware-pending.
Tier-gating avoids offering a mode the device can't deliver; the confirm-on-needs-model flow makes
the provider switch + large download an informed choice. Keys are kept-and-ignored for least
friction. Decisions (user): control in Settings (status mirrored on Privacy); keep keys. The
cross-backend test matrix split to **5.8**; Gradle dependency locking deferred (BACKLOG).

### 📁 Files Modified
- New: `agent/PrivacyMode.kt`, `agent/PrivacyModeTest.kt`
- `agent/AgentSettings.kt`, `agent/AgentController.kt`, `agent/SettingsActivity.kt`,
  `agent/ChatActivity.kt`, `PrivacyActivity.kt`, `res/values/strings.xml`,
  `androidTest/.../AgentSettingsInstrumentedTest.kt`
- Docs: `docs/SECURITY.md`, `CLAUDE.md`, `AGENTS.md`, `docs/REPO_COLLAB_GUIDE.md`, exec-plan,
  `docs/QUALITY_SCORE.md`, `docs/BACKLOG.md`, `VERIFICATION.md`, this record.
