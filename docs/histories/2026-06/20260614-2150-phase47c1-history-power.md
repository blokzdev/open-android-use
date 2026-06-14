## [2026-06-14 21:50] | Task: Phase 4.7c-1 — History: pin + preview + date grouping

### 🤖 Execution Context
* **Agent ID**: `claude-code (session 013vN6M9RAYBQnDAybhdYE7T)`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote)`

### 📥 User Query
> Phase 4.7 UX elevation — "keep merging and rolling." 4.7c = History power features; this slice
> ships pinning, a last-message preview, and date grouping.

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` session persistence + History UI + pure logic; docs.

**Key Actions:**
- **Schema (v2, back-compat)**: `SessionMeta`/`SessionPayload` gain `pinned` + `preview`;
  `SessionCodec` encodes them and decodes v1 files with safe defaults (`opt*`).
- **Store**: `SessionStore.setPinned` edits metadata in place **without** bumping `updatedAt`
  (pinning isn't recency); `editMeta` gained a `bumpUpdatedAt` flag.
- **Agent core**: `AgentController` tracks `sessionPinned` (mirrors `sessionTitle`; new
  `notePinned`, reset on new conversation, restored from the payload) so a snapshot save from the
  loop can't clobber a user's pin; the snapshot derives `preview` via `SessionPreview`.
- **Pure logic (unit-tested)**: `SessionGrouping` (Pinned / Today / Yesterday / Earlier with
  injected `now`, pinned never duplicated into a date bucket) and `SessionPreview` (last
  assistant reply → else last prompt, whitespace-collapsed + truncated).
- **UI**: the shared `SessionsList` now renders grouped sections with headers, a one-line preview,
  a pin badge, and a Pin/Unpin overflow action — so **both** the full-screen History and the
  two-pane History pane get it. `onPin` threaded through `SessionsActivity` and `ChatActivity`.
- **Tests**: `SessionGroupingTest`, `SessionPreviewTest`, extended `SessionCodecTest` (round-trip
  + v1 back-compat), instrumented `SessionsScreenTest.pinMovesSessionToPinnedSection`.
- **Docs**: 4.7 sub-plan (4.7c-1 done; 4.7c-2 = search + archived filter + multi-select);
  formally deferred **4.7b-3 (per-message timestamps + role grouping)** to BACKLOG with rationale;
  `VERIFICATION.md` V95–V99; this history.

### 🧠 Design Intent (Why)
Pinning, a preview, and date grouping are the History upgrades users feel immediately, and they
sit cleanly on the existing text-only persistence. Keeping the bucketing/sort/preview as pure,
`now`-injected functions makes the value testable on the JVM and keeps the Compose layer thin.
Pinning deliberately doesn't touch `updatedAt` (a pin is organization, not activity), and the
pin state is mirrored in `AgentController` exactly like the title so an agent re-save preserves
it. Putting the grouping inside the shared `SessionsList` means the phone and tablet/foldable
surfaces can't drift. Per-message timestamps were deferred: the timestamp half is a wide
transcript/persistence schema change (touching `transcriptSnapshot`, the UI message type,
`SessionHistory`, `ConversationExport`, `SessionCodec`) for marginal value and is device-bound to
verify — recorded in BACKLOG to revisit on demand. Search / archived-filter / multi-select are
split to 4.7c-2 to keep this diff focused and green.

### 📁 Files Modified
- `agent/SessionModels.kt`, `agent/SessionCodec.kt`, `agent/SessionStore.kt`,
  `agent/AgentController.kt`, `agent/SessionsList.kt`, `agent/SessionsActivity.kt`,
  `agent/ChatActivity.kt`; new `agent/SessionGrouping.kt`, `agent/SessionPreview.kt`
- `res/values/strings.xml` (pin/unpin/pinned + section headers)
- tests: `SessionGroupingTest`, `SessionPreviewTest`, `SessionCodecTest`, `SessionsScreenTest`
- `docs/exec-plans/active/20260614-phase47-ux-elevation.md`, `docs/BACKLOG.md`,
  `VERIFICATION.md`, this history
