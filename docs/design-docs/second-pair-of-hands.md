# Second Pair of Hands — product vision for open-android-use

## One sentence

Your phone gains a second pair of hands: an AI operator that sees the screen the way
you do, acts on it the way you would, and works *with* you on the device — not a
chatbot bolted onto an app, but a co-operator of the whole phone.

## Why Android, why now

Desktop computer-use agents (including the upstream of this fork) proved the loop:
accessibility tree + screenshot in, semantic UI actions out. But the device people
actually live on is the phone, and Android is the only mainstream OS that exposes a
real accessibility/automation surface (`AccessibilityService`, `UiAutomation`, ADB)
deep enough to build on without rooting. Nobody has shipped the robust, open,
protocol-first version of this. That is the gap `open-android-use` fills.

## Product principles

1. **Second pair of hands, not autopilot.** The human stays in the loop and in
   control. The agent narrates intent, asks before irreversible actions (send,
   pay, delete, post), and is interruptible at any moment. Synergy over substitution.
2. **Protocol first.** The product is an open MCP surface — the same 9 Computer Use
   tools the macOS/Windows/Linux runtimes expose — so *any* agent runtime (Claude
   Code, Qwen Code, Codex, custom loops) can drive a phone today. The app is a host
   for that protocol, not a silo.
3. **Semantic before pixels.** Prefer the accessibility tree and element-targeted
   actions; coordinates are the fallback, never the default. This is what makes runs
   reproducible and safe.
4. **Honest failure.** When Android can't do something cleanly over the current
   transport (non-ASCII typing over ADB, secure surfaces), say so loudly with the
   path forward — never silently degrade.
5. **Local and private by default.** Screenshots and trees go to the model the user
   configured, and nowhere else. No telemetry without opt-in.

## The three phases

### Phase 1 — Android Bridge (shipping first)

`apps/OpenAndroidUse`: a host-side Go binary, same shape as the Windows/Linux
runtimes. Drives any ADB-connected device or emulator:

- state: `uiautomator dump` accessibility snapshot + `screencap` screenshot,
  rendered in the exact format the other runtimes use;
- actions: `input` tap / swipe / drag-and-drop / keyevents, long-press mapped to
  `mouse_button: right`, Android keys (`Back`, `Menu`) reachable through `press_key`;
- distribution: single binary + the existing npm machinery later.

Who it serves: developers and agent runtimes — the people who make the paradigm real.
It is also the permanent test harness for everything that follows.

### Phase 2 — On-device companion ("the hands move in")

A Kotlin app: `AccessibilityService` for the live tree and actions, MediaProjection
for capture, a local gRPC/socket endpoint the bridge (or a Wi-Fi peer) speaks to.
Removes the cable, fixes text input (real IME injection, full Unicode), survives app
switches, and adds the consent UI: per-app permissions, action confirmation sheets,
a big visible "hands off" kill switch, and an on-screen gesture trail so the user
always sees what the second pair of hands is doing.

### Phase 3 — The union ("the product")

The agent loop moves onto the device: chat + voice front end, model API of the
user's choice, task memory, and human-AI handoff as a first-class interaction —
either side can pick up where the other left off mid-task. The phone becomes a
shared workspace: you do the judgment, it does the legwork.

## What "world-class" means here (quality bar)

- Tool surface byte-compatible with the desktop runtimes (one skill works on four
  platforms).
- Every action either succeeds verifiably (fresh post-action snapshot returned) or
  fails with an error a model can act on.
- Coordinate mapping pinned by tests; no drift between what the model sees and
  where the tap lands.
- Repo conventions: every change lands with docs, tests, and a history record in
  the same commit series (see `AGENTS.md`).

## Non-goals

- iOS (no public automation surface; revisit if that changes).
- Root-required capabilities.
- Detection-evasion of any kind (banking app overlays, bot-detection workarounds);
  we operate through the OS's sanctioned accessibility surface, visibly.
