# VERIFICATION.md — on-device verification ledger

> Working checklist of everything that must be verified on real hardware (or an
> emulator) because the dev container has no Android device attached. Each item
> says exactly what to run and what "pass" looks like. Check items off as you go;
> this file is deleted once everything passes and the results are recorded in
> `docs/histories/`.

> **CI automation:** `scripts/run-android-smoke-tests.sh` (CI: the
> `emulator-smoke` job in `.github/workflows/android-runtime.yml`, which boots a
> real API-30 emulator) automates the scriptable subset — roughly V1–V5, V7,
> V14, V20–V23, V26, V29, the V27 routing guard, the session-store I/O behind
> V67/V68/V70 (`SessionStoreInstrumentedTest`), the API-key Keystore round-trip
> behind V33 (`AgentSettingsInstrumentedTest`), and the Settings/History screens
> rendering + menus behind V62/V70 (Compose UI tests `SettingsScreenTest` /
> `SessionsScreenTest`, which also assert heading semantics + the 48dp overflow
> touch target for V74). A green emulator-smoke run is strong evidence, but the
> real-hardware pass below remains authoritative (OEM ROMs, IMEs, secure surfaces,
> and touch behavior differ).

## How to set up

1. Install [Android platform-tools](https://developer.android.com/tools/releases/platform-tools); `adb version` should work.
2. Enable Developer Options + USB debugging on the device (or start an emulator:
   `emulator -avd <name>`).
3. `adb devices` shows the device as `device` (not `unauthorized`).
4. Build the bridge: `make android-build` → `dist/android-bridge/<os>/<arch>/open-android-use`.

Set `OAU=dist/android-bridge/<os>/<arch>/open-android-use` for the steps below.

## Phase 1 — Android bridge (ADB runtime)

- [ ] **V1. Doctor**: `$OAU doctor`
  Pass: prints adb version, lists the device serial, shows `selected:` and a
  plausible `foreground:` package/activity.
- [ ] **V2. Device listing**: `$OAU devices`
  Pass: one line per connected device.
- [ ] **V3. App listing**: `$OAU call list_apps`
  Pass: `Running in foreground:` matches what's on screen; launcher apps listed.
- [ ] **V4. Snapshot of the foreground app**: `$OAU snapshot foreground`
  Pass: tree lines with indexed `[n]` actionable elements; labels match the screen.
- [ ] **V5. Snapshot launches a backgrounded app**: pick an app not on screen,
  e.g. `$OAU call get_app_state --args '{"app":"settings"}'`
  Pass: the app visibly comes to the foreground; result contains tree + screenshot
  (base64 PNG block in the JSON).
- [ ] **V6. Screenshot budget & coordinate alignment**: decode the base64 PNG from
  V5 (`jq -r '.content[1].data' | base64 -d > shot.png`).
  Pass: PNG ≤ ~900KB, long edge ≤ 1280px, and element frames from the tree line up
  with what's drawn at those pixel positions in the PNG (spot-check 2–3 elements).
- [ ] **V7. Element click**: in Settings, click an indexed row via
  `$OAU call --calls '[{"tool":"get_app_state","args":{"app":"settings"}},{"tool":"click","args":{"app":"settings","element_index":"<idx>"}}]'`
  Pass: the right row opens; post-action snapshot reflects the new screen.
- [ ] **V8. Coordinate click**: click by `x`/`y` taken from a screenshot pixel
  position. Pass: tap lands on the element at that position in the PNG (this is
  the CoordinateScale invariant on real hardware — the critical check).
- [ ] **V9. Long-press**: `click` with `"mouse_button":"right"` on a home-screen
  icon or list item. Pass: context menu / long-press behavior triggers.
- [ ] **V10. Scroll**: `scroll` with `direction: "down"` on a scrollable element
  (e.g. Settings list), then `pages: 0.5`. Pass: list scrolls; half page moves
  visibly less than a full page.
- [ ] **V11. Drag**: `drag` a home-screen icon between positions.
  Pass: icon moves (draganddrop) — or at minimum the swipe-fallback drag occurs.
- [ ] **V12. type_text (ASCII)**: focus a search box (click it first), then
  `type_text` with `"hello world 123"`. Pass: exact text appears, spaces intact.
- [ ] **V13. type_text non-ASCII guard**: `type_text` with `"héllo"`.
  Pass: clean error mentioning ASCII + on-device companion; nothing typed.
- [ ] **V14. press_key basics**: `press_key` with `"Back"`, then `"Return"` in a
  text field, then `"BackSpace"`. Pass: each key has its expected effect.
- [ ] **V15. press_key combination** (Android 13+): `press_key` with `"ctrl+a"` in
  a focused text field. Pass: text selects (or a clear error on older Android).
- [ ] **V16. set_value**: on an EditText element index, `set_value` with
  `"replaced"`. Pass: prior text replaced on Android 13+; on older devices the
  value may be appended (known limitation, note the OS version).
- [ ] **V17. MCP end-to-end**: add the bridge to a real MCP client (Claude Code:
  `claude mcp add open-android-use -- $OAU mcp`) and ask the agent to open
  Settings and toggle something benign. Pass: full agent loop works.
- [ ] **V18. Multi-device selection**: with two devices/emulators attached, verify
  the bridge refuses without `OPEN_ANDROID_USE_SERIAL` and obeys it when set.
- [ ] **V19. uiautomator flake handling**: hammer `$OAU snapshot foreground` ~10x
  in a row. Pass: no hard failures (the one-retry path absorbs transient empties).

## Phase 2 — On-device companion

Build the APK first: `ANDROID_HOME=<sdk> make companion-build`
→ `dist/companion/open-android-use-companion.apk` (also published as a CI
artifact by `.github/workflows/android-runtime.yml`).

- [ ] **V20. Companion APK installs**: `adb install dist/companion/open-android-use-companion.apk`
  Pass: installs on Android 8.0+ (minSdk 26); "Open Android Use Companion"
  appears in the launcher.
- [ ] **V21. Accessibility service enable flow**: open the companion app, tap
  "Open Accessibility Settings", enable "Open Android Use Companion", return.
  Pass: status shows "Service: running — endpoint live on 127.0.0.1:8355".
  - **V21a. Restricted-settings gate (Android 13+, sideloaded)**: if enabling
    accessibility is blocked by a "Restricted setting" dialog, the in-app hint
    (shown while the service is OFF) should point to *Settings → Apps → Open
    Android Use Companion → ⋮ → Allow restricted settings*. Pass: following that
    clears the block and the service enables; the hint disappears once running.
- [ ] **V21b. About sheet**: from the main screen tap "About". Pass: shows the
  app version (0.2.3), and the GitHub / X / email links open the right targets;
  the "Licenses & attribution" block names PolyForm Perimeter (app), MIT
  (engine), and the Apache-2.0 Anthropic SDK.
- [ ] **V22. Companion endpoint over adb forward**:
  `adb forward tcp:8355 tcp:8355` then `curl http://127.0.0.1:8355/health`
  Pass: `{"ok":true,"service":"open-android-use-companion","version":"0.2.3","protocol":1,"screenshot":<bool>}`
  (`screenshot` true on Android 11+).
- [ ] **V23. Companion snapshot**: `curl http://127.0.0.1:8355/snapshot`
  Pass: `{"ok":true,"protocol":1,"package":"<foreground>","tree":{...}}` — spot
  check that `tree` labels/bounds match the screen.
- [ ] **V24. Companion gesture via HTTP**: pick a visible button's center from
  V23 bounds, then
  `curl -X POST -d '{"type":"tap","x":<x>,"y":<y>}' http://127.0.0.1:8355/action`
  Pass: `{"ok":true}` and the tap visibly lands. Repeat with `longPress` and a
  `swipe`.
- [ ] **V25. Companion screenshot** (Android 11+):
  `curl http://127.0.0.1:8355/screenshot -o companion.png`
  Pass: valid full-resolution PNG of the current screen.
- [ ] **V26. Bridge detects companion**: with the service enabled, run
  `$OAU doctor`. Pass: a `companion: available ... (v0.2.3, protocol 1, via
  127.0.0.1:8355)` line (the bridge sets up the adb forward itself).
- [ ] **V27. Unicode typing through the bridge** (headline Phase 2 capability):
  focus a text field (click it via the bridge), then
  `OPEN_ANDROID_USE_COMPANION=1 $OAU call --calls '[{"tool":"get_app_state","args":{"app":"foreground"}},{"tool":"type_text","args":{"app":"foreground","text":"héllo 🚀 你好"}}]'`
  Pass: the exact text appears in the field (ACTION_SET_TEXT path).
- [ ] **V28. Kill switch**: disable the accessibility service mid-session.
  Pass: companion-mode `type_text` with non-ASCII fails fast with a clear
  "Companion is not reachable" error; ASCII text still works (silent fallback to
  the ADB path); `$OAU doctor` reports `companion: not available`.

### Phase 2.1 — companion mode through the bridge

With the service enabled and `OPEN_ANDROID_USE_COMPANION=1` exported:

- [ ] **V29. Companion-backed snapshot through the bridge**:
  `OPEN_ANDROID_USE_COMPANION=1 $OAU snapshot foreground`
  Pass: same output shape as V4 but sourced from the companion's live tree — no
  `uiautomator dump` lag, and it works while a text field has IME focus
  (uiautomator's weakness). Confirm via `adb logcat -s OpenAndroidUse` that the
  snapshot came from the companion.
- [ ] **V30. Companion-backed gestures through the bridge**: repeat V7 (element
  click), V9 (long-press), V10 (scroll), and V11 (drag) with companion mode on.
  Pass: identical or better behavior; gestures land mid-animation too.
- [ ] **V31. Companion set_value with Unicode**: on an EditText element index,
  `set_value` with `"héllo 🚀 你好"`.
  Pass: prior text fully replaced (ACTION_SET_TEXT) with the exact Unicode value
  on any Android version — no ctrl+a dependency.
- [ ] **V32. Degradation matrix**: kill the companion mid-session and repeat
  V29/V30. Pass: snapshots and gestures silently fall back to the
  uiautomator/ADB path (slower but correct); only non-ASCII typing errors out.

### Phase 3.1a — on-device agent

With the companion installed, the accessibility service enabled, and an
Anthropic API key at hand (Android 11+ device):

- [ ] **V33. Agent settings**: open the companion app → "Open Agent Chat" →
  Settings; enter the API key and keep the default model.
  Pass: dialog reports the key as configured on reopen; the key survives an
  app restart (Keystore-encrypted prefs) and never appears in `adb logcat`.
- [ ] **V34. First agent turn**: ask "Open Settings and tell me the Android
  version".
  Pass: the agent narrates intent, a `get_app_state` tool chip appears,
  Settings comes to the foreground, the agent taps/scrolls to About phone,
  and the final message states the correct Android version.
- [ ] **V35. Stop button**: start a multi-step task ("open three different
  apps one after another") and press Stop mid-task.
  Pass: the loop halts within one action (no further gestures land), the UI
  shows "Stopped.", and Send re-enables.
- [ ] **V36. Error surfaces**: enter a deliberately bad API key and send a
  task. Pass: a clear authentication error appears in the transcript (no
  crash, no retry loop).
- [ ] **V37. Unicode typing through the agent**: ask the agent to type
  "héllo 🚀 你好" into a notes app.
  Pass: exact text lands (set_value/ACTION_SET_TEXT path), and the next
  turn's screenshot confirms it.

### Phase 3.1b/3.1c — safety surfaces and voice

- [ ] **V38. Gesture trail**: during any agent task, watch the screen.
  Pass: every agent tap shows a fading blue ripple and every swipe a fading
  stroke at the exact gesture location; the overlay never intercepts touches
  and disappears when the task ends.
- [ ] **V39. Touch-to-pause**: while the agent is mid-task in another app,
  tap anywhere on the screen (≥3s after the agent's last gesture).
  Pass: the agent stops within one action; the chat shows "Paused — you
  touched the screen"; pressing buttons in the companion's own chat UI does
  NOT trigger the pause.
- [ ] **V40. Confirmation sheet**: enable "Ask before each action batch" in
  settings, then give the agent a task.
  Pass: a bottom sheet listing the pending actions appears over whatever app
  is foreground; Allow proceeds; Deny makes the agent acknowledge and ask
  for direction instead of retrying; no sheet appears for pure
  get_app_state/list_apps turns.
- [ ] **V41. Voice**: enable "Speak narration aloud", send a task, and lock
  attention elsewhere; then use the 🎤 button to dictate a task.
  Pass: narration is spoken sentence-by-sentence while the agent works (and
  goes silent on Stop); dictation lands in the input field for review.

### Phase 4.1 — First-run onboarding wizard + graceful handling

- [ ] **V42. First-run wizard appears**: clear app data (or fresh install) and
  open the app. Pass: the onboarding wizard launches instead of the home screen
  (Welcome → step 1 of 6).
- [ ] **V43. Accessibility step + auto-advance**: on the accessibility step, tap
  "Open Accessibility Settings", clear the Android 13+ restricted-settings gate
  (the inline hint explains how), enable the service, return. Pass: the wizard
  auto-advances to the privacy step; the card showed "running ✓".
- [ ] **V44. Skip the API key**: on the API-key step, tap "Skip for now".
  Pass: onboarding completes; the home screen shows "API key needed" with
  guidance; relaunching the app does NOT show the wizard again.
- [ ] **V45. Graceful no-key on send**: with no key set, open Agent Chat, type a
  task, Send. Pass: a clear note appears ("Add your Anthropic API key to start")
  and the settings dialog opens; the typed task is preserved; no crash/silent
  fail. After adding a key, Send works.
- [ ] **V46. Graceful no-accessibility on send**: with the service disabled but a
  key set, Send a task. Pass: a note appears ("Enable the companion accessibility
  service…") and Accessibility Settings opens; no silent fail.
- [ ] **V47. Preferences persisted**: toggle "Ask before each action batch" /
  "Speak narration" in the wizard. Pass: the choices match in Agent Chat →
  Settings afterward.

### Phase 4.3 — World-class chat (Compose)

- [ ] **V48. Live streaming**: send a task. Pass: the assistant reply streams in
  smoothly (no jank/flicker) and the list auto-scrolls to the newest message.
- [ ] **V49. Agent's view**: during a task. Pass: the "What the agent sees" card
  updates with the latest screenshot as the agent acts; tapping it opens a
  full-screen view. (Before any capture it shows the "second pair of eyes" hint.)
- [ ] **V50. Tool chips read clearly**: Pass: actions render as friendly chips
  ("Open Settings", "Tap [n]", "Type …", "Scroll down"); failures show an error
  chip.
- [ ] **V51. Thinking toggle**: Pass: a "Show thinking" control reveals/hides the
  reasoning; it's hidden by default.
- [ ] **V52. Markdown answers**: ask for a list. Pass: bullets/numbers, bold,
  italic, and inline code render.
- [ ] **V53. Select / copy / share**: long-press an answer to select & copy;
  tap "Share". Pass: text is selectable/copyable and the share sheet opens.
- [ ] **V54. New conversation + model chip + haptics**: Pass: "New" clears the
  transcript; the model chip shows the active model and opens settings; Send/Stop
  give a haptic tick; the input sits above the keyboard (IME inset).
- [ ] **V55. Error card + fix**: enter a bad API key and send. Pass: a styled
  error/needs-key card appears with a one-tap fix that opens settings.
- [ ] **V56. Stop always reachable**: mid-task. Pass: Stop is visible in the
  "agent's view" status row and the composer; tapping it halts within one action.

### Phase 4.4 — Trust & control surface

- [ ] **V57. In-control badge over other apps**: start a task that leaves the
  app. Pass: a floating "👐 Open Android Use is acting · Stop" chip appears over
  whatever app is foreground; it disappears when the task ends.
- [ ] **V58. Badge Stop works from anywhere**: tap the badge's Stop while the
  agent is in another app. Pass: the agent halts within one action.
- [ ] **V59. Badge opens chat**: tap the badge label. Pass: the Agent Chat comes
  to the foreground.
- [ ] **V60. Ongoing notification + Stop**: with notifications allowed, during a
  task. Pass: an ongoing "Open Android Use is acting" notification shows a Stop
  action that halts the agent; it clears when the task ends. (Deny notifications →
  no notification, but the badge still works.)
- [ ] **V61. Tap-location highlight**: during a task, watch the "Agent's view" in
  chat. Pass: a mint ring marks where the agent just tapped, aligned to the
  screenshot; it tracks subsequent taps and clears on New conversation.

### Phase 4.5 — Settings, privacy & multi-session conversations

The emulator-smoke `SessionStoreInstrumentedTest` already exercises the on-device
persistence I/O (save, newest-first list, load, rename, archive, delete,
delete-all); the items below cover the UI and the resume behavior on real
hardware.

- [ ] **V62. Settings screen (not a dialog)**: open Agent Chat → tap the model
  chip (or Home → Settings). Pass: a full **Settings** screen opens showing API
  key status, model picker, the confirm/voice toggles, and (Android 12+) a
  Material You toggle — the old in-chat dialog is gone.
- [ ] **V63. API key save & clear**: enter a key → "Save key" (toast; model list
  refreshes); then "Clear key". Pass: status flips to "not set", the agent now
  reports it needs a key, and the key never appears in `adb logcat`.
- [ ] **V64. Material You toggle (Android 12+)**: toggle "Use system colors".
  Pass: the UI recolors to the system palette immediately and reverts to the
  brand "Aurora" palette when off; the choice persists across an app restart. On
  Android < 12 the toggle is absent.
- [ ] **V65. Re-run setup**: Settings → "Re-run setup". Pass: the onboarding
  wizard launches again; completing it returns to the app.
- [ ] **V66. Privacy & data screen**: Settings → "Privacy & data". Pass: the
  trust story is browsable — on-device, what leaves the device (only
  api.anthropic.com), Keystore key storage, text-only saved conversations, and
  the kill switch.
- [ ] **V67. Data controls**: each of "Clear API key", "Clear current
  conversation", "Delete all saved conversations" asks for confirmation and does
  exactly what it says (delete-all empties History; clear-conversation empties
  the current chat without touching saved ones).
- [ ] **V68. Conversation persists + auto-named**: run a task, then open History
  (or background and reopen the app). Pass: the conversation is saved, **named
  from the first prompt**, newest first.
- [ ] **V69. Resume with context (headline)**: from History, tap a past
  conversation → it reopens with its transcript; send a follow-up like "what did
  you just do?". Pass: the agent answers using the prior conversation's context
  (history rebuilt from the transcript) and re-observes the device live for any
  new action — no stale screenshots.
- [ ] **V70. Rename / archive / delete**: use a row's ⋯ menu. Pass: rename
  updates the title (and the live title if it's the open session); archive shows
  the chip and removes it from the empty-state recents; delete removes it
  (blocked with a toast if it's the currently-running session).
- [ ] **V71. Recent sessions on the empty state**: tap "New", then view the empty
  chat. Pass: up to three recent non-archived conversations appear above the
  suggested prompts; tapping one resumes it.
- [ ] **V72. Export a conversation**: tap "Export". Pass: the share sheet offers a
  Markdown `.md` (named from the title); the file contains the full transcript
  (You / Agent / tool / thinking) and **no images**.
- [ ] **V73. Screenshots never persisted**: after image-producing tasks, inspect
  the saved session (export, or `adb run-as dev.openandroiduse.companion` over
  `files/sessions/*.json`). Pass: the JSON is text-only — no base64 PNG — with
  only "(screenshot omitted…)" markers where a screenshot had been.

### Phase 4.6b — Accessibility

The emulator-smoke Compose tests already assert heading semantics and the 48dp
overflow touch target; the items below are the screen-reader / settings checks that
need a real device.

- [ ] **V74. TalkBack reads every control**: enable TalkBack and swipe through each
  screen (Main, Onboarding, Settings, Privacy, History, Chat). Pass: every button,
  toggle, and the mic/overflow/in-control/Stop controls are announced with a
  meaningful label (no "unlabelled button"); section/step titles are announced as
  headings and reachable via heading navigation.
- [ ] **V75. Agent run state is announced**: with TalkBack on, start and stop a task.
  Pass: the "What the agent sees" status announces "Agent is working" on start and
  "Agent is idle" on stop (the spinner state is no longer silent); error tool-chips
  are announced as "Error: …".
- [ ] **V76. Large font / display size**: set Settings → Display → Font size and
  Display size to the largest. Pass: all screens remain usable — text scales, nothing
  important is clipped or overlapped; the in-control chip and confirmation sheet stay
  legible with ≥48dp buttons.
- [ ] **V77. Reduce motion**: enable Settings → Accessibility → Remove animations,
  then run a task. Pass: the gesture-trail ripples do not appear, the chat does not
  animate-scroll (jumps instantly), and onboarding step transitions are instant; the
  app otherwise behaves normally.

### Phase 4.6c — Responsive / large-screen

- [ ] **V78. Edge-to-edge**: on each screen, content draws under the status/nav bars
  without being obscured (top bar clears the status bar; the chat composer clears the
  nav bar / IME). Pass: nothing important is hidden behind system bars on a notched
  device; the composer still sits above the keyboard.
- [ ] **V79. Tablet / landscape content width**: on a tablet or in landscape, the
  Main/Settings/Privacy/About content is centered and capped (~640dp) rather than
  stretched full-width; chat bubbles don't span the whole width. Pass: comfortable
  line length on large screens; phones look unchanged.
- [ ] **V80. Predictive back**: on Android 14+, the back gesture shows the predictive
  back animation and navigates correctly from each screen. Pass: no janky/abrupt back;
  resuming History → Chat still routes to the single chat instance.
- [ ] **V81. RTL**: enable a RTL pseudo-locale (or force RTL). Pass: layouts mirror
  correctly (start/end honored); no clipped or left-stuck elements.

### Phase 4.6d — Richer markdown

- [ ] **V82. Links & tables in answers**: ask the agent something whose reply includes a
  Markdown link and a `| pipe | table |`. Pass: the link renders underlined/colored and
  opens in the browser when tapped; the table renders as aligned rows (header bold) and
  scrolls horizontally if wider than the screen rather than overflowing.

### Phase 4.6e — Tablet / foldable two-pane

- [ ] **V83. Side-by-side on large screens**: open Agent Chat on a tablet, an unfolded
  foldable, or a large/split window. Pass: the History list and the Chat show side by side;
  tapping a conversation swaps the chat in place (no new screen); rename/archive/delete in the
  pane work; the redundant top-bar "History" action is hidden. On a phone (and folded) it's a
  single pane and the Main → History flow is unchanged; rotating / folding-unfolding keeps the
  current chat (no lost state).

### Phase 4.7a — Design-system foundation (icons + splash)

- [ ] **V84. Splash screen**: cold-launch the app. Pass: the Android-12 splash shows the brand
  mark on the indigo ground, then hands off to the Home screen (no white flash / no double
  splash); on Android < 12 the back-compat splash is acceptable.
- [ ] **V85. Real icons, still labelled**: the chat top-bar actions (History/Export/New), the
  mic, and the History row overflow now show Material icons (no emoji). Pass: each is announced
  by TalkBack with its label (History, Export, New conversation, Voice input, "More options
  for …") and the touch targets stay ≥48dp.

### Phase 4.7a-2 — Snackbar + Undo for destructive actions

- [ ] **V86. Privacy Undo (clear key)**: with an API key configured, open Privacy & data →
  "Clear API key" → confirm. Pass: a Snackbar says the key was cleared and offers **Undo**;
  tapping Undo restores the key (Settings/Chat again report the key is configured); letting the
  Snackbar dismiss leaves the key cleared.
- [ ] **V87. Privacy Undo (clear current conversation)**: with an active chat, Privacy →
  "Clear current conversation" → confirm → **Undo**. Pass: the live conversation (transcript +
  rebuilt context) comes back; without Undo it stays cleared.
- [ ] **V88. Privacy Undo (delete all conversations)**: with several saved conversations,
  Privacy → "Delete all saved conversations" → confirm → **Undo**. Pass: every conversation
  reappears in History; without Undo all are gone.
- [ ] **V89. History delete Undo (phone + two-pane)**: in History (full-screen on a phone, and
  the side pane on a tablet/foldable) delete a conversation → **Undo**. Pass: the row returns in
  place; deleting the *running* session is still blocked with the "stop the task" notice (no
  Snackbar/Undo for that blocked case).

### Phase 4.7b-1 — Chat: per-message Copy/Share + jump-to-latest

- [ ] **V90. Per-message Copy**: in a conversation, long-press a user bubble and an assistant
  bubble. Pass: a haptic tick fires and a menu shows **Copy** / **Share**; Copy puts the message
  text on the clipboard and a Snackbar says "Copied to clipboard" (paste elsewhere to confirm).
- [ ] **V91. Per-message Share**: long-press a bubble → **Share**. Pass: the system share sheet
  opens with the message text; sharing to e.g. Notes/Messages carries the exact text.
- [ ] **V92. Jump-to-latest + no scroll-yank**: scroll up in a long conversation. Pass: a small
  down-arrow FAB appears bottom-right; while scrolled up, a new agent turn does **not** drag the
  view to the bottom (you keep reading); tapping the FAB scrolls to the newest message and the
  FAB disappears. When already at the bottom, new turns still auto-follow.

### Phase 4.7b-2 — Chat: typing cue + error→Retry

- [ ] **V93. Typing indicator**: start a task and watch the chat. Pass: while the agent is
  composing (before/between replies), an assistant-aligned bubble of pulsing dots appears at the
  end of the list and disappears once the reply streams in / the task ends; with system
  animations off the dots are static (no pulsing); TalkBack announces "Agent is working".
- [ ] **V94. Error → Retry**: cause a task to end in an error (e.g. an invalid API key, or stop
  connectivity) so the last message is an error note. Pass: that note shows a **Retry** action
  (only on the last message, only when idle); tapping it re-runs the most recent user task; if a
  prerequisite is now missing it routes to Settings / Accessibility instead.

### Phase 4.7c-1 — History: pin + preview + date grouping

- [ ] **V95. Date grouping**: with conversations from different days, open History. Pass: rows are
  grouped under **Today / Yesterday / Earlier** headers, newest first within each group; a
  conversation updated today sits under Today.
- [ ] **V96. Pin floats to top**: pin a conversation (overflow → Pin). Pass: it moves into a
  **Pinned** section at the top with a pin badge, regardless of its date; Unpin returns it to its
  date group; pinning does **not** change its "updated" time / reshuffle the rest by recency.
- [ ] **V97. Last-message preview**: each row shows a one-line preview (the agent's last reply,
  else the last prompt) under the title; long previews are truncated to one line.
- [ ] **V98. Pin survives the agent**: pin the *active* conversation, then run another task in it
  so the agent re-saves the session. Pass: it stays pinned (the snapshot didn't clobber the pin);
  the preview reflects the latest reply.
- [ ] **V99. Two-pane parity**: on a tablet/foldable, the same grouping / preview / pin behavior
  works in the side History pane.

### Phase 4.7b-3a — Timestamp plumbing (no visible change)

- [ ] **V100. Back-compat + resume preserves times**: open a conversation saved before this build
  (a v1/v2 session file) — it still loads and resumes normally. Run a new task, leave and resume
  it from History. Pass: nothing regresses (chat renders, export works, History preview/pin/group
  intact); per-message start times are now captured and survive a resume (visible rendering lands
  in 4.7b-3b).

### Phase 4.7b-3b — Chat: timestamps + role grouping

- [ ] **V101. Role grouping**: run a task that produces a run of agent/tool/thinking steps. Pass:
  steps within the same role sit tightly together, with a clear extra gap when the turn switches
  (user ↔ agent), so each turn reads as one chunk rather than a uniform stack.
- [ ] **V102. Time separator on gaps**: send a message, wait >5 min, send another (or resume an
  older conversation). Pass: a centered relative day/time marker (e.g. "Yesterday 2:34 PM")
  appears above the later turn; rapid back-to-back turns show no separator.
- [ ] **V103. Per-turn time caption**: each user and agent turn shows a small locale-aware
  time-of-day under the last bubble of its run; conversations saved before 4.7b-3a (no stored
  time) simply show no caption (no "1970"/epoch artifact).

### Phase 4.7c-2a — History: search + archived filter

- [ ] **V104. Search**: in History with several conversations, type in the search field. Pass: the
  list filters live by title and by last-message preview (case-insensitive); clearing (the ✕)
  restores the full list; a no-match query shows "No matching conversations".
- [ ] **V105. Archived hidden by default**: archive a conversation. Pass: it disappears from the
  default list; a "Show archived" chip appears (only when archived ones exist) and toggling it
  brings archived rows back (with their badge). Works on both phone and the two-pane History.

### Phase 4.7d-1 — Home dashboard

- [ ] **V106. Context-aware CTA**: open Home. Pass: when set up, a brand hero shows and the
  readiness card shows a green check + "Agent ready" with an **Open chat** primary button; with a
  prerequisite missing, it shows a warning + the relevant message and a **Finish setup** button
  that routes to the first missing step (accessibility if the service is off, else Settings for the
  key).
- [ ] **V107. Recents + resume**: with saved conversations, Home shows up to 3 recent ones (title +
  preview); tapping one opens that conversation; "See all" opens History; archived ones are
  excluded.
- [ ] **V108. Suggestion prefill**: tap a suggestion chip under "Try asking". Pass: chat opens
  with that prompt prefilled in the composer (not auto-sent), ready to edit/send.
- [ ] **V109. Nav + kill switch**: Settings / History / About each open their screen; the
  accessibility button still opens accessibility settings (the kill switch), and the hint remains.

### Phase 4.7d-2 — Onboarding glow-up

- [ ] **V110. Stepper + icons**: run first-launch onboarding (or clear data). Pass: a row of
  progress dots tracks position (the current one larger/filled), each step shows a distinct large
  icon, and TalkBack announces "Step n of 6".
- [ ] **V111. Success states**: on the accessibility step, the status card shows a green check once
  the service is enabled (neutral icon before); the Ready step shows check/neutral rows for
  accessibility and API key reflecting actual state.
- [ ] **V112. Reduce-motion**: with system animations off, stepping through onboarding shows no
  cross-fade (instant step change); with animations on, steps cross-fade.
- [ ] **V113. First-task chip**: on the Ready step, tap the example chip. Pass: onboarding
  completes and chat opens with that example prefilled in the composer (not auto-sent).

### Phase 4.7e-1 — Settings: API-key depth

- [ ] **V114. Show/hide key**: in Settings, the API-key field masks input; tapping the eye icon
  reveals/hides it (icon + content description flip). 
- [ ] **V115. Test key**: with a valid key entered (or saved), tap **Test key**. Pass: a spinner
  shows, then a Snackbar reports "API key works"; with a bad key it reports "Key didn't work: …".
  Testing uses the entered key if present, else the saved key.
- [ ] **V116. Get a key**: tap "Get an API key". Pass: the Anthropic console keys page opens in a
  browser.

### Phase 4.7e-2 — Settings: theme mode (Light/Dark/System)

- [ ] **V117. Theme mode applies app-wide**: in Settings, pick Light / Dark / System via the
  segmented control. Pass: the app re-themes immediately; navigating to Home/Chat/History/Privacy
  shows the chosen mode; "System" follows the device dark-theme setting. Choice persists across
  app restart and composes correctly with the Material You toggle.

### Phase 4.7e-3 — Privacy: storage usage + Export all

- [ ] **V118. Storage summary**: open Privacy & data. Pass: a Storage section shows the saved
  conversation count and total size (e.g. "3 saved conversation(s) · 12 kB on this device");
  matches reality after creating/deleting conversations (re-enter the screen to refresh).
- [ ] **V119. Export all**: tap "Export all conversations". Pass: the share sheet offers a single
  Markdown file containing every saved conversation (separated by rules); with none saved, a
  "No saved conversations to export" notice instead.

### Phase 4.7a-3 — Spacing tokens + reduce-motion completeness

- [ ] **V120. Reduce-motion: jump-to-latest**: enable system "Remove animations". In chat, scroll
  up so the jump-to-latest FAB appears, then tap it. Pass: the list jumps **instantly** (no smooth
  scroll). With animations on, it scrolls smoothly. (Auto-scroll on new messages and the typing
  dots already honored reduce-motion; this closes the last gap.)
- [ ] **V121. Spacing unchanged**: visually spot-check Home / Chat / Settings / Onboarding /
  Privacy / History / About — padding and gaps look the same as before the token refactor (the
  tokens encode the previous 8/12/16dp values; this is a no-visual-change cleanup).

### Phase 5.1 — AgentBackend provider seam (pure refactor)

- [ ] **V122. Thinking + tool_use resume round-trip**: with a real key, run a task that uses
  extended thinking and at least one tool call; let it finish, then resume that session (History →
  open → send a follow-up) so its history is rebuilt and re-sent. Pass: the follow-up turn proceeds
  with **no API 400** about thinking/tool_use blocks or missing thinking signatures, and the agent
  continues the task normally. (Guards the `replayPayload` round-trip the keyless smoke can only
  approximate without a live key; everyday tasks running unchanged is the broader behavior-preservation check.)

### Phase 5.2 — Gemini BYOK + provider switcher

- [ ] **V123. Gemini key test + live model list**: Settings → Provider → Gemini; paste a real Gemini
  API key → **Test key** reports valid, and the model dropdown populates from the live list (incl.
  3.x ids). Pass: valid result + a non-empty, Gemini-only model list.
- [ ] **V124. Gemini end-to-end task**: with Gemini selected and a key set, run a real multi-step task
  (e.g. "open Settings and tell me the Android version"). Pass: the agent issues tool calls, receives
  screenshots, and completes the task with a correct answer — no API errors about function calls or
  parts.
- [ ] **V125. Per-provider persistence & isolation**: set a Gemini key while a Claude key is already
  configured; restart the app. Pass: provider, both keys, and each provider's model selection persist;
  adding/clearing the Gemini key never affects the Claude key (and vice-versa).

### Phase 5.3 — model lists behind the provider + R8

- [ ] **V126. Minified release build runs end-to-end (R8 gate)**: install the **release** APK
  (`gradle assembleRelease`, R8 code-shrink on) and run a real task on **both** Claude and Gemini.
  Pass: no runtime `ClassNotFoundException`/`NoSuchMethodError`/JSON-(de)serialization failure from
  R8 stripping; both providers stream, call tools, and complete. (The keep-rules build clean, but only
  an on-device run of the shrunk build proves no reflected SDK member was removed — this is the Phase 6
  final-shrink gate, staged now.)

### Phase 5.4 — device-capability tier

- [ ] **V127. About shows the device tier + facts**: open Settings → About on a real device.
  Pass: the "Device" section shows a tier (High/Standard/Limited) and RAM / CPU-core count /
  Android version / 64-bit values that match the device (sanity-checks the collector + the
  tier rule on real hardware; nothing else gates on the tier yet).

### Phase 5.5a — on-device provider scaffolding + model download

- [ ] **V128. Model download + integrity (on real hardware/network)**: Settings → Provider →
  Gemma (on-device) → Download. Pass: progress advances, completes, shows "Model ready ✓";
  the file lands in app storage; a deliberately corrupted/interrupted download is rejected
  (not marked ready) and cleaned up; Delete frees the space. Cancel mid-download stops it and
  leaves no partial file treated as ready.
- [ ] **V129. Tier gating**: on a LOW-tier device the Download button is disabled with the
  low-memory caution; MEDIUM shows the "may be slow" note; HIGH enables normally.
- [ ] **V130. On-device placeholder (until 5.5b)**: with the model downloaded, select Gemma
  (on-device) and start a task. Pass: the loop ends with the clear "inference lands in 5.5b"
  note (no crash); switching back to Claude/Gemini still runs normally. *(Superseded by V131
  once 5.5b is on the device.)*

### Phase 5.5b — on-device inference (LiteRT-LM)

- [ ] **V131. On-device task runs fully offline**: on a HIGH-tier arm64 device with the model
  downloaded, enable airplane mode (and clear cloud keys), select Gemma (on-device), run a
  simple task ("open Settings"). Pass: the engine initializes (a few seconds), tokens stream
  into the chat, the model emits a parseable `tool_call`, the tool executes, and the task
  completes — all with no network and no API key, no native-lib/R8 crash.
- [ ] **V132. On-device function-call format**: confirm Gemma 4 E2B actually emits tool calls
  in the fenced `tool_call` JSON form `GemmaToolPrompt` parses; if not, tune the render/parse
  format (and the unit tests) to the model's real output. Confirm Stop cancels mid-generation.

### Phase 5.6 — adaptive perception

- [ ] **V133. Text-only mode works (any provider)**: Settings → turn "Send screenshots (vision)"
  off for a cloud provider, run a task. Pass: the agent completes it acting by element index/
  bounds (no image sent — confirm via the model's behavior / the Agent's-view panel showing the
  text-only placeholder); turn it back on → screenshots return.
- [ ] **V134. Toggle is per-provider and persists**: set Claude vision on, Gemma vision off (or
  vice-versa), restart the app → each provider keeps its own setting. (On-device default is
  vision-on; see V131.)

### Phase 5.7 — Privacy / Local-Only Mode

- [ ] **V135. Tier-gated availability**: on a `DeviceTier.LOW` device, the Local-only toggle is
  shown **disabled** with the "can't run the on-device model" explanation; on a MEDIUM/HIGH device
  it's actionable.
- [ ] **V136. Confirm + download when capable-but-not-downloaded**: on a capable device with no
  model, enabling Local-only shows the confirm dialog; confirming switches the provider to
  on-device and starts the ~2.6 GB download (progress in the on-device card / chat banner).
- [ ] **V137. Zero-egress operation**: with the model present and Local-only on, put the device in
  **airplane mode** and run a task → it completes fully on-device (proves no network dependency in
  the loop). Ties to V131 (on-device inference).
- [ ] **V138. Cloud is locked out**: with Local-only on, the provider picker's cloud options are
  disabled, the key Save/Test controls don't fire, and a started task uses the on-device provider
  (no `api.anthropic.com` / `generativelanguage.googleapis.com` egress). Saved cloud keys remain
  (turning Local-only off restores cloud access without re-entry).

## Results log

| Date | Device / Android version | Items run | Notes |
|---|---|---|---|
| _(fill in)_ | | | |
