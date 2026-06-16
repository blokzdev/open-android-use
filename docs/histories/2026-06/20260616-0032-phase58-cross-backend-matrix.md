## [2026-06-16 00:32] | Task: Phase 5.8 — cross-backend test matrix + EgressPolicy (Phase 5 close-out)

### 🤖 Execution Context
* **Agent ID**: `claude-code/session_013vN6M9RAYBQnDAybhdYE7T`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote execution environment)`

### 📥 User Query
> Proceed with 5.8 (split out of the 5.7 hardening): a cross-backend test matrix + the deferred
> EgressPolicy guard + supply-chain audit note, closing out Phase 5.

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` (`agent/llm` tests + a tiny `agent` refactor) + docs.

**Key Actions:**
- **`CloudBackendStreamingMatrixTest`** — `@Parameterized` over {Anthropic, Gemini}, driving each
  real backend against a dual-wire loopback SSE stub (payloads mirror the emulator
  `StubModelServer`). Asserts identical neutral semantics from the `AgentBackend` seam: tool-call
  turn → `TOOL_USE` + one parsed `get_app_state` `ToolUse`; text turn → `END_TURN` + reconstructed
  deltas. 4 cases (2 providers × 2 scenarios). **Fills the previously-missing Anthropic
  backend-streaming test.**
- **`EgressPolicy`** — extracted `AgentController.loopbackOrNull` into a pure `agent/llm` object
  (behavior-preserving; single call site) + `EgressPolicyTest` (loopback http passes; remote http /
  https / look-alike hosts rejected). The egress guard is now CI-pinned.
- **Supply-chain audit** — honest correction in `SUPPLY_CHAIN_SECURITY.md`: pinned+registered deps,
  layer boundary, SHA-pinned model, Keystore keys, 3-host egress + the new guard are real; the
  documented OSV/SBOM/Scorecard/dependency-review/provenance CI is **template-only, not wired**
  (only `android-runtime.yml` + `release.yml` exist). Logged in BACKLOG.
- **Phase 5 closed**: exec-plan marked complete and moved to `completed/`.

### 🧠 Design Intent (Why)
Phase 5 put three providers behind one seam but never proved they behave the same through it (and
Anthropic streaming had no unit test at all). A parameterized matrix gives cross-provider parity
cheaply and CI-only — Gemma stays out (native engine → emulator smoke + hardware ledger). Making
the loopback guard a pure helper turns a security invariant into a unit test. The audit follows
the "keep docs truthful" rule: rather than leave the template's aspirational CI claims standing, it
states what's actually wired and files the gap.

### 📁 Files Modified
- New: `agent/llm/EgressPolicy.kt`, `agent/llm/EgressPolicyTest.kt`,
  `agent/llm/CloudBackendStreamingMatrixTest.kt`
- `agent/AgentController.kt` (call-through)
- Docs: `docs/SUPPLY_CHAIN_SECURITY.md`, `docs/BACKLOG.md`, `docs/QUALITY_SCORE.md`,
  `VERIFICATION.md`, exec-plan (→ `completed/`), this record.
