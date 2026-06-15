# Backlog — someday / maybe

Unscheduled ideas we've deliberately deferred, kept here so they aren't lost.

- **This is not the roadmap.** Scheduled, in-flight work lives in
  `docs/exec-plans/active/`. This file is the someday/maybe list: ideas worth
  remembering but not currently planned.
- **Promote when scheduled.** When an item is picked up, move it into an
  execution plan (`docs/exec-plans/active/`) and delete it here.
- **Defer with discipline.** When you cut something out of scope, add it here in
  one line with a rationale and a rough priority, so the decision is recoverable.

Each entry: **idea** — why deferred · _priority_ · origin.

## Android runtime / bridge

- **Robust no-companion bridge snapshot** — the host-side bridge reads the UI via
  the `uiautomator dump` CLI, which is brittle (idle-wait timeouts / null root,
  worse right after boot and on Android 11+). It's now hardened with
  escalating-backoff retries, but a fundamentally more reliable *no-companion*
  capture would need a minimal instrumentation APK driving `UiAutomation` with
  controlled idle-wait (the only three avenues are: the `uiautomator dump` CLI, an
  instrumentation APK, or an AccessibilityService — i.e. the companion, which
  already provides the robust path). _Low / investigate-if-needed_ — pursue only if
  the retries prove insufficient on real fleets; otherwise steer
  robustness-sensitive users to the companion. Origin: PR #9 get_app_state
  hardening.
- **`uiautomator dump --compressed` fallback** — a last-resort fallback if the
  retries still fail; it drops nodes (changes tree content), so only behind the
  full dump. _Low._ Origin: PR #9.

## On-device agent / chat

- **Structured tool chips with element labels** — chips currently prettify the
  pre-formatted `KIND_TOOL` transcript string (e.g. "Tap [42]"); showing the
  tapped element's *label* ("Tap Settings") needs richer data threaded from the
  agent core, not just the string. _Medium._ Origin: Phase 4.3 chat.
- **Full-fidelity session resume** — resume currently rebuilds the model history
  from the saved text transcript (`SessionHistory`), so raw tool_use/tool_result/
  thinking blocks and screenshots aren't replayed (the agent re-observes the device
  live). True block-level fidelity would need persisting the Anthropic
  `MessageParam` history, but the SDK exposes no public serialization
  (`ObjectMappers.jsonMapper()` is `internal`; models have no toJson/fromJson —
  confirmed by research, 2026-06). _Low / probably-unwanted_ — text-rebuild is also
  better for privacy (no screenshots on disk); revisit only if the SDK ships public
  serialization and a real need appears. Origin: Phase 4.5.

## UI / theming

- **Runtime theme switch without `recreate()`** — the Material You toggle currently
  re-themes back-stack screens by `recreate()`-on-resume. The modern pattern is an
  observable theme (StateFlow/DataStore) collected via `collectAsState` so screens
  recompose in place with no flash. _Low–Medium._ Fold into Phase 4.6's design-system
  work (which may introduce DataStore/ViewModel). Origin: Phase 4.5.1.

## Adaptive / large-screen (deferred from Phase 4.6)

- **Hinge-aware two-pane** — the 4.6e tablet/foldable two-pane splits by width class only;
  it doesn't avoid a foldable's hinge. Add `androidx.window` (`HingeInfo`/`Posture`) to place
  the divider on the hinge and pad around it. _Low._ Origin: Phase 4.6e.
- **Adopt `NavigableListDetailPaneScaffold`** — if app-wide adaptive navigation is wanted
  later (predictive-back pane nav, canonical list-detail), migrate the History↔Chat two-pane
  (and possibly Settings/Privacy) to the official Material3-adaptive scaffold instead of the
  manual `WindowSizeClass` Row. Bigger refactor (re-houses navigation). _Low/Medium._ Origin: Phase 4.6e.
- **Two-pane Settings/Privacy on large screens** — a supporting-pane layout for the
  settings cluster on tablets/foldables. _Low._ Origin: Phase 4.6e.
- **Responsive follow-ups from 4.6c** — content max-width for Onboarding and the chat
  message list; an adaptive (height-fraction) "Agent's view" instead of the fixed 200dp.
  _Low._ Origin: Phase 4.6c.

## UX delights (captured during the Phase 4.7 sweep, not yet scheduled)

- **Model picker as a bottom sheet** — replace the dropdown with a sheet showing each model
  with a short description/"recommended" hint. _Low._ Origin: Phase 4.7.
- **Richer confirmation sheet** — per-action icons and an "always allow for this app" option in
  `ConfirmationSheet`. _Low–Medium._ Origin: Phase 4.7.
- **Draggable in-control badge showing the live action** — let the user reposition the badge and
  show the current tool/step on it. _Low._ Origin: Phase 4.7.
- **Conversation folders / tags** — organize History beyond pin/archive. _Low._ Origin: Phase 4.7.
- **Multi-select bulk archive/delete in History** — deferred from Phase 4.7c-2. A selection mode
  (long-press to enter, per-row checkboxes, a contextual action bar with bulk archive/delete +
  batch undo, back-to-exit) on the shared `SessionsList`, interoperating with search/grouping.
  Lower value than the per-row actions already shipped and hard to tune well without a device.
  _Low–Medium._ Origin: Phase 4.7c-2b (deferred).
- **Pull-to-refresh on History**; **AMOLED (true-black) theme** option; **message reactions /
  feedback** (👍/👎 on answers to inform future tuning). _Low._ Origin: Phase 4.7.
- **Per-message timestamps in the Markdown export** — the chat now shows per-message times
  (4.7b-3b) and `StoredMessage`/`TranscriptEntry` carry `createdAt`, but `ConversationExport`
  still renders text-only. Add a compact time to the role headings (pure formatter). _Low._
  Origin: Phase 4.7b-3b.
- **In-bubble partial text selection** — 4.7b-1 replaced the assistant bubble's
  `SelectionContainer` with a long-press Copy/Share menu (whole-message), since long-press can't
  drive both text selection and a context menu. Re-add fine-grained selection (e.g. a "Select
  text" menu action opening a selectable view, or a selection toolbar) if users want to copy a
  fragment. _Low._ Origin: Phase 4.7b-1.
