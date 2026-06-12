# Phase 3.1a — on-device agent shipped (companion-hosted Claude loop)

- Date: 2026-06-12 06:30 UTC
- Scope: `apps/OpenAndroidUseCompanion` (`agent` package), build script,
  ARCHITECTURE/QUALITY_SCORE/VERIFICATION/SUPPLY_CHAIN docs
- Plan: `docs/exec-plans/active/20260612-phase3-on-device-agent.md`

## What changed

The companion app now hosts a complete on-device agent: a chat Activity where
the user types a task, Claude sees the screen and acts through the same
9-tool Computer Use surface the host runtimes expose — no computer, no cable.

- **SDK**: official Anthropic Java SDK, pinned `com.anthropic:anthropic-java`
  2.40.1, scoped to the `agent` package; the control surface stays
  dependency-free (registered exception in `docs/SUPPLY_CHAIN_SECURITY.md`).
  Every binding used was verified against the published jar with `javap`
  before writing code.
- **Tool surface**: the 9 schemas ported verbatim from the Go bridge's
  `toolDefinitions()`; `ToolExecutor` executes them in-process against the
  CI-verified SnapshotBuilder/ActionExecutor paths with bridge-identical
  semantics (element indexing, CoordinateScale, snapshot-before-action,
  800ms settle, fresh snapshot after each action). `press_key` maps onto the
  accessibility action surface; unsupported keys fail loud.
- **Loop**: manual agentic loop (not the SDK tool runner) — streaming,
  adaptive thinking (summarized display), effort high, frozen tools+system
  prompt with a prompt-cache breakpoint, refusal stop-reason handling,
  pause_turn resume, cancel gate between stream events and tool executions,
  screenshot pruning beyond a 2-result window.
- **Key custody**: API key AES/GCM-encrypted with a non-exportable Android
  Keystore key; model selectable, default `claude-opus-4-8`.
- **Verification**: Milestone-1 gate passed in-container (SDK resolves,
  `assembleDebug` green with META-INF merge excludes); 17 JVM unit tests
  (tool surface determinism, tree flattening vs bridge format, key mapping,
  budget math) now run in the CI companion build. On-device acceptance is
  V33–V37 in `VERIFICATION.md`.

## Notes for future rounds

- Background activity-launch restrictions may block `get_app_state` from
  launching apps mid-task on some OEM ROMs; the executor surfaces reality in
  the next snapshot and the model adapts. Watch this on hardware day.
- The model list is hardcoded for 3.1a; a models-API fetch is a later slice.
- 3.1b (touch-to-pause, confirmation sheet, gesture trail) builds directly on
  AgentController's listener and ActionExecutor's dispatch path.
