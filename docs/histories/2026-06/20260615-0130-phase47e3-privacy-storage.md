## [2026-06-15 01:30] | Task: Phase 4.7e-3 — Privacy: storage usage + Export all (closes 4.7e)

### 🤖 Execution Context
* **Agent ID**: `claude-code (session 013vN6M9RAYBQnDAybhdYE7T)`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote)`

### 📥 User Query
> Phase 4.7e Settings/Privacy depth — final slice: storage-usage summary + Export all.

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` Privacy screen + a SessionStore query; docs.

**Key Actions:**
- **Storage summary**: `SessionStore.usage()` returns `Usage(count, bytes)` from the session
  files; Privacy shows "N saved conversation(s) · <size>" via `Formatter.formatShortFileSize`.
- **Export all**: `exportAllConversations()` renders every saved session through the existing
  `ConversationExport.toMarkdown` (joined by `---`) into one file shared via FileProvider; empty →
  a Toast. New Storage section in `PrivacyScreen` with the summary + the button.
- **Docs**: 4.7 sub-plan (4.7e-3 done; **4.7e complete**; only 4.7a-3 tokens/motion remains in 4.7);
  `VERIFICATION.md` V118–V119; this history.

### 🧠 Design Intent (Why)
A storage line makes the "saved on this device" story concrete and gives a reason to tidy up;
Export-all is the natural companion to per-conversation export and a clean offboarding/backup path.
Both reuse what exists — `SessionStore` files and `ConversationExport` — so there's no new format or
data source, and the privacy invariant (text-only, screenshots never on disk) carries into the
export unchanged.

### 📁 Files Modified
- `agent/SessionStore.kt` (`usage()` + `Usage`), `PrivacyActivity.kt` (Storage section,
  `exportAllConversations`)
- `res/values/strings.xml` (storage title/summary + export-all strings)
- `docs/exec-plans/active/20260614-phase47-ux-elevation.md`, `VERIFICATION.md`, this history
