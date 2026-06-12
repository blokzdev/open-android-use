# On-device agent — code-review hardening pass

- Date: 2026-06-12 08:00 UTC
- Scope: `apps/OpenAndroidUseCompanion` agent package + CompanionService hook,
  debug source set, CI workflow, design doc
- Follows: the 3.1a–c build and emulator-smoke commits this session

## What prompted this

Ran a high-effort multi-angle review (7 finder angles + verify) over the
session's branch diff. The finders agreed on a cluster of real defects; this
pass fixes the confirmed correctness, security, and altitude findings. The
JSON/SSE/test wire contracts were independently verified correct and left
alone.

## Fixes

Correctness:
- **Conversation-wedging 400** — the assistant `tool_use` turn is appended to
  history before the batch runs, so an interruption (Stop / touch-pause /
  max_tokens) used to leave it with no matching `tool_result` and every
  subsequent message 400'd. Now every `tool_use` gets a result (real or an
  "interrupted" error) before the loop returns, so the conversation stays
  resumable.
- **Agent pausing itself** — `press_key`'s IME-enter and delete paths bypassed
  the gesture stamp, and `TYPE_VIEW_TEXT_CHANGED` (overwhelmingly programmatic)
  was in the interaction set, so the agent paused on its own typing or on app
  field updates during long model turns. Stamped those paths; dropped
  TEXT_CHANGED (kept CLICKED/LONG_CLICKED/SCROLLED, so scroll-to-take-over
  still pauses); `reset()` now stamps `now` (window was pre-expired); window
  widened to 4s.
- **Model blinded by pruning** — the screenshot window counted image-less
  results (list_apps, errors), evicting the only real screenshot and appending
  a false "(screenshot omitted)" to results that never had one. Now only
  image-bearing entries count and the note is added only where an image was
  removed. The heavy full param is dropped on prune (no heap leak in the
  always-on service).
- **Resume/streaming render** — collapsed the dual render paths (live
  per-event callbacks + full re-render) into one: the transcript is the single
  source of truth and the chat renders incrementally via a view cursor. Fixes
  the double-append race and the live-vs-resume text drift.

Security:
- `baseUrlOverride` is now honored only in `BuildConfig.DEBUG` and only for
  `http://127.0.0.1`/`localhost`; the cleartext network-security config moved
  to `src/debug`, so release builds restore the platform default (cleartext
  denied). Removes the hidden, key-bearing redirect surface in shipped builds.

Robustness / altitude:
- Stop works during the confirmation sheet (`ConfirmationSheet.cancel()`
  releases the parked loop thread).
- Restored the control-surface boundary by inversion: `CompanionService`
  exposes a neutral `interactionListener` hook and the agent registers it, so
  no control-surface file imports the agent package or the SDK (verified by
  grep over CompanionService/HttpServer/SnapshotBuilder/ActionExecutor/Protocol).
- Conversation-prefix prompt caching via top-level `cache_control`.
- `remember()` drops the `"foreground"` alias (matches the bridge); decrypt
  failure clears the stored key; consolidated `_input` parsing, element-point
  conversion, and the launchable-apps query (now cached per task).

Docs/CI: design doc states the agent-package dependency exception; the build
script is packaging-only again and the CI companion job runs the unit tests.

## Deferred (noted for hardware day)

- Moving `SnapshotBuilder`'s tree walk off the main looper (per-action snapshot
  amplifies a pre-existing main-thread cost) — too risky to change control-
  surface threading without a device to validate.
- `ImageBudget`'s fixed-step downscale loop could jump by `sqrt(budget/size)`;
  left the tested fixed-step contract in place for now.
- Per-action (vs per-batch) consent granularity.

## Verification

27 JVM unit tests green (added touch-pause reset coverage); `assembleDebug`,
`assembleDebugAndroidTest`, Go vet/test all green in container. The connected
agent-loop smoke runs in the CI emulator job.
