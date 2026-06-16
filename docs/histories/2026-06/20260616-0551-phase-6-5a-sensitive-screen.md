## [2026-06-16 05:51] | Task: Phase 6.5a — credential-screen safety gate

### 🤖 Execution Context
* **Agent ID**: `claude-code/session_013vN6M9RAYBQnDAybhdYE7T`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote execution environment)`

### 📥 User Query
> Continue Phase 6. After 6.3 (reliability) merged, do 6.5 safety: detect password/credential
> screens and auto-decline interaction so secrets are never acted on. Scope approved: 6.5a
> password-only (payment + toggle deferred to 6.5b).

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` `agent` (perception model + action gate).

**Key Actions:**
- New pure **`SensitiveScreenDetector.isSensitive(snapshot)`** → true iff any element is a password
  field; `null → false`. Carries a model-facing `REASON_PASSWORD` refusal string. Structured as the
  single chokepoint so 6.5b can add payment signals in one place.
- **`Snapshot.kt`**: `ElementRecord` gains `password: Boolean`; the flattener copies the existing
  `password` JSON flag (`SnapshotBuilder` already emits `node.isPassword`) through the walk. No
  change to the rendered tree-line text → no dual-runtime/byte-compat impact.
- **`ToolExecutor.callTool`**: a single central gate — for the seven action tools (set
  `ACTION_TOOLS`), if the app's last snapshot is sensitive, return a refusal `Outcome(isError=true,
  changed=null)` before dispatch/`perform()`. Reads (`list_apps`/`get_app_state`) are not gated; the
  gate is unconditional (independent of `confirmActions`). `callTool` converted from expression body
  to block body for the early return.
- **`SensitiveScreenDetectorTest`**: null → false, normal screen → false, password field → true,
  masked/empty-value password field → still true.

### 🧠 Design Intent (Why)
A "second pair of hands" on the user's real device must never type into or tap a password/credential
field — secrets are the human's to enter. The gate keys solely on the framework's authoritative
`AccessibilityNodeInfo.isPassword` (near-zero false positives); substring matching on labels/ids was
explicitly rejected because "card" matches `CardView`/"Discard" on most screens and would block
normal use. Reads stay open so the agent can still perceive the screen and ask the human to fill it,
then continue. Payment detection (autofill hints), a Privacy-settings toggle (default on), and
per-app "always allow" are deferred to 6.5b (BACKLOG). 6.4 (perception text) was deferred ahead of
this: focus already shipped in the 6.3b diff, and scroll/modal hints carry a dual-runtime
rendered-text cost — recorded in BACKLOG.

### 📁 Files Modified
- New: `agent/SensitiveScreenDetector.kt`, `agent/SensitiveScreenDetectorTest.kt`
- `agent/Snapshot.kt` (`ElementRecord.password` + flatten copy),
  `agent/ToolExecutor.kt` (`ACTION_TOOLS`, central gate, block-body `callTool`)
- Docs: exec-plan 6.5a ✓ / 6.4 deferred, `docs/BACKLOG.md` (6.5b + 6.4), `docs/QUALITY_SCORE.md`,
  `VERIFICATION.md` (V143), this record.
