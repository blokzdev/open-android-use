## [2026-06-14 07:52] | Task: Phase 4.1 — first-run onboarding wizard + agent-readiness graceful handling

### 🤖 Execution Context
* **Agent ID**: `claude-code`
* **Base Model**: `claude-opus-4-8`
* **Runtime**: Claude Code on the web (remote container, no Android SDK)

### 📥 User Query
> After merging PR #6 (Compose foundation + brand icon), comprehensively plan and
> build PR-B: the first-run onboarding wizard. Richer scope (privacy +
> voice/confirmation toggles); the API-key step is skippable, and the UI should
> handle the missing-key case gracefully (surface a notice / degrade) rather than
> failing.

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` (presentation layer + a pure helper),
docs. Approved plan executed.

**Key Actions:**
- **Agent-readiness model** (`Readiness.kt`, pure Kotlin): `readiness(accessibilityOn,
  hasKey) -> {READY, NEEDS_ACCESSIBILITY, NEEDS_KEY, NEEDS_BOTH}`. Unit-tested
  with plain JUnit (`ReadinessTest.kt`) — no Robolectric, matching the existing
  pure-logic test style and the minimal-dependency posture.
- **Onboarding wizard** (`OnboardingActivity.kt`, Compose): 6 steps — welcome →
  enable accessibility (deep-link + restricted-settings hint, auto-advances when
  `CompanionService.isRunning` flips true on resume) → privacy & transparency →
  skippable API key + model dropdown (persists via `storeApiKey`, kicks
  `ModelCatalog.refresh`) → preferences (confirm/voice toggles) → ready. Steps via
  `mutableStateOf` + `Crossfade` (no nav library on the classpath).
- **Gating**: `MainActivity` routes to the wizard on first run
  (`AgentSettings.onboardingCompleted`, new boolean pref), then never again; the
  home card now reflects readiness (accessibility + key) with a "key needed" line.
- **Graceful degradation**: `ChatActivity.sendTask` checks readiness before
  starting — surfaces a clear note + opens the relevant fix (settings dialog for
  the key, accessibility settings otherwise) and preserves the typed task,
  instead of a silent failure.
- **Build/docs**: added `androidx.compose.animation:animation` (for `Crossfade`);
  registered `OnboardingActivity`; `VERIFICATION.md` V42–V47; roadmap (4.1 done)
  + QUALITY_SCORE.

### 🧠 Design Intent (Why)
The roughest new-user moments are enabling accessibility (past the Android 13+
restricted-settings gate) and adding a key; the wizard guides both. Making the
key skippable keeps first-run friction low, but the agent has two hard
prerequisites — so a single `readiness` helper drives consistent "what's missing"
guidance across home and chat, and the app never fails cryptically.

### ⚠️ Verification note
No Android SDK in this container, so the Kotlin/Compose compile is validated by
the CI `companion` job (`gradle assembleDebug` + `testDebugUnitTest`); the new
`ReadinessTest` runs there. The `emulator-smoke` job drives `AgentController`
directly and never launches MainActivity, so the onboarding gate doesn't affect
it. Wizard copy is inline (matching ChatActivity's pragmatic style) rather than
new string resources, to minimise compile risk without local compilation.

### 📁 Files Modified
- New: `OnboardingActivity.kt`, `Readiness.kt`, `app/src/test/.../ReadinessTest.kt`
- Edit: `MainActivity.kt` (gate + readiness card), `agent/ChatActivity.kt`
  (graceful send-path), `agent/AgentSettings.kt` (`onboardingCompleted`),
  `AndroidManifest.xml`, `app/build.gradle.kts`
- Docs: `VERIFICATION.md`, `docs/exec-plans/active/20260612-android-use-runtime.md`,
  `docs/QUALITY_SCORE.md`
