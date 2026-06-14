## [2026-06-14 04:32] | Task: Open-core licensing + onboarding docs after first hardware verification

### 🤖 Execution Context
* **Agent ID**: `claude-code`
* **Base Model**: `claude-opus-4-8`
* **Runtime**: Claude Code on the web (remote container, no Android SDK)

### 📥 User Query
> On-device verification passed on a real device (first agent turn succeeded).
> Hit Android's "Restricted setting" prompt when enabling accessibility — does
> that go away with a signed release? Is a tagged-release APK on the roadmap?
> Do a small PR updating docs/README/license; repo is a public fork and end
> vision is a Play Store launch (signed AAB). Anything for world-class mobile
> UI/UX — separate phase?

### 🛠 Changes Overview
**Scope:** licensing (root + `apps/OpenAndroidUseCompanion`), docs, in-app
(companion app), roadmap.

**Key Actions:**
- **Open-core split**: engine stays MIT (upstream © Leo preserved); the Android
  app `apps/OpenAndroidUseCompanion` is now PolyForm Perimeter 1.0.0
  (source-available, no-compete) © Blokz Development Co. Added the app `LICENSE`,
  an app `README.md` explaining the split, and a root `NOTICE` crediting
  upstream and the bundled Anthropic Java SDK (Apache-2.0, reproduced because
  the build strips its `META-INF`).
- **Restricted-settings, documented accurately**: it is an Android 13+
  *install-source* gate (every sideloaded app hits it), removed by a Play
  install — *not* by release signing. Added to README ("Install on your phone"),
  `VERIFICATION.md` (V21a), and an in-app hint shown while the service is off.
- **About sheet**: new dependency-free `AboutActivity` (plain Views) with
  version, GitHub/X/email links, and the OSS licenses/attribution block —
  doubles as the Play-Store-expected licenses surface.
- **Roadmap + Phase 4 design doc**: split the vague "release signing /
  distribution" backlog into tagged-release + Play-AAB items; added
  `docs/design-docs/phase4-product-ui.md` (Compose/Material 3 presentation
  layer, trust-and-control-first, 4.1 onboarding wizard → 4.6 Play readiness)
  and recorded the decision that Phase 4 refines the "no-androidx UI" stance for
  the UI only.
- **Metadata**: npm package `author` = Blokz Development Co.; `SUPPLY_CHAIN_SECURITY.md`
  gains a License column + redistribution note; `QUALITY_SCORE.md` next-steps
  updated (first hardware run noted; grades unchanged).

### 🧠 Design Intent (Why)
A public repo bound for a commercial Play Store launch needs its licensing to be
correct and explicit *before* release: keep the engine genuinely open (MIT, with
upstream compliance) while protecting the product the founder will ship (no-
compete). The restricted-settings friction was a real first-run wall, and the
common "it's a signing issue" misconception had to be corrected in the docs and
in-app. World-class UI is a substantial, framework-deciding effort, so it is
captured as a tracked Phase 4 rather than bolted onto this focused PR — the
control surface stays dependency-free regardless.

### 📁 Files Modified
- `LICENSE` (unchanged, MIT) · `NOTICE` (new) · `apps/OpenAndroidUseCompanion/LICENSE` (new) · `apps/OpenAndroidUseCompanion/README.md` (new)
- `apps/OpenAndroidUseCompanion/app/src/main/kotlin/dev/openandroiduse/companion/AboutActivity.kt` (new)
- `apps/OpenAndroidUseCompanion/app/src/main/kotlin/dev/openandroiduse/companion/MainActivity.kt`
- `apps/OpenAndroidUseCompanion/app/src/main/res/values/strings.xml`
- `apps/OpenAndroidUseCompanion/app/src/main/AndroidManifest.xml`
- `README.md` · `VERIFICATION.md` · `docs/SUPPLY_CHAIN_SECURITY.md` · `docs/QUALITY_SCORE.md`
- `docs/exec-plans/active/20260612-android-use-runtime.md`
- `docs/design-docs/phase4-product-ui.md` (new)
- `scripts/npm/build-android-package.mjs`
