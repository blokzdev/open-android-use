## [2026-06-14 22:30] | Task: Phase 4.7b-3a — Timestamp plumbing (transcript createdAt)

### 🤖 Execution Context
* **Agent ID**: `claude-code (session 013vN6M9RAYBQnDAybhdYE7T)`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote)`

### 📥 User Query
> Founder: do the full per-message timestamps + role grouping (it makes the chat more intuitive
> and elegant); split into multiple PRs, take your time. This PR = the plumbing.

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` transcript model + persistence; no visible UI change.

**Key Actions:**
- **Model**: new `TranscriptEntry(kind, text, createdAt)` (Android/SDK-free, in `SessionModels`);
  `StoredMessage` gains `createdAt` (default 0 for legacy).
- **AgentController**: the in-memory transcript is now a list of `Line(kind, text, createdAt)`;
  `log` stamps a line's start time on creation (appends keep it); `transcriptSnapshot()` now
  returns `List<TranscriptEntry>`; `snapshotForPersistence` persists each line's time and `restore`
  replays it, so resumed sessions keep their original times.
- **SessionCodec → v3**: writes a per-message `t` (omitted when 0); decodes with `optLong("t",0)`
  so v1/v2 files load unchanged (back-compat).
- **Call sites updated** to the new shape: `ChatActivity.messages` (`List<TranscriptEntry>`),
  `MessageItem`/typing-cue/retry/export, `SessionPreview` (now consumes `TranscriptEntry`),
  `AgentLoopEmulatorTest`. `ConversationExport`/`SessionHistory.rebuild` stay on `(kind,text)`
  pairs via a `.map { it.kind to it.text }` at the call site (export timestamps land in 4.7b-3b).
- **Tests**: `SessionCodecTest` round-trips per-message timestamps + v1 back-compat;
  `SessionPreviewTest` rebuilt on `TranscriptEntry`.

### 🧠 Design Intent (Why)
Per-message timestamps need a real spot in the model, so this PR threads `createdAt` end-to-end
(live transcript → snapshot → JSON → resume) as a clean, behavior-preserving foundation, with the
visible rendering and role grouping deliberately split into 4.7b-3b to keep each diff reviewable.
Changing `transcriptSnapshot()` to a typed `TranscriptEntry` (rather than adding a parallel timed
method) keeps one source of truth and no drift; the two remaining pure consumers that don't need
time (`ConversationExport`, `SessionHistory`) take a trivial pair mapping at the call site. The
codec bump is additive and `opt*`-guarded, so existing on-device sessions keep working.

### 📁 Files Modified
- `agent/SessionModels.kt`, `agent/SessionCodec.kt`, `agent/AgentController.kt`,
  `agent/SessionPreview.kt`, `agent/ChatActivity.kt`
- tests: `SessionCodecTest`, `SessionPreviewTest`, `AgentLoopEmulatorTest`
- `docs/exec-plans/active/20260614-phase47-ux-elevation.md`, `docs/BACKLOG.md` (un-deferred),
  `VERIFICATION.md` (V100), this history
