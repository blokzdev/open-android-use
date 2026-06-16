## [2026-06-16 03:16] | Task: Phase 6.3a — transient-error retry/backoff + status badge

### 🤖 Execution Context
* **Agent ID**: `claude-code/session_013vN6M9RAYBQnDAybhdYE7T`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote execution environment)`

### 📥 User Query
> Continue Phase 6. Plan + implement 6.3a (transient-error retry/backoff + the live status badge
> deferred from 6.2), within the frozen 9-tool schema.

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` `agent` (loop + chat UI).

**Key Actions:**
- New pure **`RetryPolicy`**: `isTransient(Throwable)` scans the cause chain for transient markers
  (rate-limit / 429 / 5xx / timeout / connection / IOException / partial-stream) and excludes
  permanent ones (auth / 4xx), defaulting unknown → permanent; `backoffMs(attempt)` is deterministic
  exponential (1s→2s→4s, capped 8s). `RetryPolicyTest` covers classification (incl. nested causes,
  permanent-overrides-transient) + backoff.
- **`AgentController.streamTurnWithRetry`** wraps `backend.streamTurn`: retries a transient failure
  with backoff **only on a clean attempt** (a per-attempt guard sink tracks whether any delta was
  emitted) so partial output never double-prints; mid-stream and permanent errors propagate to the
  existing fail paths. Backoff waits are **interruptible** (wake on Stop). New `@Volatile
  transientStatus` + `Listener.onStatusChanged` (default no-op → emulator smoke unaffected); cleared
  on success, in the loop `finally`, and on reset/restore.
- **Chat UI**: `ChatActivity` observes `onStatusChanged`; `AgentViewCard` shows a tonal, a11y-live
  **status badge** ("Rate-limited, retrying… (2/3)") under the title. Two new strings.

### 🧠 Design Intent (Why)
A second-pair-of-hands shouldn't abort a task because Wi-Fi blipped or the API briefly rate-limited;
it should wait a beat, tell the user it's waiting, and resume. The clean-attempt guard is the crux:
deltas stream live into the transcript, so retrying after partial output would duplicate text —
guarding on "nothing emitted this attempt" makes retry safe while still covering the common case
(429/5xx/connection at request start). Classification lives in a pure, tested `RetryPolicy`; the
loop owns sleeping + the attempt budget. This also delivers the status badge deferred from 6.2 (now
that there's a live transient state to show).

### 📁 Files Modified
- New: `agent/RetryPolicy.kt`, `agent/RetryPolicyTest.kt`
- `agent/AgentController.kt` (retry wrapper, `transientStatus`, `Listener.onStatusChanged`, clears),
  `agent/ChatActivity.kt` (observe + badge), `res/values/strings.xml`
- Docs: exec-plan 6.3a ✓, `docs/QUALITY_SCORE.md`, `VERIFICATION.md`, this record.
