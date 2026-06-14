# Backlog — someday / maybe

Unscheduled ideas we've deliberately deferred, kept here so they aren't lost.

- **This is not the roadmap.** Scheduled, in-flight work lives in
  `docs/exec-plans/active/`. This file is the someday/maybe list: ideas worth
  remembering but not currently planned.
- **Promote when scheduled.** When an item is picked up, move it into an
  execution plan (`docs/exec-plans/active/`) and delete it here.
- **Defer with discipline.** When you cut something out of scope, add it here in
  one line with a rationale and a rough priority, so the decision is recoverable.

Each entry: **idea** — why deferred · _priority_ · origin.

## Android runtime / bridge

- **Robust no-companion bridge snapshot** — the host-side bridge reads the UI via
  the `uiautomator dump` CLI, which is brittle (idle-wait timeouts / null root,
  worse right after boot and on Android 11+). It's now hardened with
  escalating-backoff retries, but a fundamentally more reliable *no-companion*
  capture would need a minimal instrumentation APK driving `UiAutomation` with
  controlled idle-wait (the only three avenues are: the `uiautomator dump` CLI, an
  instrumentation APK, or an AccessibilityService — i.e. the companion, which
  already provides the robust path). _Low / investigate-if-needed_ — pursue only if
  the retries prove insufficient on real fleets; otherwise steer
  robustness-sensitive users to the companion. Origin: PR #9 get_app_state
  hardening.
- **`uiautomator dump --compressed` fallback** — a last-resort fallback if the
  retries still fail; it drops nodes (changes tree content), so only behind the
  full dump. _Low._ Origin: PR #9.

## On-device agent / chat

- **Structured tool chips with element labels** — chips currently prettify the
  pre-formatted `KIND_TOOL` transcript string (e.g. "Tap [42]"); showing the
  tapped element's *label* ("Tap Settings") needs richer data threaded from the
  agent core, not just the string. _Medium._ Origin: Phase 4.3 chat.
