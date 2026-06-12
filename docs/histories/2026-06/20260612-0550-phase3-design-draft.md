## [2026-06-12 05:50] | Task: Phase 3 design draft — the agent loop

### 🤖 Execution Context
* **Agent ID**: Claude Code (cloud session, continuation of the Android pivot)
* **Base Model**: claude-fable-5
* **Runtime**: Claude Code remote execution container

### 📥 User Query
> Loop on (continue autonomously).

### 🛠 Changes Overview
**Scope:** docs only.

**Key Actions:**
- **[Design draft]**: `docs/design-docs/phase3-agent-loop.md` — fixes the
  Phase 3 direction: host-loop first (3.0, experience polish on the existing
  bridge+companion+skill), then the on-device loop (3.1: chat/voice UI +
  Anthropic client + in-process tools reusing the protocol-v1 surface).
  Interaction model: narrate-then-act, touch-to-pause, consent ladder,
  visible hands, physical kill switch. Non-goals: background autonomy,
  telemetry, credential handling.

### 🧠 Design Intent (Why)
Phase 3 decisions (where the loop runs, how interruption/consent work) shape
everything 3.x ships; writing them down now lets the next session start from
an execution plan instead of re-deriving direction. Host-first is the cheap
validation path; the on-device loop reuses the companion's existing tool
surface in-process, so Phase 2 work carries forward intact.

### 📁 Files Modified
- `docs/design-docs/phase3-agent-loop.md`
