## [2026-06-14 10:46] | Task: Add docs/BACKLOG.md and wire it into the memory harness

### 🤖 Execution Context
* **Agent ID**: `claude-code`
* **Base Model**: `claude-opus-4-8`
* **Runtime**: Claude Code on the web

### 📥 User Query
> The no-companion bridge-snapshot idea was flagged out of scope — should we add
> it to a BACKLOG.md and update the CLAUDE.md workflow to encompass this backlog?

### 🛠 Changes Overview
**Scope:** docs only.

**Key Actions:**
- **New `docs/BACKLOG.md`**: a curated someday/maybe list, distinct from the
  scheduled exec-plan work. Conventions: not the roadmap; promote into an exec-plan
  when scheduled; record deferrals with rationale + rough priority. Seeded with the
  genuine deferrals to date — robust no-companion bridge snapshot (low /
  investigate-if-needed, with the three-avenues note), `uiautomator dump
  --compressed` fallback (low), structured tool chips with element labels (medium).
- **`CLAUDE.md`**: added memory-harness step 5 ("Defer with discipline") pointing
  to `docs/BACKLOG.md`, with the exec-plan-vs-backlog distinction.
- **`AGENTS.md`**: added `docs/BACKLOG.md` to the navigation layer.
- **Roadmap** (`docs/exec-plans/active/20260612-android-use-runtime.md`): a dated
  Decisions note that unscheduled ideas live in BACKLOG.md (no duplication of the
  scheduled phases).

### 🧠 Design Intent (Why)
Deferred ideas were being parked inline in the roadmap, blurring with scheduled
phases and risking loss. A dedicated, harness-wired BACKLOG.md gives future
out-of-scope cuts a durable home by convention, while keeping the exec-plan focused
on what's actually scheduled.

### 📁 Files Modified
- `docs/BACKLOG.md` (new), `CLAUDE.md`, `AGENTS.md`,
  `docs/exec-plans/active/20260612-android-use-runtime.md`
