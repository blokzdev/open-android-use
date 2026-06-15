## [2026-06-15 02:00] | Task: Phase 4.7a-3 — spacing tokens + reduce-motion completeness (closes Phase 4.7); roadmap → Phase 6

### 🤖 Execution Context
* **Agent ID**: `claude-code (session 013vN6M9RAYBQnDAybhdYE7T)`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote)`

### 📥 User Query
> Complete Phase 4.7 thoroughly (the last 4.7a-3 tokens/motion slice); also move 4.8 (Play) to a
> dedicated final phase covering Play + launch readiness + performance + security hardening.

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` Compose surfaces (spacing/motion); roadmap restructure.

**Part A — 4.7a-3:**
- **Spacing tokens**: new `ui/theme/Spacing.kt` formalizing the de-facto 4dp grid
  (`xs2/sm4/md8/lg12/xl16/xxl20/xxxl24`), adopted across the *structural* spacing (screen padding,
  `Arrangement.spacedBy`, card padding) of all 7 Compose surfaces: ChatActivity, MainActivity,
  SettingsActivity, OnboardingActivity, PrivacyActivity, SessionsList, AboutActivity. Genuine
  one-off values (icon sizes, max widths, 6/10/11/14/18dp) left inline.
- **Reduce-motion completeness**: new `ui/MotionUi.kt` `isReducedMotion()` Compose helper wrapping
  `Motion.animationsDisabled`; the previously **unguarded** chat jump-to-latest FAB now scrolls
  instantly when animations are off; the auto-scroll + typing-dots paths route through the helper.
  (4/5 surfaces were already guarded; this closes the last gap.)
- Deferred custom Type tokens + motion-wrapper composables → BACKLOG.

**Part B — roadmap restructure:** old "Phase 4.8 — Play readiness" became **Phase 6 — Launch
readiness & hardening**, sequenced *after* Phase 5 and expanded with performance + security
hardening; Phase 5 now notes R8/minify-enable folds into its build work (final shrink in Phase 6).
Master roadmap updated (path line, Phase 5/6 entries, dated decision); the 4.7 sub-plan marked
COMPLETE and moved `active/ → completed/`.

### 🧠 Design Intent (Why)
The substantive win here is **reduce-motion completeness** — a real a11y guarantee — plus
establishing a named spacing scale so future surfaces stop hand-picking dp values; the token
adoption is behavior/visual-preserving (tokens encode the prior values). On sequencing: Phase 5
adds native libs, a runtime model download, new provider egress, and an on-device perf profile —
i.e. it reshapes exactly what launch-hardening targets — so consolidating Play + perf + security
into a final Phase 6 (done against the real shipping surface) avoids re-hardening after Phase 5.

### 📁 Files Modified
- new `ui/theme/Spacing.kt`, `ui/MotionUi.kt`; spacing-token adoption + (Chat) FAB/auto-scroll/
  typing reduce-motion via the helper across the 7 surfaces
- `docs/exec-plans/active/20260612-android-use-runtime.md` (Phase 5/6 + path + decision),
  `docs/exec-plans/completed/20260614-phase47-ux-elevation.md` (moved, marked complete),
  `docs/BACKLOG.md`, `VERIFICATION.md` (V120–V121), this history
