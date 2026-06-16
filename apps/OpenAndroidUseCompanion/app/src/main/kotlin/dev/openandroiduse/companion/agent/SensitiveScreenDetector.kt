package dev.openandroiduse.companion.agent

/**
 * Phase 6.5a: detects screens the agent must not *act on* so secrets are never
 * typed into, tapped, or otherwise manipulated by the operator. Pure (snapshot in,
 * verdict out) so it is unit-tested; [ToolExecutor.callTool] gates the seven action
 * tools on it while leaving reads (list_apps/get_app_state) untouched, so the agent
 * can still perceive and narrate and ask the human to handle the sensitive entry.
 *
 * 6.5a keys solely on the framework's authoritative AccessibilityNodeInfo.isPassword
 * flag (carried through [ElementRecord.password]) — near-zero false positives.
 * Substring matching on labels/resource-ids is deliberately avoided ("card" matches
 * CardView/"Discard" on most screens). Payment detection via autofill hints is 6.5b;
 * add those signals here so the gate stays a single chokepoint.
 */
object SensitiveScreenDetector {

    /** The model-facing reason a credential screen was declined. */
    const val REASON_PASSWORD =
        "This screen has a password or credential field. For your security I won't " +
            "type into or tap it — please enter that yourself, then tell me to continue."

    /** True when [snapshot] contains a credential field the agent must not act on. */
    fun isSensitive(snapshot: AppSnapshot?): Boolean {
        if (snapshot == null) return false
        return snapshot.elements.any { it.password }
    }
}
