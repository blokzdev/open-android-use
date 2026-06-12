# Phase 3.1b/3.1c — safety surfaces and voice for the on-device agent

- Date: 2026-06-12 07:00 UTC
- Scope: `apps/OpenAndroidUseCompanion` (`agent` package, CompanionService
  event hook, accessibility config, manifest), VERIFICATION/ARCHITECTURE/
  QUALITY_SCORE docs
- Plan: `docs/exec-plans/active/20260612-phase3-on-device-agent.md`
  (stretched past 3.1a in the same session)

## What changed

The interaction contract from `docs/design-docs/phase3-agent-loop.md` is now
code, not just prompt text:

- **Touch-to-pause**: the accessibility service now subscribes to
  click/long-click/text-change events; `TouchPauseMonitor` (pure logic,
  tested) treats any direct manipulation outside the agent's own gesture
  window (2.5s temporal heuristic — services cannot distinguish injected
  gestures from fingers directly) as the user reaching for the screen and
  suspends the task. The companion's own UI is exempt so the Stop button
  cannot race itself. Surfaced as its own finish state in the chat.
- **Gesture trail ("visible hands")**: a non-touchable
  `TYPE_ACCESSIBILITY_OVERLAY` view draws a fading ripple per tap and a
  fading stroke per swipe at the exact dispatched coordinates, attached for
  the task lifetime. Best-effort: overlay failure never blocks the agent.
- **Confirmation sheet**: optional (settings toggle) consent gate before
  each mutating tool batch, drawn as a touchable accessibility overlay so it
  works while the chat Activity is backgrounded — which is the normal state
  mid-task. Deny returns explanatory `tool_result` errors so the model
  re-plans instead of retrying; a 2-minute timeout fails closed.
- **Voice (3.1c)**: `VoiceNarrator` — controller-owned TTS — speaks the
  agent's streamed narration sentence-by-sentence (`SentenceBuffer`, tested)
  and goes silent on stop; push-to-talk via `SpeechRecognizer` fills the
  input field for review (no auto-send). Wake word stays out of scope.
- **Transcript buffer**: `AgentController` keeps the append-only transcript;
  the chat re-renders it on resume, fixing the silent-loss problem of a
  listener detached while the agent drives other apps.
- **Package visibility**: `QUERY_ALL_PACKAGES` so `list_apps`/app resolution
  see launchable apps on Android 11+ (sideloaded/debug distribution;
  rationale in the manifest).

26 JVM unit tests green; `assembleDebug` green. On-device acceptance:
V33–V41 in `VERIFICATION.md`.

## Notes for future rounds

- The touch-to-pause heuristic can false-negative during long scroll inertia
  (scroll events are deliberately not forwarded) and false-positive if an
  app fires synthetic click events >2.5s after an agent gesture. Tune
  `AGENT_ACTION_WINDOW_MS` against real hardware behavior.
- The confirmation sheet currently gates whole batches, not per-action; a
  per-action ladder keyed to the consent levels (send/post/purchase/delete)
  needs either model-side tagging or a destination classifier — later slice.
- Narration speaks everything the model says; a "narration channel"
  (separate short intent line vs. long answer) could come from prompting.
