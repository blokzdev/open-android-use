# Phase 3 — the agent loop ("Second Pair of Hands" proper)

Status: design draft. Phases 1–2 (bridge + companion) are the hands; Phase 3 is
the mind and the relationship. This doc fixes the architecture direction so the
next execution plan can slice it.

## What Phase 3 is

A user-facing experience where the agent and the human co-operate the phone:

- The user talks (chat or voice) to an agent that can see and act on the device.
- The agent narrates intent before acting ("I'll open Settings and turn off
  auto-rotate"), acts visibly, and stops the moment the user touches the screen.
- Either side can hand off mid-task: the user does the judgment step (a login,
  a CAPTCHA, a choice), the agent resumes the legwork.

## Architecture decision: host-first, device-native second

Two candidate topologies:

1. **Host loop (ship first)** — the agent runtime runs on a computer (Claude
   Code, or any MCP host) using the existing bridge + companion. Zero new
   trust surface, works today; the skill (`skills/open-android-use`) is already
   the operating manual. Phase 3.0 is *experience polish* on this path:
   narration conventions, interruption, handoff.
2. **On-device loop (the product)** — the companion app grows a chat/voice UI
   and calls a model API directly (user-supplied key; Anthropic first). The
   phone is then self-contained: no computer, no cable.

Decision: build 3.0 on the host loop (cheap, validates the interaction design),
then move the loop on-device (3.1) reusing the same tool surface the companion
already exposes locally — the protocol-v1 endpoint becomes an in-process call.

## Interaction model (applies to both topologies)

- **Narrate-then-act**: every action batch is preceded by one line of intent.
  No silent multi-step runs longer than one screen transition.
- **Touch-to-pause**: in the on-device loop, any user touch suspends the agent
  (the accessibility service sees user-initiated events); the agent re-snapshots
  and asks before resuming. On the host loop this is approximated by
  re-snapshotting before every action (already the runtime's behavior).
- **Consent ladder**: read/navigate freely → confirm before externally visible
  actions (send, post, purchase, delete, call) → never touch credentials or
  secure surfaces uninvited. Already encoded in the skill; in 3.1 it becomes a
  visible confirmation sheet on the phone, not just a model rule.
- **Visible hands**: 3.1 draws a gesture trail/cursor overlay (the desktop
  runtime's software-cursor heritage, reborn as a touch indicator) so the user
  always sees what the agent is doing.
- **Kill switch stays physical**: disabling the accessibility service remains
  the hard stop; the UI gets a one-tap pause as the soft stop.

## On-device loop (3.1) component sketch

- Companion app gains: a conversation Activity (chat first, voice later via
  SpeechRecognizer/TTS), a task runner service holding the agent loop, and a
  model client (Anthropic Messages API, streaming, tool use) — keys stored in
  Android Keystore, never leaving the device except to the model API.
- The 9-tool surface is reused verbatim as the model's tool definitions
  (`ToolDefinitions` already exist in the Go bridge; port the JSON schemas).
  Snapshots/actions call SnapshotBuilder/ActionExecutor in-process.
- Screenshot budget logic ports from the bridge (`image.go`) to keep payloads
  model-friendly.
- Memory: per-task transcript only at first; durable user preferences are a
  later, explicitly-opt-in feature.

## Non-goals for 3.x

- No autonomous background operation while the screen is off.
- No telemetry; no server component of ours in the loop.
- No credential autofill — the human does logins (that's the handoff feature
  working as intended, not a limitation to engineer away).

## Milestones

1. **3.0 host-loop polish**: narration + consent conventions exercised through
   Claude Code against real hardware (after `VERIFICATION.md` passes); capture
   transcripts as artifacts; tune the skill from observed failure modes.
2. **3.1a chat loop on device**: conversation UI + Anthropic client + in-process
   tools, debug builds only.
3. **3.1b safety surfaces**: confirmation sheets, touch-to-pause, gesture trail.
4. **3.1c voice**: push-to-talk first; wake word out of scope.
