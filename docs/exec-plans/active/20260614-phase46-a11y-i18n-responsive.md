# Phase 4.6 — Accessibility, i18n & responsive (Companion)

> Sub-plan of `docs/exec-plans/active/20260612-android-use-runtime.md` (Phase 4 UI/UX).
> English-first per CLAUDE.md.

## Goal

Make the Companion app exemplary on its own terms — accessible, localizable, and great on
tablets/foldables — which is also the Play quality bar. Surveys found ~280 hardcoded strings,
no `values-<locale>`, gaps in content-descriptions / 48dp targets / reduce-motion, no
`WindowSizeClass`/edge-to-edge/predictive-back, and a markdown renderer without links/tables.

Scope (with founder): i18n = **externalize only** (English, translation-ready; shipping
actual languages is a future **Localization** phase); responsive = **include tablet/foldable
two-pane**; markdown = **links + tables**. Play distribution (signing/AAB/policy) is Phase 4.7.

Delivered as sub-PRs; each carries tests + docs/history + `VERIFICATION.md` (harness step 6).
Guardrail: control surface stays dependency-free; 4.6 work is presentation-layer only.

## Sub-phases

- **4.6a — i18n.** Externalize every user-facing string to `res/values/strings.xml` (plurals
  + positional format args), convert non-Compose surfaces via `getString`. Enable lint
  `HardcodedText`/`MissingTranslation` as a guard once complete. Shipped in two PRs:
  - **4.6a-1** (this PR): static screens (`MainActivity`, `OnboardingActivity`,
    `PrivacyActivity`, `AboutActivity`, `agent/SettingsActivity`, `agent/SessionsActivity`)
    + the non-Compose surfaces (`agent/InControlOverlay`, `agent/AgentNotification`,
    `agent/ConfirmationSheet`).
  - **4.6a-2**: `agent/ChatActivity` (~40 strings) + agent-core transcript notes in
    `agent/AgentController` (via a `Context`/resolver) + `agent/ToolChipLabel`; then enable
    the lint guard.
- **4.6b — accessibility.** Remaining content-descriptions/semantics; 48dp targets; fix the
  classic-View hardcoded `sp` in overlays; reduce-motion gate (`agent/Motion.kt`); TalkBack pass.
- **4.6c — responsive polish.** `material3-window-size-class`; content max-width; responsive
  chat bubbles + adaptive Agent's-view; edge-to-edge; predictive back; `resizeableActivity`.
- **4.6d — richer markdown.** Links (`LinkAnnotation`) + `| pipe |` tables in `agent/ChatMarkdown.kt`
  (+ tests), rendered with horizontal scroll.
- **4.6e — tablet/foldable two-pane.** `material3.adaptive` + `androidx.window`; History ↔ Chat
  list-detail via `ListDetailPaneScaffold` (single-pane on compact).

## Verification
- `gradle testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`, `make companion-build`.
- `gradle lintDebug` (no `HardcodedText`/`MissingTranslation`) once 4.6a-2 lands.
- Manual (founder, phone + tablet/foldable AVD), appended to `VERIFICATION.md`: TalkBack,
  max font scale, RTL pseudo-locale, reduce-motion, links/tables, two-pane, edge-to-edge,
  predictive back.

## Progress
- [x] 4.6a-1: externalized the static screens + overlay/notification/confirmation surfaces;
  consolidated `strings.xml` (~120 entries: app/shared/main/settings/privacy/onboarding/
  sessions/overlay/notif/confirm). Builds + unit tests green in-container.
- [x] 4.6a-2: `ChatActivity` (~35 strings + suggested prompts) and the `AgentController`
  transcript notes (via `AgentSettings.appContext` + a `str()` resolver). `ToolChipLabel`
  verbs left as a documented exception (see Decisions). Builds + unit tests + APK green.
- [x] 4.6b accessibility: reduce-motion (`Motion` helper; gesture-trail gated, chat
  auto-scroll instant), 48dp touch targets (mic/overflow + classic-View buttons), heading
  semantics on section/step titles, polite live-region for agent running/idle, error tool-chip
  descriptions, decorative tap-marker cleared. `MotionTest` + Compose a11y assertions
  (heading + 48dp). Builds + unit tests + instrumentation green.
- [x] 4.6c responsive: edge-to-edge on all activities; predictive-back +
  `resizeableActivity` in the manifest; `ui/Responsive.kt` `ResponsiveContent`
  (centered, `ContentMaxWidth=640.dp`) wrapping Main/Settings/Privacy/About; chat
  bubble width caps. (`WindowSizeClass` dep deferred to 4.6e where the two-pane
  choice needs it; Onboarding/chat-list width + adaptive Agent's-view height are
  minor follow-ups.) Builds + unit tests + APK green.
- [x] 4.6d markdown: links `[text](url)` (Compose `LinkAnnotation`/`withLink`, brand-colored
  underline) + `| pipe |` tables (header + `---` separator + rows) in `agent/ChatMarkdown.kt`,
  rendered with horizontal scroll. Parser unit-tested (links, tables, fallbacks, link-in-cell).
  Builds + unit tests + APK green.
- [x] 4.6e tablet/foldable two-pane: `material3-window-size-class`; extracted
  `agent/SessionsList.kt` (shared by `SessionsActivity` + the pane); `agent/TwoPane.kt`
  (Expanded-only) + `TwoPaneTest`; `ChatActivity` shows a `HistoryPane` beside `ChatScreen`
  at Expanded width (reusing `resumeSession`), single-pane otherwise. Out-of-scope items
  (hinge-awareness, full `NavigableListDetailPaneScaffold`, Settings/Privacy two-pane, 4.6c
  responsive follow-ups) recorded in `docs/BACKLOG.md`. Builds + unit tests + APK +
  instrumentation green.

**Phase 4.6 complete** (a–e).

## Decisions
- 2026-06-14: i18n ships as **externalize-only** (English, translation-ready). Actual
  translations (zh-CN + others) are deferred to a dedicated future **Localization** phase
  recorded in the roadmap, so the framework lands now without a translation-quality commitment.
- 2026-06-14: 4.6a split into two PRs — static screens/overlays first, then the larger/
  fluider `ChatActivity` and the agent-core `AgentController` notes (which take a `Context`
  via `AgentSettings.appContext`), keeping each diff reviewable and the tree green.
- 2026-06-14: `ToolChipLabel` (the tool-chip verbs like "Tap"/"Read the screen") stays
  English for now — it's a pure JVM-unit-tested helper with no `Context`, so localizing it
  would force its test onto an Android runtime. Folded into the future Localization phase.
  The `▸`/`✗` transcript tokens and the diagnostic error-chain in `AgentController.fail`
  also stay (they are structured/smoke-pinned, not prose).
- 2026-06-14: No lint `HardcodedText` guard — that detector targets XML `android:text`, which
  this Compose-only app doesn't use; a custom/detekt rule for `Text("literal")` is the real
  future guard. Completeness is enforced by review for now.
