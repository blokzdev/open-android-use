## [2026-06-14 23:40] | Task: Phase 4.7d-1 — Home dashboard

### 🤖 Execution Context
* **Agent ID**: `claude-code (session 013vN6M9RAYBQnDAybhdYE7T)`
* **Base Model**: `claude-opus-4-8[1m]`
* **Runtime**: `Claude Code on the web (remote)`

### 📥 User Query
> Phase 4.7 UX elevation — "keep merging and rolling." 4.7d = Home + onboarding glow-up; this
> slice is the Home dashboard.

### 🛠 Changes Overview
**Scope:** `apps/OpenAndroidUseCompanion` Home (`MainActivity`) + a `ChatActivity` intent extra; docs.

**Key Actions:**
- **Dashboard `MainActivity`**: replaced the status-card + button-stack with —
  - a **brand hero** (monochrome mark + app name + tagline);
  - a **readiness card** with a status icon (check / warning) and **one context-aware primary CTA**:
    Ready → *Open chat*; otherwise → *Finish setup*, routing to the first missing prerequisite
    (accessibility if the service is off, else Settings for the key);
  - a **recent-conversations** section (up to 3 non-archived, title + preview, tap to resume, "See
    all" → History);
  - **suggestion chips** ("Try asking") that open chat **prefilled** via a new
    `ChatActivity.EXTRA_PROMPT` (prefills the composer, doesn't auto-send);
  - a cleaner **Settings / History / About** nav row + the accessibility/kill-switch footer + hint.
- **ChatActivity**: honors `EXTRA_PROMPT` (only when idle) to seed the composer.
- **Docs**: 4.7 sub-plan (4.7d-1 done; 4.7d-2 onboarding next); deferred 4.7c-2b multi-select to
  BACKLOG; `VERIFICATION.md` V106–V109; this history.

### 🧠 Design Intent (Why)
Home is the first impression and was a flat list of equal-weight buttons; a dashboard gives it a
clear hierarchy — identity, one obvious next action, a way back into recent work, and gentle
on-ramps (suggestions) — while keeping every existing route. The single context-aware CTA removes
the "which button?" ambiguity: it's always the one thing to do next. Suggestions reuse the chat's
existing prompts and prefill (not auto-send) so the user stays in control. Recents/suggestions are
read from the same `SessionStore`/strings already in use — no new data sources. Multi-select
(4.7c-2b) was deferred: it's a heavy, stateful selection mode that's the hardest 4.7c item to tune
without a device and lower value than the per-row actions already shipped.

### 📁 Files Modified
- `MainActivity.kt` (dashboard: Hero / ReadinessCard / RecentsSection / SuggestionsSection /
  SecondaryNav / AccessibilityFooter)
- `agent/ChatActivity.kt` (`EXTRA_PROMPT` prefill)
- `res/values/strings.xml` (tagline, CTAs, recents, suggestions, about)
- `docs/exec-plans/active/20260614-phase47-ux-elevation.md`, `docs/BACKLOG.md`,
  `VERIFICATION.md`, this history
