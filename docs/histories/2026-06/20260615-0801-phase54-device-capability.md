## [2026-06-15 08:01] | Task: Phase 5.4 — device-capability tier signal + About diagnostic

### 🤖 Execution Context
* **Agent ID**: `claude-code/session_013vN6M9RAYBQnDAybhdYE7T`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote execution environment)`

### 📥 User Query
> Proceed with Phase 5.4 (device-capability diagnostics + tier-based degradation),
> and keep the autonomous loop going.

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` root package — new `DeviceCapability`;
`AboutActivity` diagnostic. No agent-loop / perception / Go changes.

**Key Actions:**
- **`DeviceCapability` + `DeviceTier`** (root package, beside `Readiness`): a pure,
  unit-tested `classifyTier(ram, cores, sdk, is64Bit, isLowRam)` → HIGH/MEDIUM/LOW,
  plus a thin Android collector `detectDeviceCapability(context)` reading
  `ActivityManager` total RAM + low-RAM flag, CPU cores, `Build.VERSION.SDK_INT`,
  64-bit ABI support.
- **About "Device" section**: surfaces the tier + the facts behind it (RAM, cores,
  Android release, 64/32-bit) — honest transparency, no gating.
- **Tests**: `DeviceCapabilityTest` covers the tier boundaries (low-RAM flag / 32-bit /
  <4 GiB → LOW; ≥8 GiB + ≥6 cores + SDK 31 + 64-bit → HIGH; else MEDIUM).

### 🧠 Design Intent (Why)
5.5 (on-device Gemma) and 5.6 (adaptive perception) must know what a device can
handle before they gate on it; the roadmap sequences 5.4 first to produce that
signal. 5.4 deliberately stays narrow — **produce + surface + test** the tier, with
**no behavior change** to the loop or perception (the gates ship with their features),
honoring "don't pile speculative entropy". Kept as a **sibling** to `Readiness`
(can-the-agent-run) rather than merged, since the two answer different questions.
Tier thresholds are coarse and **provisional** — the real "can run an on-device
model" cutoff is tuned in 5.5 against the model; nothing gates on the tier yet, so a
rough cutoff only affects an About label (deferral logged in BACKLOG).

### 📁 Files Modified
- `app/.../DeviceCapability.kt` (new), `app/.../DeviceCapabilityTest.kt` (new)
- `app/.../AboutActivity.kt`, `app/.../res/values/strings.xml`
- docs: this record, `docs/exec-plans/active/20260615-phase5-pluggable-models.md`, `docs/ARCHITECTURE.md`, `docs/QUALITY_SCORE.md`, `docs/BACKLOG.md`, `VERIFICATION.md`
