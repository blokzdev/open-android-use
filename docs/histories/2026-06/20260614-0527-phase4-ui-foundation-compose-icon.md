## [2026-06-14 05:37] | Task: Phase 4 PR-A — Compose/Material 3 foundation + brand icon

### 🤖 Execution Context
* **Agent ID**: `claude-code`
* **Base Model**: `claude-opus-4-8`
* **Runtime**: Claude Code on the web (remote container, no Android SDK)

### 📥 User Query
> Approved Phase 4 (world-class UI/UX + onboarding), foundation-first. Design an
> awesome logo/icon for the Second-Pair-of-Hands vision; conceptualize a gorgeous
> brand palette and use it effectively. Add multi-model BYOK (Claude + Gemini) —
> GenKit or Vercel AI SDK? Should it be a new phase? Keep the roadmap covering
> the other thrusts.

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` (presentation layer only), docs.

**Key Actions:**
- **Enable Compose + Material 3** for the presentation layer only: Kotlin 2.0
  `org.jetbrains.kotlin.plugin.compose`, `buildFeatures { compose = true }`,
  Compose BOM `2024.09.03` + material3 + activity-compose. Control surface stays
  androidx-free (guardrail recorded in `SUPPLY_CHAIN_SECURITY.md`).
- **Brand "Aurora" palette + theme**: `ui/theme/Color.kt` + `Theme.kt`
  (`OpenAndroidUseTheme`) — indigo / blue→violet / mint, Material 3 light+dark,
  brand-forward (dynamic color opt-in, off by default).
- **App icon — the agent's hand tapping out an AI sparkle**: touch-gesture hand
  (blue→violet gradient) with an open mint "sparkle crown" blooming above the
  fingertip (top tip anchored; lower arms cut with gaps so the sparkle stays
  distinct from the hand, including in monochrome). Adaptive vector
  (`ic_launcher_foreground.xml`), gradient background (`ic_launcher_background.xml`),
  monochrome themed layer (`ic_launcher_monochrome.xml`); manifest icon/roundIcon
  updated. Designed iteratively with the founder by rasterizing candidates
  (hands-cradle-a-phone → tap ripple → screen-squircle → hand+sparkle) via
  cairosvg and reviewing renders at full and 48px sizes plus a monochrome proof.
- **Screen migration**: `MainActivity` + `AboutActivity` → Compose
  (`ComponentActivity` + `setContent`), preserving intents/behavior; `ChatActivity`
  stays Views (chunk 4.3).
- **Docs**: new `docs/design-docs/phase5-multi-provider-byok.md` (Claude + Gemini
  via in-house `AgentBackend` + official per-provider SDKs; GenKit/Vercel rejected
  as JS/server-side); roadmap marks Phase 4 in progress, adds Phase 5, keeps the
  parallel thrusts explicit + Decisions; phase4 design doc references the rendered
  icon (`docs/design-docs/assets/phase4-app-icon.png`); QUALITY_SCORE next-steps.

### 🧠 Design Intent (Why)
World-class UI is the Play-Store critical path but it's a framework decision
(adopt Compose) that deserved its own phase, not a feature-PR bolt-on. PR-A
de-risks the Gradle/Compose change on a deliberately no-androidx app and lands
the brand identity; the onboarding wizard follows in PR-B. Multi-provider BYOK is
on-device, so GenKit/Vercel (JS/server-side) would force a backend and break the
key-stays-local model — hence an in-house Kotlin interface + official SDKs,
captured as Phase 5.

### ⚠️ Verification note
No Android SDK in this container, so the Kotlin/Compose compile is validated by
the CI `companion` job (`gradle assembleDebug` + `testDebugUnitTest`), not
locally. Versions pinned to known-good (AGP 8.5.2 / Kotlin 2.0.21 / Gradle
8.14.3). XML resources parse-checked locally; the icon was rasterized for review.

### 📁 Files Modified
- `apps/OpenAndroidUseCompanion/build.gradle.kts` · `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/kotlin/.../ui/theme/Color.kt` (new) · `ui/theme/Theme.kt` (new)
- `app/src/main/kotlin/.../MainActivity.kt` · `AboutActivity.kt` (→ Compose)
- `app/src/main/res/drawable/ic_launcher_foreground.xml` (new) · `ic_launcher_monochrome.xml` (new) · `ic_launcher_background.xml` (new)
- `app/src/main/res/values/themes.xml` (new)
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` (new)
- `docs/design-docs/phase5-multi-provider-byok.md` (new) · `phase4-product-ui.md` · `assets/phase4-app-icon.png` (new)
- `docs/exec-plans/active/20260612-android-use-runtime.md` · `docs/SUPPLY_CHAIN_SECURITY.md` · `docs/QUALITY_SCORE.md`
