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

> **Phase 6 (world-class app) is in progress** — `docs/exec-plans/active/20260616-phase6-world-class.md`
> schedules many of the UX/refinement/modernization items below (labeled actions ✓, richer live
> feedback, reliability/perception, safety depth, model-picker bottom sheet, ToolChipLabel i18n,
> reactive theme, two-pane Settings, zh-CN, etc.). Each is removed from here as its sub-phase PR
> lands. Launch-readiness/hardening items moved to **Phase 7/8** (see the master roadmap).

## Android runtime / bridge

- **6.5c — per-app "always allow" + screenshot suppression on sensitive screens** — 6.5a (password
  gate) and 6.5b (payment heuristic + default-on user toggle) shipped. Remaining: a per-app
  allowlist so the user can let the agent operate on a trusted app's sensitive screens — the gate
  hook is trivial (`snapshot.packageName !in allowList`), the work is the **consent UI** to add
  entries (a button on the refusal / Privacy screen + a confirm dialog) and `Set<String>` pref
  storage. Also: optionally suppress the screenshot on a sensitive screen (a11y already masks the
  field value, but the surrounding screen still uploads in vision mode). _Medium._ Origin: Phase
  6.5b (deferred). Note: payment detection is a **label heuristic** (cvv/cvc/csc, "card number",
  "credit/debit card", "security code", "card verification") because `AccessibilityNodeInfo` does
  not expose autofill hints; revisit if a more authoritative signal becomes reachable.
- **6.4 — perception richness (scroll/modal hints in the a11y text)** — deferred after 6.3b. Focus
  already ships in the 6.3b action diff; the remaining wins (scroll "can-scroll-further", modal/
  dialog hint, adaptive breadth-first tree budget) change the rendered tree-line **text**, which is
  kept byte-aligned across the Kotlin companion (`SnapshotFlattener`) and the Go bridge
  (`flattenCompanionTree`) flatteners — each pinned by its own test. So every signal must be
  mirrored in both runtimes (the value *is* the text format, unlike 6.5's logic-only change). Lower
  marginal value / higher cost than 6.5. _Medium._ Origin: Phase 6.4 (deferred 2026-06-16).
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
- **Adaptive-perception refinements (Phase 5.6 follow-ups)** — (a) a text-only system-prompt
  hint so the model leans on element indices when no screenshot is present (the prompt is frozen
  for cache stability, so add it per-mode); (b) a tier-based default for the vision toggle
  (auto-off on LOW devices); (c) multi-image on-device (currently sends only the latest
  screenshot, `maxNumImages=1`); (d) a clearer "text-only mode" affordance in the chat's
  Agent's-view panel. _Low._ Origin: Phase 5.6.
- **Build-level supply-chain enforcement** — direct deps are pinned + registered, but there's no
  Gradle dependency locking (`gradle.lockfile`) or `gradle/verification-metadata.xml` (sha256
  checksum/signature enforcement). Deferred from 5.7: AGP makes verification-metadata brittle
  (large, platform-specific artifacts, frequent churn), so it needs a dedicated, careful pass.
  _Medium._ Origin: Phase 5.7 (user call).
- **Wire the documented supply-chain CI** — `docs/SUPPLY_CHAIN_SECURITY.md` describes OSV scan,
  SBOM, OpenSSF Scorecard, dependency-review, build-provenance, and action-SHA-pinning as the
  repo's controls, but none are wired (only `android-runtime.yml` + `release.yml` exist;
  `scripts/check-action-pinning.sh` isn't invoked by any workflow). Either add the workflows or
  trim the doc to match reality. _Medium._ Origin: Phase 5.8 audit.
- **Local-only mode follow-ups (Phase 5.7)** — (a) deep-link the confirm dialog straight to the
  on-device download card / show progress inline rather than relying on the existing
  card+banner; (b) auto-suggest local-only on first run for HIGH-tier devices; (c) re-evaluate
  "wipe keys on enable" as an opt-in strict variant (we chose keep-and-ignore). _Low._
  Origin: Phase 5.7.
- **Native LiteRT-LM tool calling instead of prompt-based FC** — 5.5b does on-device
  function calling via `GemmaToolPrompt` (structured prompt render+parse) for portability +
  unit-testability. LiteRT-LM also has a native tool API (`ToolProvider`/`ToolCall`/
  `automaticToolCalling=false`); evaluate switching once the on-device loop is validated on
  hardware and the native path proves more reliable than prompt parsing. _Low/Medium._ Origin: Phase 5.5b.
- **APK size: LiteRT-LM native runtime (~22 MB) + resource shrink (Phase 6)** — the release
  APK grew from ~8.6 MB to ~31 MB with the arm64 LiteRT-LM `.so` libs. Phase 6 size pass:
  consider an ABI split / Play feature-delivery (on-demand on-device module) so devices that
  never use on-device don't carry the runtime; turn on `isShrinkResources`. _Medium._ Origin: Phase 5.5b.
- **On-device download hardening (Phase 6 / 5.5b polish)** — the model download is a
  one-time user-initiated WorkManager job without a foreground-service type or its own
  progress notification; for a ~2.6 GB download on flaky networks, add a foreground
  `dataSync` service (Android 14 type) + resumable/range requests + a persistent
  notification. _Medium._ Origin: Phase 5.5a.
- **Tune the `DeviceTier` thresholds against the real on-device model** — 5.4's
  HIGH/MEDIUM/LOW cutoffs (RAM ≥8/≥4 GiB, ≥6 cores, SDK ≥31, 64-bit, low-RAM flag) are a
  coarse first cut; the precise "can run Gemma 4 E2B" cutoff (and any GPU/NPU/accelerator
  signal the runtime needs) should be measured and set in Phase 5.5 when the model lands.
  Nothing gates on the tier until then, so the current cutoffs only affect the About label.
  _Medium._ Origin: Phase 5.4.
- **`AgentBackend` as `Flow<BackendEvent>` instead of the blocking sink** — 5.1 chose
  a blocking `streamTurn(request, sink)` because the loop is a single sequential
  consumer on a dedicated worker thread that cancels by force-closing the in-flight
  stream; Flow's backpressure/operator-composition/multi-collector features would be
  unused and a `runBlocking` bridge would add subtler cancellation semantics for zero
  behavioral gain. Reconsider only if a *UI* surface wants to consume agent events
  reactively (a `callbackFlow` wrapper at the UI edge is likely enough, leaving the
  backend port as-is) or if a future provider's SDK is Flow-native. _Low._ Origin: Phase 5.1.

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
- **Type-scale tokens + motion-wrapper composables** — Phase 4.7a-3 shipped spacing tokens
  (`ui/theme/Spacing.kt`) + an `isReducedMotion()` helper, but left typography on
  `MaterialTheme.typography` defaults and didn't add reusable animated-container composables (e.g.
  a `CrossfadeScaffold`). Also: a handful of non-grid one-off `.dp` values (6/10/11/14/18) remain
  inline by design. Low value now; revisit if a custom type scale or repeated transitions warrant.
  _Low._ Origin: Phase 4.7a-3 (deferred).
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
