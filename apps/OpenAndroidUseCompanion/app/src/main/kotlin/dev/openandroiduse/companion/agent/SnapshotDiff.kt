package dev.openandroiduse.companion.agent

/**
 * Phase 6.3b: a concise, model-facing summary of how the screen changed after an
 * action — "Screen unchanged." or "Screen changed: window → 'Settings'; focus →
 * the Email field; +2 elements." Prepended to the action's tool-result text so the
 * model can tell whether its action had any effect (and stop repeating no-ops)
 * instead of inferring it from a full re-parse. Pure (snapshots in, summary out) so
 * it is unit-tested; [ToolExecutor.settleAndSnapshot] supplies the before/after pair.
 */
object SnapshotDiff {

    data class Result(val changed: Boolean, val summary: String)

    fun summarize(previous: AppSnapshot?, current: AppSnapshot): Result {
        // First action of a session: nothing to compare against.
        if (previous == null) return Result(changed = true, summary = "")

        val parts = mutableListOf<String>()
        if (previous.packageName != current.packageName || previous.windowTitle != current.windowTitle) {
            val where = current.windowTitle.trim()
                .ifEmpty { current.appName.ifEmpty { current.packageName } }
            parts.add("window → '${where.take(MAX)}'")
        }
        val delta = current.elements.size - previous.elements.size
        if (delta != 0) parts.add(if (delta > 0) "+$delta elements" else "$delta elements")
        if (previous.focusedSummary != current.focusedSummary && current.focusedSummary.isNotBlank()) {
            parts.add("focus → ${current.focusedSummary.take(MAX)}")
        }

        val changed = previous.treeLines != current.treeLines || parts.isNotEmpty()
        if (!changed) return Result(changed = false, summary = "Screen unchanged.")
        val detail = if (parts.isEmpty()) "" else ": " + parts.joinToString("; ")
        return Result(changed = true, summary = "Screen changed$detail.")
    }

    private const val MAX = 40
}
