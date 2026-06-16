package dev.openandroiduse.companion.agent

/**
 * Phase 6.5c-1 (L0 privacy redaction): the single policy that keeps a sensitive
 * field's *value* out of everything the model sees, while preserving the screen's
 * *structure* so the agent can still understand the screen and hand off to the human.
 *
 * Pure (no Android types) so it is unit-tested and can be the one place that decides
 * what "redacted" means. Used at two layers (see the security spec
 * `docs/design-docs/agent-security-trust-architecture.md`):
 *
 *  1. **Emission** (`SnapshotBuilder.nodeJson`): the load-bearing chokepoint. A
 *     flagged node's `text` is replaced with [PLACEHOLDER] before it is serialized,
 *     so neither the on-device flattener nor the host-side Go bridge (which reads the
 *     same protocol JSON) ever receives the value. This is why a card `EditText`'s
 *     typed digits — which the framework does *not* mask, unlike password fields —
 *     no longer leak through the rendered tree.
 *  2. **Data-model boundary** (`SnapshotFlattener.walk` → `ElementRecord.value`): defense
 *     in depth so a flagged element's `value` can never be a raw secret, even if some
 *     path built the record from un-redacted JSON. The rendered tree-line text stays
 *     byte-aligned with the Go bridge (which has no flatten-level redaction), because the
 *     line's name derives from the already-redacted emission `text`, not from `value`.
 *
 * Redaction is never grant-aware: a per-app trust grant (6.5c-3) can relax the action
 * gate, but it can never un-redact a secret value.
 */
object Redaction {

    /** The marker shown in place of a secret field's value. */
    const val PLACEHOLDER = "[redacted]"

    /** Appended to a tool result when the screenshot was withheld on a sensitive screen. */
    const val SCREENSHOT_WITHHELD_NOTE =
        "(screenshot withheld: this screen has a password or payment field, so its " +
            "contents are hidden from you — use the element labels and ask the user to enter the secret)"

    /** True when a node's value must be masked (a password or payment field). */
    fun shouldRedact(password: Boolean, creditCard: Boolean): Boolean = password || creditCard

    /**
     * The value to emit for a node's `text`: the original when not sensitive, [PLACEHOLDER]
     * when sensitive and there is a value to hide, or null when there is nothing to emit
     * (so emission stays "only when non-empty", matching `putIfNotEmpty`).
     */
    fun emittedText(password: Boolean, creditCard: Boolean, rawText: String?): String? {
        if (rawText.isNullOrEmpty()) return null
        return if (shouldRedact(password, creditCard)) PLACEHOLDER else rawText
    }

    /**
     * The value to store on a flagged [dev.openandroiduse.companion.agent.ElementRecord]:
     * [PLACEHOLDER] when sensitive, the original otherwise. Belt-and-suspenders for the
     * data-model boundary; does not affect the rendered tree line.
     */
    fun redactedValue(password: Boolean, creditCard: Boolean, rawValue: String): String =
        if (shouldRedact(password, creditCard)) PLACEHOLDER else rawValue
}
