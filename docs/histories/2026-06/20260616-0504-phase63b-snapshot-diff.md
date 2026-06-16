## [2026-06-16 05:04] | Task: Phase 6.3b — snapshot diffing + stuck detection

### 🤖 Execution Context
* **Agent ID**: `claude-code/session_013vN6M9RAYBQnDAybhdYE7T`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote execution environment)`

### 📥 User Query
> Continue Phase 6. Plan + implement 6.3b: tell the model what changed after an action, and stop
> gracefully when the screen is stuck — within the frozen 9-tool schema.

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` `agent` (perception text + loop).

**Key Actions:**
- New pure **`SnapshotDiff.summarize(previous, current)`** → `Result(changed, summary)`: compares
  window/app, element-count delta, focus, and `treeLines` equality, emitting a one-liner ("Screen
  unchanged." / "Screen changed: window → 'Settings'; focus → …; +2 elements."). `SnapshotDiffTest`
  covers unchanged, add/remove, focus-only, window, tree-content-only, and null-previous.
- **`ToolExecutor`**: `Outcome` gains `changed: Boolean?`; `settleAndSnapshot` prepends the diff
  summary to the action result text and records `lastActionChanged` (reset per `callTool`, so reads
  leave it null). Diff is on the **action path only** — `get_app_state` (read) is untouched.
- **`AgentController`**: a `noProgressTurns` counter over action-turns (`executor.actionChanged`:
  true → reset, false → increment, null → leave); at `STUCK_THRESHOLD` (3) it stops gracefully with
  a resumable note instead of looping to `MAX_TOOL_TURNS`. Skipped on denied/interrupted batches.

### 🧠 Design Intent (Why)
The agent could previously repeat an action that visually did nothing (a11y tree not yet updated, a
disabled control) until the 60-turn cap, with the user watching it spin. Feeding the model an
explicit "what changed / unchanged" line lets it self-correct cheaply; the stuck backstop catches
the case where it doesn't. Kept conservative (action-turns only, threshold 3, after the 800ms
settle) so a slow UI doesn't trip it, and the stop is graceful + resumable, not a hard failure.
Pure `SnapshotDiff` for CI; the diff rides the existing tool-result `text` (not the cached system
prefix, not the read path) so prompt-cache and the get_app_state-only emulator smoke are unaffected.

### 📁 Files Modified
- New: `agent/SnapshotDiff.kt`, `agent/SnapshotDiffTest.kt`
- `agent/ToolExecutor.kt` (`Outcome.changed`, diff in `settleAndSnapshot`, `lastActionChanged`),
  `agent/AgentController.kt` (no-progress counter + graceful stop + `STUCK_THRESHOLD`),
  `res/values/strings.xml`
- Docs: exec-plan 6.3b ✓, `docs/QUALITY_SCORE.md`, `VERIFICATION.md`, this record.
