# Agent Security & Trust Architecture

> Language note: English-first, per `CLAUDE.md`. This is a **durable reference** — the security and
> trust model for an AI agent that operates a user's device through the accessibility surface. It is
> written to outlive any single phase and to be reusable by other projects and contributors. It is
> introduced by Phase 6.5c (`docs/exec-plans/active/20260616-phase6-world-class.md`); the code that
> realizes each layer lands in the 6.5c-1..-5 PRs. Where a layer is not yet built, this doc says so.

## Status

| Layer | State |
| --- | --- |
| Cardinal rule: secrets never enter the model loop | **Policy adopted** (enforced by redaction + handoff once 6.5c-1/-2 land) |
| L0 Privacy redaction (sensitive-field values, screenshot) | Specified — 6.5c-1 |
| L1 Human handoff / takeover (+ login tap-to-fill) | Specified — 6.5c-2 |
| L2 Scoped, revocable, decaying per-app trust + audit | Specified — 6.5c-3 |
| L3 Untrusted-content isolation / spotlighting + injection-signal classifier | Specified — 6.5c-4 |
| L4 Risk-adaptive confirmation + trust transparency | Specified — 6.5c-5 |
| Provider model hardening | **Relied upon** (Claude/Gemini); weaker on-device (Gemma) — documented limitation |
| Full CaMeL (provable data-flow control) | **Principles adopted; full system deferred** — see §7 + `docs/BACKLOG.md` |

## 1. Why this exists — the threat model

A "second pair of hands" agent reads the screen (accessibility tree + screenshot) and acts on it
(tap / type / scroll / swipe / keys). That capability *is* the threat surface:

- **Indirect / environmental prompt injection.** The screen contains untrusted content — ads,
  pop-ups, notifications, web/user-generated content — that an LLM cannot reliably distinguish from
  the user's instructions (everything is just tokens). 2024–2026 benchmarks show mobile/GUI agents
  are hijacked by injected on-screen content **~36–42% of the time** in real Android apps, and *every*
  evaluated agent is vulnerable (MobileSafetyBench, AgentHazard, MIRAGE, the pop-up attack; §11).
- **The "lethal trifecta"** (Willison): private data **+** exposure to untrusted content **+** an
  exfiltration channel. A screen-driving agent already has the latter two by construction. Adding the
  user's **secret** to its context lights the third leg — and a leaked password or card number is
  **non-recoverable**.
- **Excessive agency / over-permissioning** (OWASP LLM06). The agent collapses natural-language
  instructions, tool execution, and (if we let it) credentials into one trust domain with no OS-level
  permission boundary.
- **The accessibility API is a top Android-malware vector**, and the platform is actively narrowing
  it (Android 14 `accessibilityDataSensitive`, the Dec-2025 anti-malware changes, the A16/17
  restrictions on non-accessibility apps touching the API). An honest automation agent must operate
  *visibly and within policy*, and must not behave like the credential-harvesting pattern these
  changes target. This also aligns with the product non-goal "no detection-evasion of any kind"
  (`docs/design-docs/second-pair-of-hands.md`).

## 2. The cardinal rule — secrets never enter the model loop

**The agent never receives, types, or transmits a user secret (password, credit-card number, and
comparable high-stakes non-recoverable values).** This is a *policy boundary*, not a confidence
threshold, because the research is unanimous that no amount of model hardening makes a secret-in-the-loop
safe:

- Anthropic's hardened browser agent still has a ~1% residual injection rate — "meaningful risk … prompt
  injection is far from a solved problem."
- Probabilistic classifiers (88–99% block) are a *failing grade* against an adaptive attacker for an
  **irreversible** action: the attacker retries until the small miss rate hits, and one success is
  permanent compromise.
- Even CaMeL's *provable* data-flow control doesn't rescue this: when the task itself is "type the
  secret into this field," the flow is user-authorized, and the model still cannot reliably verify the
  field is the legitimate (un-spoofed, un-overlaid) destination.

So secrets are kept **outside** the model's trust boundary and supplied by the **human or the OS**
(§6). Every credible source — OpenAI Operator, Anthropic computer use, Google Mariner, Claude Code —
converges on the same handoff pattern for logins and payments.

> **Important platform caveat we design around:** we do **not** rely on the framework to mask secrets
> for us. Password-field text masking to an accessibility service is **version- and capability-dependent,
> not a guaranteed invariant**, and ordinary fields (e.g. a credit-card `EditText`) are *not* masked at
> all. We therefore redact secret values ourselves (L0). Likewise, `FLAG_SECURE` blocks *pixels*
> (screenshots) but **not** the accessibility tree, and there is no public `isSecure()` API for a
> service to even know a window is secure — so screenshot suppression must be driven by our own
> sensitivity inference, not by the OS flag.

## 3. The layered defense model

Defense-in-depth: no single layer is trusted to be sufficient. For each layer we state honestly whether
we **build** it, **rely** on someone else for it, or **defer** it.

| # | Layer | What it does | Build / Rely / Defer |
| --- | --- | --- | --- |
| L0 | **Privacy redaction** | Secret field values never reach the model in tree text or screenshot | **Build** (6.5c-1) |
| L1 | **Human handoff / takeover** | At a secret field, pause and let the human (or OS chip) supply it | **Build** (6.5c-2) |
| L2 | **Scoped least-privilege trust** | Default-deny per-app grants: one-time / session / persistent, revocable, decaying; audited | **Build** (6.5c-3) |
| L3 | **Untrusted-content isolation + injection classifier** | Screen text enters only as `tool_result` data with provenance; a v1 signal flags injection-like content | **Build** (6.5c-4) |
| L4 | **Risk-adaptive confirmation + transparency** | High-risk/injection-suspected actions confirm even under a grant; the user can see/limit what the agent does | **Build** (6.5c-5) |
| — | **Model hardening** | Adversarial training so the model resists injection | **Rely** on provider (Claude/Gemini); on-device Gemma is weaker — a documented tier limitation |
| — | **Provable data-flow control (CaMeL)** | Untrusted data structurally cannot drive control flow | **Principles adopted**; full system **deferred** (§7) |

## 4. The structural enforcement invariant

The cardinal rule (§2) is enforced by **three layers, of which only the outermost is grant-aware**.
This is what makes "a *trusted* app still hands off secrets" true **by construction**, not by a
code comment that a future edit could quietly violate:

1. **Redaction** — in `SnapshotBuilder` emission and the `ElementRecord` data-model boundary. *Never*
   consulted with the grant set; a grant can never un-redact a secret value.
2. **Element-level secret-targeting handoff** — in the `ToolExecutor` decision seam. A tool that targets
   a `password`/`creditCard` element (or the focused field for `type_text`) **always** hands off, even in
   a fully trusted app. *Never* grant-bypassable.
3. **Screen-level action gate** — in `AgentController`. The **only** layer that consults `activeGrants`:
   a grant unblocks acting on the *non-secret* controls of a screen that merely *contains* a sensitive
   field. (6.5c-5 adds a parallel **risk gate** that a grant likewise cannot bypass.)

```
snapshot ─▶ [L0 redact: always] ─▶ model never sees secret values
action  ─▶ [element-level: targets a secret? → handoff, always]
        ─▶ [risk: high-risk / injection-flagged? → confirm, always]      (6.5c-5)
        ─▶ [screen-level: sensitive screen? → grant? execute+audit : handoff]
```

## 5. Sensitive-screen trust model

- **Detection** (`SensitiveScreenDetector`, pure): authoritative `isPassword` ∪ a tight card-token label
  heuristic (autofill hints aren't exposed to accessibility), extended (6.5c-1) to honor OS-withheld
  (`accessibilityDataSensitive`) nodes and, measured, OTP/2FA `inputType` signals — all with near-zero
  false-positive discipline and unit tests.
- **Redaction** (L0): on a sensitive screen, secret field *values* are replaced with `[redacted]` at
  emission (covering both the on-device agent and the host bridge / external MCP runtimes), while
  structure (control type, resource id, bounds, actions) is preserved so the agent still understands the
  screen. The screenshot is withheld in vision mode with a synthetic note explaining why.
- **Handoff** (L1): the gate's dead-end refusal becomes an in-flow takeover overlay — "🔒 you take it
  from here" — reusing the accessibility-overlay + latch pattern; the human enters the secret, the agent
  resumes on Continue. Auth-aware copy distinguishes login from payment.
- **Scoped trust** (L2): default-deny. From the handoff the user may grant *once* / *this session* /
  (effortfully, risk-framed) *always for this app*. Grants are revocable and **decay** when unused
  (Android auto-reset precedent), surfaced in a Privacy "Trusted apps" list, with a text-only audit log.
- **Risk-adaptive confirmation** (L4 / 6.5c-5): even under a grant, an irreversible (send/pay/delete/post)
  or injection-suspected action requires a one-tap confirm. This is the mobile-specific
  *action-surface → risk-gated confirmation* consent model the literature identifies as unclaimed.

## 6. OS-mediated credential entry (login "tap-to-fill")

The agent can *assist* a login without ever seeing the secret: it navigates to the screen and **focuses
the credential field**, which causes the system to surface the user's autofill / password-manager chip;
the user taps it and the secret flows **manager → field**, brokered by the OS. The agent only emits
focus/tap actions and **never reads the field contents**.

Honest scoping:

- The security guarantee is **identical to plain human handoff** — it's "handoff with a nicer tap," not a
  categorical upgrade. Human handoff remains the **universal fallback**.
- `AutofillManager.requestAutofill` is an **in-app** API (the owning app calls it) — an external service
  cannot remote-control it; the realistic cross-app mechanism is *focus the field, let the OS chip appear*.
- **Credential Manager / passkeys** are **app-integrated** — a generic agent cannot summon them for
  another app. We benefit when present; we never drive them.
- This sits in the **most heavily scrutinized, actively tightening** corner of Play policy. It is gated
  behind explicit consent + prominent disclosure, must never read/log/transmit field contents, and is
  **not load-bearing** — the product is fully functional on human handoff alone if the platform narrows
  this path further.

## 7. CaMeL — principles adopted, full system deferred

CaMeL ("Defeating Prompt Injections by Design," arXiv 2503.18813) is a system *around* the LLM that
extracts control/data flow from the trusted query so untrusted data can never influence the program flow,
attaching capabilities to values and enforcing policies at tool-call time. It offers *provable* properties
— at a real cost (~77% of tasks solved vs ~84% undefended; it depends on correct, hand-specified policies).

We **adopt its principles** now, sized to a mobile 9-tool agent:

- **Control/data separation** — the user's instruction is the trusted control; on-screen text is untrusted
  data that never carries instructions (L3 isolation/spotlighting).
- **Least-privilege capabilities** — our scoped, decaying per-app grants (L2) *are* the capability layer:
  authority is per-app, time-boxed, and revocable, not ambient.
- **Policy enforced at the action boundary** — the structural gates of §4 are policy checks at tool-call
  time.

We **defer the full system** (a dual-LLM split with formal capability tracking and a policy engine):
it would fight the frozen 9-tool schema and the on-device tier and gut utility for marginal gain over the
layered defenses above. Recorded as a future direction in `docs/BACKLOG.md`.

## 8. What we rely on the provider for

**Model hardening** (adversarial training against injection) is the provider's responsibility — Claude and
Gemini both invest in it, and we benefit without building it. The **on-device** tier (Gemma via LiteRT-LM)
is materially weaker here; this is a documented limitation, and it raises the value of the *structural*
layers (L0–L2, L4) that do not depend on the model resisting injection. Local-Only Mode trades cloud model
hardening for zero egress — an honest, user-chosen tradeoff documented in `docs/SECURITY.md`.

## 9. Boundaries

- **External MCP runtimes** driving the host-side Go bridge benefit from L0 redaction (it is emission-side,
  topology-agnostic) but do **not** get the on-device consent UI (handoff/grants/confirmation), which is
  owned by the companion app. A headless host runtime is responsible for its own human-in-the-loop policy;
  the bridge guarantees it never *types* a value the model didn't supply and that secret values are redacted
  out of snapshots.
- **The 9-tool Computer Use schema stays frozen.** Every gain here is execution-/perception-side or UI —
  no new tools.

## 10. How the layers map to the 6.5c PRs

6.5c-0 (this doc + doc alignment) → 6.5c-1 (L0 redaction) → 6.5c-2 (L1 handoff + tap-to-fill) →
6.5c-3 (L2 scoped trust + audit) → 6.5c-4 (L3 isolation + injection classifier) →
6.5c-5 (L4 risk-adaptive confirm + transparency). See the exec-plan for status.

## 11. References (primary sources, verified 2026-06)

- **Industry handoff:** OpenAI Operator; Anthropic computer use docs; Google Project Mariner; Claude Code
  permission model.
- **Least-privilege / consent:** NIST SP 800-207 Zero Trust (per-session, least-privilege, dynamic policy)
  — https://csrc.nist.gov/pubs/sp/800/207/final ; Android one-time permissions + auto-reset/decay
  (Android 11; Dec-2021 backport) — https://developer.android.com/about/versions/11/privacy/permissions ;
  GDPR Art. 4(11)/7(3) (specific, revocable consent).
- **Injection (unsolved) + secrets out of the loop:** Google "layered defense" —
  https://blog.google/security/mitigating-prompt-injection-attacks/ and DeepMind Gemini security paper;
  CaMeL — https://arxiv.org/abs/2503.18813 ; Anthropic browser-use defenses —
  https://www.anthropic.com/research/prompt-injection-defenses and "Mitigate jailbreaks and prompt
  injections" docs; Willison lethal-trifecta — https://simonwillison.net/tags/lethal-trifecta/ ;
  OWASP LLM01:2025 + LLM06:2025 — https://genai.owasp.org/llmrisk/llm01-prompt-injection/ .
- **Android secrecy surface:** `AccessibilityNodeInfo.isPassword` / `setAccessibilityDataSensitive` (API 34)
  / `AccessibilityServiceInfo.isAccessibilityTool` ; `WindowManager.LayoutParams.FLAG_SECURE` (pixel-only);
  no a11y `isSecure()` accessor (verified against AOSP `AccessibilityWindowInfo`).
- **Accessibility tightening:** Android Developers Blog "Enhancing Android security" (Dec 2025); A16/17
  restrictions on non-accessibility apps using the API.
- **GUI-agent safety literature:** MobileSafetyBench (arXiv 2410.17520), AgentHazard (2507.04227), R-Judge
  (2401.10019), pop-up attack (2411.02391), MIRAGE, InjecAgent (2403.02691), OS-Sentinel (2510.24411),
  "Towards Automating Data Access Permissions in AI Agents" (2511.17959). Open whitespace: no mature
  *mobile-specific* consent model tying the action surface to risk-gated confirmation — 6.5c-5 claims it.
- **OS-mediated credentials:** Android Autofill framework —
  https://developer.android.com/identity/autofill/autofill-services ; Credential Manager (app-integrated) —
  https://developer.android.com/identity/credential-manager ; Play AccessibilityService policy —
  https://support.google.com/googleplay/android-developer/answer/10964491 .
