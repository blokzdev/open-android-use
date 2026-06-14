# Phase 4.7 — UX Elevation (Companion)

> Sub-plan of `docs/exec-plans/active/20260612-android-use-runtime.md` (Phase 4 UI/UX).
> English-first. Play-readiness renumbered 4.7 → **4.8**.

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
- [ ] 4.7a-3 tokens/motion · [ ] 4.7b-2 chat (timestamps/grouping · streaming indicator · retry)
  · [ ] 4.7c History · [ ] 4.7d Home/onboarding · [ ] 4.7e Settings/Privacy.

## Decisions
- 2026-06-14: "UX elevation" inserted as Phase **4.7**; the prior Play-readiness work is now
  **4.8**. Delivered as small sequential sub-PRs holding the 4.6 quality bars.
- 2026-06-14: `material-icons-extended` over hand-authored vectors for breadth/velocity; its
  size is pruned by R8/resource-shrink in the 4.8 Play release build.
