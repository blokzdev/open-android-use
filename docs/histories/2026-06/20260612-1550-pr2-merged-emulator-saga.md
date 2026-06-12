# PR #2 merged — the on-device agent is on main; emulator CI debugging notes

- Date: 2026-06-12 15:50 UTC
- Scope: PR #2 (`Phase 3.1 — on-device agent`), merged at `1183f20` with all
  three checks green, including the emulator agent-loop smoke
- Plan: `docs/exec-plans/completed/20260612-phase3-on-device-agent.md` (closed)

## Outcome

The complete Phase 3.1a–c on-device agent (chat + voice + safety surfaces),
the code-review hardening pass, and the keyless emulator agent-loop smoke are
on main. The smoke's green run is the first end-to-end execution of the
production loop on real Android: SDK streaming against the on-device stub,
tool_use → real accessibility snapshot → real screenshot → tool_result with
image → final narration, with wire-level assertions both directions.

## Emulator CI debugging notes (keep — these will bite again)

Four red runs, four distinct causes, in order:

1. **`reactivecircus/android-emulator-runner` executes each `script:` line as
   a separate `sh -c`.** Backslash line-continuations are split and fail with
   shell syntax errors. Keep commands one-per-line.
2. **`gradle connectedDebugAndroidTest` reinstalls the app APK, and a package
   update unbinds an enabled accessibility service without rebinding it** —
   the secure setting persists but the service stays dead. Our smoke now
   installs only the instrumentation APK (its own package) and drives
   `am instrument` directly from `run-android-smoke-tests.sh
   --agent-test-apk`; note `am instrument -w` exits 0 even when tests fail,
   so the script asserts on the output markers.
3. **`am instrument` force-restarts the target process, and the accessibility
   manager does not rebind the service into the instrumented process on its
   own.** The test forces a rebind by toggling
   `enabled_accessibility_services` via
   `UiAutomation.executeShellCommand` — acquired with
   `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`, or UiAutomation itself would
   displace the service it is reviving — then polls `CompanionService.isRunning`.
4. **Android's `InetAddress.getLoopbackAddress()` prefers `::1`** (desktop
   JVMs return `127.0.0.1`, which is why a local JVM repro of the exact
   SDK↔stub exchange passed). The stub listened on IPv6 loopback while the
   SDK dialed `http://127.0.0.1` → instant `ECONNREFUSED` within one process.
   Bind test/loopback servers to `InetAddress.getByName("127.0.0.1")`
   explicitly. The companion's HttpServer is unaffected because `adb forward`
   tries both address families.

Diagnostics added along the way, kept deliberately in the product:

- The agent's chat error note now carries the full exception cause chain
  (SDK wrappers like "Request failed" are useless alone).
- The smoke prints the agent transcript and dumps companion logcat
  (`OpenAndroidUse`, `TestRunner`, `AndroidRuntime:E`) on failure.

## Loose ends

- Branch deletion is blocked from this environment: the git proxy silently
  ignores ref deletions and the GitHub MCP surface has no delete-branch tool.
  `claude/phase-3-1a-agent-build-kd0om1` and `claude/open-android-use-arch-c1xqtu`
  are both fully merged and await one-click deletion in the GitHub UI.
- Hardware acceptance V33–V41 in `VERIFICATION.md` still owns the real-device
  pass.
