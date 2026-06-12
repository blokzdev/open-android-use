package dev.openandroiduse.companion.agent

/**
 * Touch-to-pause (Phase 3.1b, docs/design-docs/phase3-agent-loop.md): any user
 * touch suspends the agent. Accessibility services cannot tell injected
 * gestures from fingers directly, so the heuristic is temporal: ToolExecutor
 * stamps every agent-initiated gesture, and an interaction event that arrives
 * outside the stamp window — and not from the companion's own UI — is treated
 * as the user reaching for the screen.
 *
 * Pure logic, no Android types, so the decision table is unit-testable.
 */
object TouchPauseMonitor {

    /**
     * How long after an agent gesture its ripple of events is still "ours".
     * Generous because slow devices dispatch the agent's own click/text events
     * well after the action returns; false pauses are worse than a slightly
     * late real one.
     */
    const val AGENT_ACTION_WINDOW_MS = 4_000L

    @Volatile
    private var lastAgentActionAtMs = 0L

    /** Called by ToolExecutor around every gesture/text action it performs. */
    fun noteAgentAction(nowMs: Long = System.currentTimeMillis()) {
        lastAgentActionAtMs = nowMs
    }

    /**
     * Opens a fresh window at task start. Stamping "now" (not 0) prevents a
     * stray event between Send and the first gesture from instantly pausing.
     */
    fun reset(nowMs: Long = System.currentTimeMillis()) {
        lastAgentActionAtMs = nowMs
    }

    /**
     * Decides whether an interaction event should pause the agent.
     *
     * @param agentRunning whether a task is currently executing
     * @param eventPackage the package that emitted the accessibility event
     * @param ownPackage the companion's own package (its UI is exempt — the
     *   Stop button must not race itself)
     */
    fun shouldPause(
        agentRunning: Boolean,
        eventPackage: String?,
        ownPackage: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!agentRunning) return false
        if (eventPackage == null || eventPackage == ownPackage) return false
        return nowMs - lastAgentActionAtMs > AGENT_ACTION_WINDOW_MS
    }
}
