package dev.openandroiduse.companion.agent

/**
 * Phase 6.5c-5 (L4 risk-adaptive confirmation): a pure classifier for *irreversible /
 * externally-visible* actions (send, pay, delete, post…). The agent loop confirms a
 * high-risk action with a one-tap prompt **even under a per-app trust grant** — a grant
 * relaxes the *sensitive-screen* gate, never the *risk* gate. This is the mobile-specific
 * "action-surface → risk-gated confirmation" consent model the GUI-agent safety literature
 * identifies as unclaimed (R-Judge / uncertainty motivation; spec §5).
 *
 * Keys on the resolved action label (`ActionSummary` → "Tap 'Send'") for the buttons that
 * trigger an effect — taps and key presses. Tight verb set so routine navigation isn't gated.
 */
object RiskClassifier {

    private val TRIGGER_TOOLS = setOf("click", "perform_secondary_action", "press_key")

    // Irreversible / externally-visible verbs. Word-boundaried so "sender" / "deleted items"
    // headers don't trip it; tight enough that ordinary navigation (Back, Next, Open) is free.
    private val RISKY = Regex(
        "\\b(send|pay|buy|purchase|order|checkout|delete|remove|discard|post|publish|" +
            "share|transfer|withdraw|unsubscribe|deactivate|uninstall)\\b",
    )

    /** True when [actionLabel] (a resolved action summary) is an irreversible/external action. */
    fun isHighRisk(toolName: String, actionLabel: String): Boolean {
        if (toolName !in TRIGGER_TOOLS) return false
        return RISKY.containsMatchIn(actionLabel.lowercase())
    }
}
