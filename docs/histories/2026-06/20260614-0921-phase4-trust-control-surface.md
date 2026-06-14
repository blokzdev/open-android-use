## [2026-06-14 09:21] | Task: Phase 4.4 — trust & control surface

### 🤖 Execution Context
* **Agent ID**: `claude-code`
* **Base Model**: `claude-opus-4-8`
* **Runtime**: Claude Code on the web (remote container, no Android SDK)

### 📥 User Query
> Comprehensive planning + build of Phase 4.4 (trust/control surface), per the
> second-pair-of-hands vision: the user always knows when the agent is acting,
> can stop it from anywhere, and can see what it just did.

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` agent + presentation layer, docs.

**Key Actions:**
- **In-control badge** (`agent/InControlOverlay.kt`): a touchable
  `TYPE_ACCESSIBILITY_OVERLAY` chip ("👐 Open Android Use is acting · Stop")
  shown over every app while a task runs — Stop from anywhere → `requestStop()`,
  label opens the chat. Mirrors `ConfirmationSheet`'s overlay; best-effort.
  Attached/detached alongside `GestureTrail` in `AgentController.startTask`.
- **Ongoing Stop notification** (`agent/AgentNotification.kt` + top-level
  `StopAgentReceiver`): an ongoing notification with a Stop action while a task
  runs. **Not** a foreground service (the accessibility service is already kept
  alive; FGS types are a 4.6/Play concern). POST_NOTIFICATIONS-gated — silently
  no-ops without it (the badge is the guaranteed control); requested from
  `ChatActivity` via the Activity Result API.
- **Tap-location highlight** on the chat's "Agent's view": pure
  `agent/TapHighlight.kt` (`devicePixelToNormalized`, respects the CoordinateScale
  invariant, unit-tested); `ToolExecutor` records the last gesture point as a
  normalized fraction (using the latest screenshot's scale+dims); `AgentController`
  exposes `latestTapNormalized` (additive, cleared on reset); the chat draws a
  mint ring on the screenshot at that fraction (ContentScale.Fit-aware Canvas).
- **Docs**: VERIFICATION V57–V61; roadmap 4.4 done; QUALITY_SCORE; manifest
  (POST_NOTIFICATIONS + receiver).

### 🧠 Design Intent (Why)
For a *second pair of hands*, trust requires the agent's control to be visible
and stoppable from anywhere — not buried in one screen. The badge is the
guaranteed always-on control (no permission, works over any app); the
notification is a permission-gated convenience; the tap highlight closes the loop
on "what it did". All hooks are additive and best-effort, keeping `AgentController`
the stable source of truth and changing no transcript strings.

### ⚠️ Verification note
No Android SDK in this container → the compile + `TapHighlightTest` are gated by
the CI `companion` job. The new overlay/notification calls are wrapped in
try/catch and the notification self-skips without POST_NOTIFICATIONS, so the
`emulator-smoke` (which drives `AgentController` directly) is unaffected; no
transcript strings changed. Chose a normal ongoing notification over
`startForeground` to avoid Android 14+ foreground-service-type + Play justification
(tracked for 4.6).

### 📁 Files Modified
- New: `agent/InControlOverlay.kt`, `agent/AgentNotification.kt`,
  `agent/TapHighlight.kt` (+ `app/src/test/.../TapHighlightTest.kt`),
  `StopAgentReceiver.kt`
- Edit: `agent/AgentController.kt` (badge/notification lifecycle;
  `latestTapNormalized`), `agent/ToolExecutor.kt` (record tap + screenshot dims),
  `agent/ChatActivity.kt` (tap marker overlay; POST_NOTIFICATIONS request),
  `AndroidManifest.xml`
- Docs: `VERIFICATION.md`, `docs/exec-plans/active/20260612-android-use-runtime.md`,
  `docs/QUALITY_SCORE.md`
