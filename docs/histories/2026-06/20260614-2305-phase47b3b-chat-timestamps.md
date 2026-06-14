## [2026-06-14 23:05] | Task: Phase 4.7b-3b — Chat: timestamps + role grouping

### 🤖 Execution Context
* **Agent ID**: `claude-code (session 013vN6M9RAYBQnDAybhdYE7T)`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote)`

### 📥 User Query
> Founder: do the full per-message timestamps + role grouping for a more intuitive, elegant chat.
> This PR = the visible UI, on the 4.7b-3a plumbing.

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` chat rendering (`agent/ChatActivity.kt`); docs.

**Key Actions:**
- **Role grouping**: the message `LazyColumn` now spaces tightly within a run of same-role turns
  and adds extra space when the role changes (`roleChanged` from the previous entry's kind), so a
  task's agent/tool/thinking steps read as one chunk.
- **Time separators**: a centered, relative day/time marker (`TimeSeparator`,
  `DateUtils.getRelativeDateTimeString`) is inserted above a turn that starts ≥5 min after the
  previous (`TIME_SEPARATOR_GAP_MS`); skipped for legacy `createdAt==0` lines.
- **Per-turn time caption**: a subtle locale-aware time-of-day (`BubbleTime`,
  `DateUtils.formatDateTime(FORMAT_SHOW_TIME)`) under the **last** bubble of each user/assistant
  run (`lastOfRun`), right-aligned for the user, left for the agent; none when no stored time.
- **Docs**: 4.7 sub-plan (4.7b-3b done); BACKLOG (export-time follow-up); `VERIFICATION.md`
  V101–V103; this history.

### 🧠 Design Intent (Why)
Showing a time on every message is noisy; the elegant, conventional pattern is to *group* turns
and surface "when" sparingly — a separator only when there's a real gap, plus one quiet caption
per turn. Grouping by role change turns the previously uniform stack into readable conversational
chunks. All time formatting goes through `DateUtils` so it follows the device's locale and 12/24h
setting. Legacy sessions (saved before timestamps existed) carry `createdAt==0` and simply render
no separator/caption — no epoch artifacts. No new custom animation, so the reduce-motion bar is
untouched. Export-time was left to a backlog one-liner to keep this a focused chat-UI diff.

### 📁 Files Modified
- `agent/ChatActivity.kt` (message-list grouping/separator logic; `TimeSeparator`, `BubbleTime`;
  `MessageItem`/`Bubble`/`AssistantBubble` gain `timestamp`; `TIME_SEPARATOR_GAP_MS`; DateUtils import)
- `docs/exec-plans/active/20260614-phase47-ux-elevation.md`, `docs/BACKLOG.md`,
  `VERIFICATION.md`, this history
