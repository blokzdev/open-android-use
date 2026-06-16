## [2026-06-16 06:26] | Task: Phase 6.5b — payment detection + Privacy toggle

### 🤖 Execution Context
* **Agent ID**: `claude-code/session_013vN6M9RAYBQnDAybhdYE7T`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote execution environment)`

### 📥 User Query
> Continue Phase 6. Plan + implement 6.5b: extend the safety gate to payment screens and make the
> guard user-controllable. Approved scope: payment + toggle (per-app allow → 6.5c).

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` (`SnapshotBuilder`, `agent`, settings UI).

**Key Actions:**
- **Payment signal** — `SnapshotBuilder` emits a `creditCard` flag via a **tight label heuristic**:
  `AccessibilityNodeInfo` exposes no autofill hints (verified against `android.jar` — only
  `getHintText`/`getInputType`), so the pure, unit-tested `looksLikePaymentLabel` matches the node's
  hint/text/contentDesc/resourceId against card-specific tokens only (`cvv|cvc|csc`, "card number",
  "credit/debit card", "security code", "card verification"), with camelCase splitting and
  non-alphanumeric normalization. Bare "card"/"expiry" excluded → no `CardView`/"Discard" false hits.
- **Carry-through** — `ElementRecord` gains `creditCard`; `SnapshotFlattener.walk` copies it. No
  rendered-text change (no dual-runtime impact; the Go bridge ignores the unknown JSON key).
- **Detector** — `SensitiveScreenDetector.isSensitive` now ORs `password || creditCard`;
  `REASON_PASSWORD` → `REASON_SENSITIVE` ("password or payment field").
- **User toggle** — `AgentSettings.sensitiveScreenGuard` (default **true**); a `SettingToggle` row in
  Settings + strings. Threaded as a plain `Boolean` from `AgentController` (read beside
  `confirmActions`) through `runLoop` into `ToolExecutor(service, captureScreenshots, guard)`; the
  gate becomes `if (sensitiveScreenGuard && name in ACTION_TOOLS && isSensitive(...))`.
- **Tests** — `PaymentLabelHeuristicTest` (matches real card labels / resource-ids / camelCase;
  rejects Discard/CardView/Dashboard/Add-to-cart/empty); `SensitiveScreenDetectorTest` +creditCard
  cases; `SnapshotFlattenerTest` sensitive-flag pass-through.

### 🧠 Design Intent (Why)
Closes the payment half of the safety track and puts the human in control of the guard. Discovered
during implementation that the planned authoritative autofill-hint signal isn't reachable from an
accessibility service, so (with the user's explicit choice) the payment signal is a **conservative
label heuristic** — scoped to tokens that essentially never appear off a payment form, keeping the
near-zero-false-positive property that motivated rejecting substring matching in 6.5a. The guard
defaults on (safety first; disabling is a conscious opt-out, unlike `confirmActions` which defaults
off) and fails safe (the injected boolean defaults true). `ToolExecutor` stays `AgentSettings`-free
(takes a `Boolean`) so the gate remains unit-testable; the heuristic's logic lives in a pure
`internal` function so it is tested without Android types.

### 📁 Files Modified
- `SnapshotBuilder.kt` (`creditCard` emit + pure `looksLikePaymentLabel`),
  `agent/Snapshot.kt` (`ElementRecord.creditCard` + flatten copy),
  `agent/SensitiveScreenDetector.kt` (`creditCard` + `REASON_SENSITIVE`),
  `agent/AgentSettings.kt` (`sensitiveScreenGuard` pref),
  `agent/SettingsActivity.kt` (toggle row), `agent/AgentController.kt` (thread the flag),
  `agent/ToolExecutor.kt` (`sensitiveScreenGuard` param + gate), `res/values/strings.xml`
- New tests: `PaymentLabelHeuristicTest.kt`; updated `SensitiveScreenDetectorTest.kt`,
  `SnapshotFlattenerTest.kt`
- Docs: exec-plan 6.5b ✓ / 6.5c queued, `docs/BACKLOG.md` (6.5c), `docs/QUALITY_SCORE.md`,
  `VERIFICATION.md` (V144), this record.
