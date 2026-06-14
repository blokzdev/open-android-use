# Phase 4.5.1 — Hardening + 4.6 enablers

> Sub-plan of `docs/exec-plans/active/20260612-android-use-runtime.md` (Phase 4 UI/UX).
> English-first per CLAUDE.md.

## Goal

A short pre-4.6 pass that fixes the live regressions Phase 4.5 introduced, lays the Compose
UI test safety net the larger Phase 4.6 (i18n/a11y/responsive) will lean on, and auto-retires
more of the manual `VERIFICATION.md` ledger. Play-readiness work is split into a new Phase 4.7.

## Scope

- Included: 4.5 regression fixes; Compose UI test infrastructure + screen smokes; an
  instrumented Keystore test; a session-save churn guard; docs (Phase 4.7 split).
- Not included (→ 4.6/4.7): full string externalization, foldable/tablet layouts, richer
  markdown, `QUERY_ALL_PACKAGES` Play policy, signing/AAB. Hardware ledger + keystore are
  founder-gated.

## Changes

- **Material You re-theme of back-stack screens** — `ChatActivity`/`MainActivity` store the
  `dynamicColor` they were built with and `recreate()` in `onResume` if it changed, so a
  Settings toggle applies without an app restart. (Other screens open fresh.)
- **`ChatActivity` single instance** — `android:launchMode="singleTask"` + `onNewIntent`
  reading `EXTRA_SESSION_ID` → existing `resumeSession`; resuming from History routes into the
  one Chat instead of stacking duplicates.
- **Accessibility** — `contentDescription` for the mic button (`ChatActivity`), the History
  overflow `⋯` (`SessionsActivity`), and the in-control chip (`InControlOverlay`).
- **Naming** — `ChatScreen` `onShare` → `onExport`.
- **Session-save churn guard** — `AgentController.transcriptRevision` (bumped in `log()` /
  `restore()`); `ChatActivity` skips persisting when the revision is unchanged, so opening
  Settings/History no longer bumps `updatedAt` and reshuffles the list.
- **Compose UI test infra** — `androidx.compose.ui:ui-test-junit4` (androidTest) +
  `ui-test-manifest` (debug); `SettingsScreenTest` (renders controls), `SessionsScreenTest`
  (seeded row renders, overflow menu opens — `createEmptyComposeRule` + `ActivityScenario`),
  `AgentSettingsInstrumentedTest` (Keystore encrypt→decrypt→clear). All run in the existing
  `emulator-smoke` am-instrument pass — no script change.

## Critical files

- `agent/ChatActivity.kt`, `MainActivity.kt`, `agent/AgentController.kt`,
  `agent/SessionsActivity.kt`, `agent/InControlOverlay.kt`, `AndroidManifest.xml`,
  `app/build.gradle.kts`; new `app/src/androidTest/.../agent/{SettingsScreenTest,
  SessionsScreenTest,AgentSettingsInstrumentedTest}.kt`.

## Verification

- `gradle testDebugUnitTest --no-daemon` — JVM tests green.
- `gradle assembleDebugAndroidTest --no-daemon` — instrumentation APK (incl. Compose tests)
  builds. ✅ in-container.
- `make companion-build` — APK builds.
- CI `emulator-smoke` runs the new instrumented + Compose tests on API-30.
- Manual (founder): toggle Material You in Settings → Chat recolors on return; resume from
  History → no duplicate Chat in back stack; TalkBack announces mic/overflow; opening Settings
  from Chat and returning leaves History order unchanged.

## Progress

- [x] 4.5 regression fixes (Material You re-theme, singleTask+onNewIntent, a11y, naming).
- [x] Session-save churn guard.
- [x] Compose UI test infra + Settings/History smokes + Keystore instrumented test.
- [x] Docs: Phase 4.7 split in the roadmap; VERIFICATION CI note; QUALITY_SCORE; history.

## Decisions

- 2026-06-14: `ChatActivity` is `singleTask` (single workspace instance) so History resumes
  route in via `onNewIntent` rather than stacking duplicates.
- 2026-06-14: Compose UI tests are instrumented (need an emulator); they ride the existing
  `emulator-smoke` job, which also expands automated `VERIFICATION` coverage — rather than
  adding a JVM Robolectric stack.
- 2026-06-14: Play-readiness work moved to a dedicated **Phase 4.7**, keeping 4.6 a pure
  UX/i18n phase.
