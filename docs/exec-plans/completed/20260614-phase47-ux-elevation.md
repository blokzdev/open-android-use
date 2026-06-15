# Phase 4.7 — UX Elevation (Companion)

> Sub-plan of `docs/exec-plans/active/20260612-android-use-runtime.md` (Phase 4 UI/UX).
> English-first. **Status: COMPLETE (2026-06-15)** — 4.7a→4.7e + 4.7a-3 all merged (PRs #16–#32).
> Play-readiness/distribution moved to the dedicated final **Phase 6 — Launch readiness & hardening**
> (after Phase 5); see the master roadmap.

## Goal

A design-led elevation sweep across every Companion surface — more polished, delightful,
feature-rich, and intuitive — while holding the 4.6 bars (i18n, accessibility, reduce-motion,
tests, dependency-free control surface). Founder selected all themes + opportunistic delights.

## Principles (every sub-PR)
- New copy → `res/values/strings.xml`; icon-only controls get `contentDescription`; keep
  headings/48dp; honor reduce-motion (`agent/Motion.kt`); pure logic extracted + unit-tested;
  no androidx/`com.anthropic` under the control surface.

## Sub-PRs
- **4.7a — design-system foundation**: real Material icons (`material-icons-extended`) replacing
  emoji (mic, overflow, top-bar actions); Android-12 splash (`core-splashscreen`, brand indigo +
  mono mark). _(Snackbar+Undo and motion/tokens split into 4.7a-2 to keep PRs green.)_
- **4.7a-2 — Snackbar + Undo + tokens/motion**: shared `SnackbarHost`; replace the 11 `Toast`s;
  Undo for delete / delete-all / clear-conversation / clear-key; `ui/Tokens.kt`; reduce-motion-
  aware transitions.
- **4.7b — chat depth**: per-message copy/share; timestamps + role grouping; scroll-to-bottom +
  new-message cue; streaming indicator; richer Agent's-view (current action + progress); retry.
- **4.7c — History power**: search, date grouping, archived filter, last-message preview,
  pin/star, multi-select bulk actions, undo-delete (model: `pinned`/`preview` in
  `SessionModels`/`SessionCodec`/`SessionStore`).
- **4.7d — Home + onboarding**: a dashboard Home (hero + readiness + recents + suggestions);
  onboarding stepper/illustrations + first-task demo.
- **4.7e — Settings/Privacy depth**: API-key show/hide + Test-key + "Get a key"; Light/Dark/System
  theme mode; storage-usage summary; per-conversation + all export; (optional) model-picker sheet.

## Dependencies
`androidx.compose.material:material-icons-extended`, `androidx.core:core-splashscreen:1.0.1`
(presentation only; enable R8 + resource-shrink in 4.8 to prune icons).

## Verification
- `gradle testDebugUnitTest` + `assembleDebug` + `assembleDebugAndroidTest`; CI `emulator-smoke`.
- Manual (founder), `VERIFICATION.md`: splash shows; icons read under TalkBack; (per sub-PR)
  Undo restores; search/group/pin/multi-select; theme mode; per-message copy/share; reduce-motion
  still disables new motion.

## Progress
- [x] 4.7a — icons (mic/overflow/top-bar) + Android-12 splash. Builds + tests + APK +
  instrumentation green; `SessionsScreenTest` still valid with the IconButton overflow.
- [x] 4.7a-2 — **Snackbar + Undo for destructive actions**: shared `ui/showUndo` helper;
  Privacy clear-key / clear-conversation / delete-all and History single-delete (both the
  full-screen `SessionsActivity` and the two-pane `HistoryPane`, via a shared
  `rememberDeleteWithUndo`) now confirm → act → Snackbar-with-Undo, capturing pre-state (the
  key / live conversation / removed `SessionPayload`s) and restoring on Undo. New
  `SessionsScreenTest.deleteThenUndoRestoresSession`. _(Remaining Toasts are non-destructive
  "busy" notices; spacing/type tokens + reduce-motion transition wrappers deferred to 4.7a-3
  so this PR stays a focused, reviewable Undo change.)_
- [x] 4.7b-1 — **chat: per-message Copy/Share + jump-to-latest**: long-press a user/assistant
  bubble → context menu (Copy to clipboard with a confirming Snackbar / Share via the system
  chooser); a `SmallFloatingActionButton` appears only when the newest message is off-screen and
  jumps to it; new turns no longer auto-scroll while the user reads history (auto-scroll gated on
  "already at bottom"). Builds + tests + APK + instrumentation green. _(In-bubble partial text
  selection was traded for the long-press menu → backlogged.)_
- [x] 4.7b-2 — **chat: typing cue + error→Retry**: a pulsing-dots `TypingIndicator` shows where
  the next answer will land while the agent composes (static dots under reduce-motion; labelled
  for TalkBack, no second live region); error notes show a **Retry** action when the agent is idle
  and the note is the last message — it re-runs the most recent user task (respecting readiness).
  Builds + tests + APK + instrumentation green.
- [x] 4.7c-1 — **History: pin + preview + date grouping**: `SessionMeta`/`SessionPayload` +
  `SessionCodec` (v2, back-compat) gain `pinned` + `preview`; `SessionStore.setPinned` (no
  updatedAt bump); `AgentController` tracks `sessionPinned` (mirrors title; `notePinned`) and
  derives `preview` in the snapshot. New pure, unit-tested `SessionGrouping` (Pinned / Today /
  Yesterday / Earlier) + `SessionPreview`. Shared `SessionsList` now renders grouped sections,
  a last-message preview, a pin badge, and a Pin/Unpin action (both phone + two-pane). JVM tests
  (`SessionGroupingTest`, `SessionPreviewTest`, extended `SessionCodecTest`) + instrumented
  `pinMovesSessionToPinnedSection`. Builds + tests + APK + instrumentation green.
- [x] 4.7b-3a — **timestamp plumbing** (no visible change): a per-message `createdAt` now flows
  through the transcript model — `AgentController` transcript lines (`Line`) + `transcriptSnapshot`
  (→ new `TranscriptEntry`), `StoredMessage`, and `SessionCodec` (v3, back-compat `t`). All call
  sites (`ChatActivity` `messages`, `SessionPreview`, export, emulator test) updated; resume
  preserves saved times. JVM/instrumented tests green. Foundation for 4.7b-3b.
- [x] 4.7b-3b — **chat: timestamps + role grouping**: consecutive same-role turns are visually
  grouped (tight spacing within a run, extra space on role change); a centered relative day/time
  separator appears when a turn is ≥5 min after the previous; a subtle locale-aware time caption
  sits under the last bubble of each user/assistant run (legacy `createdAt==0` lines show none).
  Builds + tests + APK + instrumentation green. _(Export-time in the Markdown → backlog.)_
- [x] 4.7c-2a — **History: search + archived filter**: a search field filters by title/preview
  (pure, unit-tested `SessionSearch`); archived conversations are hidden by default behind a
  "Show archived" chip (shown only when some exist); a "No matching conversations" empty state.
  Lives in the shared `SessionsList`, so phone + two-pane both get it. JVM `SessionSearchTest` +
  instrumented `searchFiltersByTitle`. Builds + tests + APK + instrumentation green.
- [x] 4.7d-1 — **Home dashboard**: `MainActivity` is now a dashboard — a brand hero (mark +
  name + tagline), a readiness card with a status icon and one **context-aware primary CTA**
  (Ready → Open chat; otherwise → Finish setup, routing to the first missing prerequisite), a
  recent-conversations section (tap to resume, See all → History), suggestion chips that open chat
  prefilled (new `ChatActivity.EXTRA_PROMPT`), and a cleaner Settings / History / About nav plus
  the accessibility/kill-switch footer. Builds + tests + APK + instrumentation green.
- [x] 4.7d-2 — **onboarding glow-up**: a persistent progress-dots stepper (current/total announced),
  a large per-step icon (waving hand / accessibility / lock / key / tune / rocket), success-state
  rows with check/neutral icons (accessibility + ready), a reduce-motion-aware step transition
  (Crossfade only when animations are on), and a one-tap first-task chip on the Ready step that
  opens chat prefilled (`EXTRA_PROMPT`). Builds + tests + APK + instrumentation green.
- [x] 4.7e-1 — **Settings: API-key depth**: show/hide toggle on the key field; a **Test key**
  action that validates via the Models API (background, spinner, Snackbar result) reusing a new
  `ModelCatalog.validateKey`; a "Get an API key" link to the Anthropic console. Builds + tests +
  APK + instrumentation green.
- [x] 4.7e-2 — **Settings: theme mode (Light/Dark/System)**: new `ThemeMode` + `AgentSettings.themeMode`;
  `OpenAndroidUseTheme` honors it (derives dark from the mode) alongside Material You; a segmented
  selector in Settings; all surfaces pass it; Home/Chat recreate-on-resume when it changes. Builds +
  tests + APK + instrumentation green.
- [x] 4.7e-3 — **Privacy: storage usage + Export all**: a Storage section shows saved-conversation
  count + bytes (`SessionStore.usage()` + `Formatter.formatShortFileSize`); "Export all
  conversations" writes every saved session as one Markdown file shared via FileProvider (reusing
  `ConversationExport`). Closes **4.7e**. Builds + tests + APK + instrumentation green.
- [x] 4.7a-3 — **spacing tokens + reduce-motion completeness**: new `ui/theme/Spacing.kt` (4dp-grid
  tokens) adopted across the structural spacing of all 7 Compose surfaces; `ui/MotionUi.kt`
  `isReducedMotion()` helper standardizes the reduce-motion check and the previously-unguarded chat
  jump-to-latest FAB now scrolls instantly when animations are off. Custom Type tokens + motion-
  wrapper composables deferred to BACKLOG. **Closes Phase 4.7.**
- _Deferred:_ 4.7c-2b multi-select bulk archive/delete → BACKLOG (stateful selection mode is the
  lowest-value / hardest-to-tune-blind item in 4.7c; revisit if requested).
- _Founder call (2026-06-14):_ do the full per-message timestamps + role grouping (un-deferred
  from BACKLOG) for a more intuitive, elegant chat — split into plumbing (4.7b-3a) + UI (4.7b-3b).

## Decisions
- 2026-06-14: "UX elevation" inserted as Phase **4.7**; the prior Play-readiness work is now
  **4.8**. Delivered as small sequential sub-PRs holding the 4.6 quality bars.
- 2026-06-14: `material-icons-extended` over hand-authored vectors for breadth/velocity; its
  size is pruned by R8/resource-shrink in the 4.8 Play release build.
