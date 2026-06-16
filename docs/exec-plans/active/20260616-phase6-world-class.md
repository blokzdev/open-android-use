# Phase 6 — Toward a world-class app

## Goal

Before launch readiness, make the Android "second pair of hands" genuinely world-class: close loose
ends, land quick wins, add advanced features, elevate UX, and modernize — so on-device verification
(founder-led, next) and the launch phases (7/8) harden a polished, stable surface.

## Scope

- **Includes:** agent trust & transparency, reliability & perception (within the frozen 9-tool
  schema), safety depth, UX polish, and modernization/reach (adaptive UI, localization).
- **Excludes (moved to Phase 7/8 or on-device verification):** AAB/signing/resource-shrink, Play
  policy, supply-chain CI, profiling, foreground-service download hardening, DeviceTier tuning,
  native LiteRT tool calling, threat-model. **Excluded entirely (off-mission):** cloud sync /
  multi-device, arbitrary command execution from chat, Compose Multiplatform, any new agent tools.

## Background

- Curated from three Explore passes (chat/UX, agent capability, polish/modernization), 2026-06-16.
- Constraints (`CLAUDE.md`): the 9-tool Computer Use schema is **byte-frozen** (gains are
  execution-/perception-side, not new tools); ship as **small CI-green PRs** via the merge-then-plan
  loop.
- Code: `agent/` (AgentController, ToolExecutor, Snapshot/SnapshotFlattener, ChatActivity,
  SettingsActivity, ConfirmationSheet, GestureTrail), `agent/llm/`.

## Risks

- Scope creep → each sub-phase is its own approved PR; this plan fixes the order, not a mega-PR.
- Perception/reliability changes touch the loop → keep behind pure helpers + unit tests; the
  dual-provider emulator smoke is the regression guard.

## Milestones (sub-phases — ordered by leverage; each its own PR)

- **6.1 — Element-labeled actions + refusal clarity.** ✅ (this PR) `ActionSummary` resolves
  `element_index` → label; consent sheet / log / chips / transcript show "Tap 'Send'"; refusals
  show their reason.
- **6.2 — Richer live feedback:** ✅ gesture overlay on the Agent's-view (tap ripples + swipe/drag
  arrows via `GestureMark`) + a live action caption from 6.1's labels. (Status badge → 6.3; a full
  timeline stays folded into the chat's tool chips.)
- **6.3 — Reliability:** transient-error retry/backoff + status badge (6.3a ✅); snapshot diffing
  ("what changed") in action results + stuck/no-progress detection (6.3b ✅).
- **6.4 — Perception richness:** focus-change, scroll-state, modal awareness, adaptive tree budget
  in the a11y-tree text (no schema change). _Deferred after 6.3b_: focus already ships in the 6.3b
  action diff, and the remaining wins (scroll/modal hints) change the rendered tree-line **text**,
  which is kept byte-aligned across the Kotlin companion and the Go bridge flatteners — so each
  signal must be mirrored in both runtimes. Lower marginal value / higher cost than 6.5; see
  `docs/BACKLOG.md`.
- **6.5 — Safety depth:** sensitive-screen detection (password/payment → auto-decline),
  pause-on-touch re-snapshot + resume confirmation, per-app "always allow" consent.
  - **6.5a ✅** — credential-screen gate: `SensitiveScreenDetector` auto-declines the seven action
    tools on a screen with a password field (authoritative `isPassword`); reads stay open.
  - **6.5b ✅** — payment detection (tight label heuristic — `AccessibilityNodeInfo` has no autofill
    hints, so `SnapshotBuilder` keys on card-specific tokens) folded into the same gate, plus a
    **default-on Settings toggle** (`AgentSettings.sensitiveScreenGuard`) so the guard is
    user-controllable.
  - **6.5c** — the trust model for sensitive screens, reframed by research (2026-06-16) from a single
    "per-app always allow" toggle into a **layered, least-privilege trust model**. Cardinal rule:
    **secrets never enter the model loop** (handoff, never agent-typed — verified industry/academic
    consensus). Spec: `docs/design-docs/agent-security-trust-architecture.md`. Sub-PRs:
    - **6.5c-0** — Security & Trust Architecture spec (permanent reference) + doc alignment (this doc,
      SECURITY, BACKLOG, QUALITY_SCORE). Doc-only.
    - **6.5c-1** — L0 privacy redaction: secret field values → `[redacted]` at `SnapshotBuilder`
      emission (covers companion + Go bridge) + `ElementRecord` defense-in-depth; screenshot withheld
      on sensitive screens in vision mode (note explains why). Fixes a live card-digit leak.
    - **6.5c-2** — L1 human handoff/takeover (in-flow "🔒 you take it from here" overlay, auth-aware)
      + opportunistic login **tap-to-fill** (focus field → OS chip → user taps; agent never reads it).
    - **6.5c-3** — L2 scoped trust: default-deny per-app grants (once/session/persistent), revocable +
      decaying, Privacy "Trusted apps" list, text-only audit log. Granted app still hands off secrets.
    - **6.5c-4** — L3 injection hardening: untrusted-content isolation/spotlighting + a v1
      injection-signal classifier hook. CaMeL principles adopted; full CaMeL deferred (BACKLOG).
    - **6.5c-5** — L4 capstone: risk-adaptive confirmation (high-risk/injection-flagged actions confirm
      even under a grant) + a consolidated Trust & Safety surface, activity receipts, onboarding step.
- **6.6 — Composer & messages:** suggested follow-ups, `SelectionContainer` selection + copy
  feedback, model-picker bottom sheet (dedup `ModelDropdown`) with descriptions + recommended badge.
- **6.7 — Refinements:** externalize `ToolChipLabel` verbs, export timestamps, reactive theme
  switch (no `recreate()` flash), tier/key-aware smart onboarding.
- **6.8 — Adaptive UI:** two-pane Settings/Privacy + predictive-back (eval
  `NavigableListDetailPaneScaffold`), Material 3 typography tokens.
- **6.9 — Localization:** ship zh-CN translation on the externalized strings.

## Verification

- Per slice: `gradle :app:assembleDebug testDebugUnitTest assembleDebugAndroidTest
  assembleRelease` green; `gofmt`/`go vet` clean; new pure logic unit-tested; emulator smoke stays
  green.
- Hardware items appended to `VERIFICATION.md` per slice where behavior is device-observable.

## Progress

- [x] 6.1 — element-labeled actions + refusal clarity (`ActionSummary` + `ToolExecutor.describeAction`).
- [x] 6.2 — gesture overlay (`GestureMark`) + live action caption in the Agent's-view.
- [x] 6.3a — transient-error retry/backoff (`RetryPolicy`) + live status badge.
- [x] 6.3b — snapshot diffing (`SnapshotDiff`) in action results + stuck/no-op detection.
- [~] 6.4 — perception richness: **deferred** (focus shipped in 6.3b; scroll/modal hints carry a
  dual-runtime rendered-text cost — see BACKLOG). Reorder approved 2026-06-16.
- [x] 6.5a — credential-screen safety gate (`SensitiveScreenDetector` + `ToolExecutor` action gate).
- [x] 6.5b — payment detection (label heuristic) folded into the gate + default-on `sensitiveScreenGuard` toggle.
- [~] 6.5c — layered trust model for sensitive screens (spec:
  `docs/design-docs/agent-security-trust-architecture.md`). 6.5c-0 (spec + doc alignment) ✅; 6.5c-1
  redaction → 6.5c-2 handoff+tap-to-fill → 6.5c-3 scoped trust → 6.5c-4 injection hardening →
  6.5c-5 risk-adaptive confirm + transparency.
- [ ] 6.6–6.9 polish/reach.
