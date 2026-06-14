## [2026-06-14 13:03] | Task: Wire VERIFICATION.md into the CLAUDE.md memory harness

### 🤖 Execution Context
* **Agent ID**: `claude-code (session 013vN6M9RAYBQnDAybhdYE7T)`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote)`

### 📥 User Query
> During Phase 4.5 I initially forgot to batch the on-device verification steps
> into VERIFICATION.md. Update the CLAUDE.md workflow/harness so this is encoded
> and nothing slips next time.

### 🛠 Changes Overview
**Scope:** docs only (`CLAUDE.md`).

**Key Actions:**
- **Memory-harness step 6 ("Batch on-device checks")**: when a change adds or
  alters behavior that can only be confirmed on a real device/emulator, append its
  checks to `VERIFICATION.md` in the same change — numbered `Vn`, grouped by phase,
  each saying what to run and what "pass" looks like. Notes that `emulator-smoke`
  CI covers the scriptable subset and the ledger is the real-hardware source of
  truth until cleared into a history record.

### 🧠 Design Intent (Why)
The harness named histories, exec-plans, QUALITY_SCORE, and (recently) BACKLOG,
but not `VERIFICATION.md` — so appending on-device checks each phase was a
convention living only in the runtime plan / the ledger itself, and it got missed
in Phase 4.5 until flagged. Encoding it as a per-round harness step makes it a
mechanical, observable expectation rather than tribal knowledge.

### 📁 Files Modified
- `CLAUDE.md`
