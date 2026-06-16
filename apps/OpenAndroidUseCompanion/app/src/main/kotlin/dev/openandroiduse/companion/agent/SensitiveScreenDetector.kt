package dev.openandroiduse.companion.agent

/**
 * Phase 6.5a: detects screens the agent must not *act on* so secrets are never
 * typed into, tapped, or otherwise manipulated by the operator. Pure (snapshot in,
 * verdict out) so it is unit-tested; [ToolExecutor.callTool] gates the seven action
 * tools on it while leaving reads (list_apps/get_app_state) untouched, so the agent
 * can still perceive and narrate and ask the human to handle the sensitive entry.
 *
 * 6.5a keyed on the framework's authoritative AccessibilityNodeInfo.isPassword flag
 * (carried through [ElementRecord.password]). 6.5b adds [ElementRecord.creditCard], a
 * payment-field signal computed by SnapshotBuilder from the node's labels (autofill
 * hints aren't exposed to accessibility) against a tight, card-specific token set —
 * not bare substring matching ("card" matches CardView/"Discard" on most screens).
 * Whether this guard is enforced is the caller's choice (a user toggle); the detector
 * stays pure so it is unit-tested and remains the single sensitivity chokepoint.
 */
object SensitiveScreenDetector {

    /** The model-facing reason a sensitive screen was declined. */
    const val REASON_SENSITIVE =
        "This screen has a password or payment field. For your security I won't " +
            "type into or tap it — please enter that yourself, then tell me to continue."

    /** True when [snapshot] contains a credential or payment field the agent must not act on. */
    fun isSensitive(snapshot: AppSnapshot?): Boolean {
        if (snapshot == null) return false
        return snapshot.elements.any { it.password || it.creditCard }
    }
}
