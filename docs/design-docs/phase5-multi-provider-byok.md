# Phase 5 — Multi-provider BYOK (Claude + Gemini)

> Language note: English-first, per `CLAUDE.md`. Design doc — captures direction
> ahead of implementation. No code lands with this doc beyond its own creation;
> Phase 5 is built in its own PR(s) after the Phase 4 UI foundation.

## Goal

Let users bring their own key for **more than one provider** and pick a model per
provider — starting with **Anthropic (Claude)** and **Google (Gemini)** — while
keeping the on-device, no-server, key-stays-local posture intact.

## Why not GenKit or the Vercel AI SDK

Both were considered and rejected on **platform**, not quality:

- The **Vercel AI SDK** is TypeScript/JavaScript and needs a Node runtime — it
  cannot run inside an Android/Kotlin app.
- **Genkit** is a JS/Go **backend orchestration** framework that expects a server
  in the loop.

Either would force a backend proxy between the phone and the model provider. That
breaks the product's spine: the agent runs **on the device**, and the user's API
key never leaves it except directly to the chosen provider. So the answer is
neither framework — we add a small abstraction in Kotlin and use the **official
first-party SDK per provider** (consistent with the existing "official SDK over
hand-rolled HTTP" decision, `docs/SUPPLY_CHAIN_SECURITY.md`).

## Design

### `AgentBackend` interface (provider-agnostic)

Introduce a Kotlin interface that captures one streaming, tool-using turn over
our **frozen 9-tool schema** (unchanged — schema parity with the desktop runtimes
is preserved):

```
interface AgentBackend {
    fun stream(
        messages: List<AgentMessage>,
        tools: List<ToolSpec>,      // the frozen 9 tools, provider-neutral
        model: String,
    ): Flow<BackendEvent>           // text | thinking | tool_call | stop | error
}
```

Refactor the current `agent/AgentController` so the **loop, safety gates
(touch-to-pause, confirmation, stop), the in-process 9-tool executor, screenshot
pruning, and narration** stay provider-independent. Everything provider-specific
— auth, wire format, streaming decode, tool-call mapping, prompt-cache semantics
— moves behind `AgentBackend`.

### Providers

- **`AnthropicBackend`** — keep `com.anthropic:anthropic-java:2.40.1` (the
  existing, working path); becomes one implementation of the interface.
- **`GeminiBackend`** — add the **official Google Gen AI Java SDK
  (`com.google.genai`)**, BYOK with the user's Gemini key. Map the 9 tools to
  Gemini **function declarations**, and translate Gemini's streamed parts +
  function calls into `BackendEvent`s.
- The interface is **provider-extensible** (OpenAI, etc., later) without
  committing to more now.

### Settings & state

Extend `agent/AgentSettings`:

- a selected **provider** (`anthropic` | `gemini`);
- a **per-provider API key**, each AES/GCM-encrypted with its own Android
  Keystore alias (reuse the existing `storeApiKey`/`loadApiKey` pattern, keyed by
  provider);
- a **per-provider model** + per-provider cached model list.

The settings and onboarding UI (Compose, from Phase 4) gain a provider toggle and
a model picker that switches lists by provider.

### Model lists are provider-driven

Model IDs are **fetched from each provider's models endpoint** (reusing the
agent-intelligence "models-API-driven list" backlog item), with a curated
fallback list per provider. **Exact Gemini model identifiers (e.g. a "Pro" and a
"Flash" tier) must be confirmed against Google's live models endpoint at build
time** — this design does not hardcode them (the assistant's knowledge cutoff is
Jan 2026 and cannot guarantee current strings).

### Supply chain & control surface

- Register `com.google.genai` (pinned exact version, License column, NOTICE entry
  if its `META-INF` is stripped like the Anthropic SDK) in
  `docs/SUPPLY_CHAIN_SECURITY.md` as the second sanctioned `agent`-package
  dependency.
- The **control surface stays dependency-free**; only the `agent` package may
  import provider SDKs.

### Tests

- JVM unit tests for each provider's **tool mapping** (9 tools → provider format)
  and event translation.
- Extend the keyless emulator smoke (`AgentLoopEmulatorTest`) to cover **backend
  selection** — a stub Gemini endpoint alongside the existing stub model server,
  asserting the loop drives either backend identically.

## Sequencing

After the Phase 4 UI foundation (PR-A), because the provider/model picker is a
settings surface that benefits from the Compose foundation. The `AgentBackend`
refactor itself can begin independently of the UI.

## Out of scope

Providers beyond Claude + Gemini; routing/fallback between providers; per-task
provider switching mid-conversation. Revisit once the two-provider path ships.
