## [2026-06-14 19:34] | Task: Phase 4.7a — design-system foundation (icons + splash)

### 🤖 Execution Context
* **Agent ID**: `claude-code (session 013vN6M9RAYBQnDAybhdYE7T)`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote)`

### 📥 User Query
> Designer sweep → new Phase 4.7 "UX elevation" (all themes + delights); push Play to 4.8.
> (Plan approved.) First sub-PR: design-system foundation.

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` presentation layer + resources; docs.

**Key Actions:**
- **Real Material icons** (`androidx.compose.material:material-icons-extended`) replacing emoji
  glyphs: mic (`ChatActivity` Composer → `Icons.Filled.Mic`), History-row overflow
  (`SessionsList` → `MoreVert`), and the chat top-bar actions now `IconButton`s
  (History / IosShare / Add) — each with a `contentDescription` and ≥48dp target.
- **Android-12 splash** (`androidx.core:core-splashscreen`): `Theme.OpenAndroidUse.Starting`
  (brand indigo `@color/splash_background` + the monochrome mark, `postSplashScreenTheme`),
  applied to `MainActivity` in the manifest; `installSplashScreen()` in `MainActivity.onCreate`.
- **Docs**: new Phase 4.7 sub-plan; roadmap renumber (UX = 4.7, Play = **4.8**) + decision;
  design-delights BACKLOG section; `VERIFICATION.md` V84–V85; this history.

**Split out** (kept this PR green/reviewable): Snackbar+Undo, tokens, and motion → 4.7a-2.

### 🧠 Design Intent (Why)
Replacing emoji with real icons and adding a splash are the cheapest, highest-signal polish
wins and the foundation the rest of 4.7 builds on. Icons keep their accessible labels (the
4.6b a11y bar holds). `material-icons-extended` is convenient for breadth; its size is pruned
by R8/resource-shrink in the 4.8 Play build. Snackbar/Undo and motion are behavioral and get
their own diff so this PR stays a clean visual-foundation change.

### 📁 Files Modified
- `app/build.gradle.kts`; `res/values/{themes,colors}.xml`; `AndroidManifest.xml`
- `MainActivity.kt`, `agent/ChatActivity.kt`, `agent/SessionsList.kt`
- `docs/exec-plans/active/20260614-phase47-ux-elevation.md` (new),
  `docs/exec-plans/active/20260612-android-use-runtime.md`, `docs/BACKLOG.md`, `VERIFICATION.md`
