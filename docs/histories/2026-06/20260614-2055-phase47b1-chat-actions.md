## [2026-06-14 20:55] | Task: Phase 4.7b-1 — Chat: per-message Copy/Share + jump-to-latest

### 🤖 Execution Context
* **Agent ID**: `claude-code (session 013vN6M9RAYBQnDAybhdYE7T)`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote)`

### 📥 User Query
> Phase 4.7 UX elevation — "keep merging and rolling." 4.7b = chat experience depth; this first
> slice ships per-message actions and scroll affordances (presentation-only).

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` chat surface (`agent/ChatActivity.kt`) + strings; docs.

**Key Actions:**
- **Per-message Copy / Share**: a new `MessageActions` wrapper puts a long-press context menu on
  user and assistant bubbles (haptic tick on open). Copy writes the message text to the clipboard
  and confirms via a Snackbar; Share opens the system chooser with the text (`shareMessage` →
  `ACTION_SEND text/plain`). `ChatScreen` gained a `SnackbarHost`, `onShareMessage`, and an
  `onCopyMessage` (clipboard + snackbar).
- **Jump-to-latest FAB**: a `SmallFloatingActionButton` shows only when the newest message is
  off-screen (`atBottom` derived from `LazyListState.layoutInfo`) and scrolls to it.
- **Smarter auto-scroll**: new turns auto-follow only when the user is already at the bottom, so
  reading history is no longer interrupted (the `LaunchedEffect(messages.size)` now gates on
  `atBottom`).
- **Trade-off**: the assistant bubble's `SelectionContainer` was removed (long-press can't drive
  both text selection and the menu); whole-message Copy covers the common need. Fine-grained
  selection is backlogged.
- **Docs**: 4.7 sub-plan progress (4.7b-1 done; 4.7b-2 = timestamps/grouping, streaming
  indicator, retry); `BACKLOG.md` partial-selection note; `VERIFICATION.md` V90–V92; this history.

### 🧠 Design Intent (Why)
Copy/Share per message and a non-intrusive scroll model are the highest-signal chat upgrades and
ship cleanly without touching the agent core or the transcript schema. Gating auto-scroll on
"already at bottom" fixes a real annoyance (being yanked down mid-read) and pairs naturally with
the jump-to-latest FAB. A plain `if (!atBottom)` (no `AnimatedVisibility`) keeps the FAB
reduce-motion-friendly and sidesteps the BoxScope/`AnimatedVisibility` composable-context quirk;
richer entrance motion belongs with the 4.7a-3 motion/tokens pass. Heavier chat work
(timestamps, role grouping, streaming indicator, retry) is split to 4.7b-2 so this stays a
focused, green, presentation-only diff.

### 📁 Files Modified
- `agent/ChatActivity.kt` (imports; `MessageActions`/`Bubble`/`AssistantBubble`/`MessageItem`;
  `ChatScreen` SnackbarHost + FAB + auto-scroll; `shareMessage`)
- `res/values/strings.xml` (chat_msg_copy/share/copied/actions/share_chooser, chat_scroll_to_latest)
- `docs/exec-plans/active/20260614-phase47-ux-elevation.md`, `docs/BACKLOG.md`,
  `VERIFICATION.md`, this history
