# Phase 4.5 — Settings, Privacy & Multi-Session Conversations

> Sub-plan of `docs/exec-plans/active/20260612-android-use-runtime.md` (Phase 4 UI/UX).
> English-first per CLAUDE.md.

## Goal

Promote the cramped in-chat settings dialog into a real **Settings** screen and a
browsable **Privacy & data** screen, and replace the originally-scoped "ephemeral
recent prompts" with **persistent, resumable multi-session conversations** — a named
History list (auto-named from the first prompt) that the user can revisit, **resume with
the conversation's context**, rename, archive, and delete. Add a Material You toggle,
honest data controls, and Markdown export of a whole conversation.

## Scope

- Included:
  - `SettingsActivity` (key + Clear, model, confirm/voice toggles, Material You, links to
    Privacy/About, re-run setup); `PrivacyActivity` (trust story + data controls);
    `SessionsActivity` (History list: resume/rename/archive/delete).
  - Session persistence: `SessionStore` (filesDir JSON, one file per session),
    `SessionCodec` (pure org.json), `SessionModels`, `SessionTitle`, `SessionHistory`
    (rebuild model history from transcript on resume).
  - `AgentController` additions: session id/title tracking, `snapshotForPersistence`,
    `restore`, `newConversation`.
  - Material You wiring through `OpenAndroidUseTheme(dynamicColor=…)` at every surface.
  - Conversation export to Markdown (`ConversationExport`) shared via FileProvider.
- Not included:
  - Replaying raw tool_use/tool_result/screenshot history into the model on resume (see
    Decisions); opt-in long-term "task memory"; cloud sync; per-message editing.

## Background

- Design: `docs/design-docs/phase4-product-ui.md` §5 (Settings & privacy/transparency).
- Key code: `apps/OpenAndroidUseCompanion/app/src/main/kotlin/dev/openandroiduse/companion/`
  — `agent/AgentController.kt` (in-memory `history`/`transcript`, screenshot pruning),
  `agent/ChatActivity.kt` (former `SettingsDialog`), `agent/AgentSettings.kt`
  (SharedPreferences + Keystore), `OnboardingActivity.kt` (`PrivacyStep`), `ui/theme/`.
- Constraint: the control surface (`CompanionService`/`HttpServer`/snapshot/action) stays
  dependency-free; new code lives in the `agent` package / Activities.

## Risks

- Risk: serializing the Anthropic `MessageParam` history is brittle — the SDK's Jackson
  mapper is Kotlin-`internal` and its modules aren't on the compile classpath.
  Mitigation: do **not** serialize SDK types; persist the transcript and rebuild a valid
  alternating history from it on resume (`SessionHistory.rebuild`). Pure, dependency-free,
  SDK-version-stable, and consistent with "screenshots never touch disk".
- Risk: a resumed history ending in an unanswered user turn would make the next prompt two
  consecutive user messages. Mitigation: `rebuild` drops a trailing user turn; unit-tested.
- Risk: persisting conversations changes the data story. Mitigation: text-only on disk,
  screenshots never written; honest copy in Privacy screen + `SECURITY.md`; delete controls.

## Milestones

1. Settings & Privacy home, Material You, export. ✅
2. Session persistence + transcript→history rebuild + unit tests. ✅
3. History UI + resume wiring + instrumented SessionStore test. ✅
4. Docs sync, QUALITY_SCORE, history record; build/test green; push + PR.

## Verification

- `cd apps/OpenAndroidUseCompanion && gradle testDebugUnitTest --no-daemon` (JVM: codec,
  history rebuild, title, export).
- `make companion-build` (APK) and `gradle assembleDebugAndroidTest` (instrumentation incl.
  `SessionStoreInstrumentedTest`); `make android-smoke` runs it on the emulator.
- Manual (device): open Settings from chat; toggle Material You; add/clear key; run a task
  → it appears in History auto-named; resume it → the agent continues with context;
  rename/archive/delete; export → shareable `.md`; "Delete all conversations"; re-run setup.

## Progress

- [x] 4.5a settings/privacy screens, Material You toggle, Markdown export (FileProvider).
- [x] 4.5b SessionStore/Codec/Models/Title/History + AgentController snapshot/restore.
- [x] 4.5c History UI, chat resume + auto-save, recent-sessions empty state, MainActivity
  entry points, instrumented SessionStore test.
- [x] Docs synced; unit tests + APK + instrumentation build green in-container.

## Decisions

- 2026-06-14: Recent prompts are **superseded by full sessions** — a resumable, named
  History list is the world-class "quick re-run". The chat empty state surfaces recent
  sessions for one-tap resume.
- 2026-06-14: **Resume rebuilds the model history from the saved transcript**
  (`SessionHistory.rebuild`) rather than serializing the SDK's `MessageParam` blocks. The
  SDK's configured Jackson mapper is `internal` and replicating it couples us to SDK
  internals and would add dependencies; rebuilding from text is dependency-free,
  version-stable, and matches the privacy invariant (screenshots never persist). The agent
  resumes the dialogue's context and re-observes the device live; raw tool/screenshot
  blocks are intentionally not replayed.
- 2026-06-14: Conversations persist **text-only** on the device (filesDir JSON, atomic
  writes), deletable per-session or all at once; Settings/Privacy split with About kept
  separate for the licenses screen Play expects; export is a Markdown file shared via
  FileProvider (cache-path, per-share read grant); Material You is an opt-in toggle
  (Android 12+) defaulting to the brand palette.
