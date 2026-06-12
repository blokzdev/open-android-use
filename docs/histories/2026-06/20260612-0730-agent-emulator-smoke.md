# Agent-loop emulator smoke — Milestone 4 un-deferred, no API key needed

- Date: 2026-06-12 07:30 UTC
- Scope: `apps/OpenAndroidUseCompanion` (androidTest source set, agent
  settings hook, network security config), `.github/workflows/android-runtime.yml`
- Plan: `docs/exec-plans/active/20260612-phase3-on-device-agent.md`

## What changed

Milestone 4 was deferred over "API keys in CI are awkward". Solved without
secrets: an instrumentation test (`AgentLoopEmulatorTest`) hosts
`StubModelServer` — a loopback HTTP server speaking scripted Messages-API SSE
(turn 1: text narration + `get_app_state` tool_use; turn 2: end_turn) — and
points the production SDK client at it through a new no-UI
`AgentSettings.baseUrlOverride`. The test then runs the *real* agent loop:
streaming, MessageAccumulator, ToolExecutor, a live accessibility snapshot
and a real screenshot on the emulator, tool_result assembly with the image
block, transcript logging.

Assertions cover the wire protocol both ways: the first request carries the
frozen 9-tool schema, system prompt, and adaptive thinking; the second
carries the `tool_result` for the stub's tool_use id with an image/png block.

Supporting changes:

- `network_security_config.xml`: cleartext permitted to 127.0.0.1/localhost
  only; real traffic stays HTTPS.
- `android.useAndroidX=true` (androidx.test is instrumentation-only; the
  shipped APK has no androidx code).
- CI emulator job runs `connectedDebugAndroidTest` after the existing smoke
  (which leaves the companion's accessibility service enabled), passing
  `requireCompanion=true` so a missing service is a failure, never a vacuous
  skip. The test also polls up to 30s for the service to rebind after the
  test-APK reinstall.

## Verification state

Container: `assembleDebug`, `assembleDebugAndroidTest`, and all 26 JVM unit
tests green. The connected test itself needs the CI emulator (no KVM here);
it runs in the `emulator-smoke` job on the next PR/main push.
