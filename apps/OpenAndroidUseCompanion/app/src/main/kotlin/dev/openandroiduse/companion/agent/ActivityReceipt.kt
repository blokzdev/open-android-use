package dev.openandroiduse.companion.agent

/**
 * Phase 6.5c-5b (transparency): turns a per-run tally of executed tools into a one-line
 * "activity receipt" the agent posts in chat when a task ends — "3 taps · 1 scroll · 1 text entry".
 * Pure (no Android types) so it is unit-tested. Reads (list_apps/get_app_state) are excluded — the
 * receipt is about what the agent *did*, not what it looked at.
 */
object ActivityReceipt {

    private class Bucket(val tools: Set<String>, val one: String, val many: String)

    // Ordered for stable output; irregular plurals are spelled out, not naively suffixed.
    private val BUCKETS = listOf(
        Bucket(setOf("click", "perform_secondary_action"), "tap", "taps"),
        Bucket(setOf("scroll"), "scroll", "scrolls"),
        Bucket(setOf("drag"), "swipe", "swipes"),
        Bucket(setOf("type_text", "set_value"), "text entry", "text entries"),
        Bucket(setOf("press_key"), "key press", "key presses"),
    )

    /** A receipt summary for [tally] (tool name → count), or null when no action ran. */
    fun summarize(tally: Map<String, Int>): String? {
        val parts = mutableListOf<String>()
        for (bucket in BUCKETS) {
            val count = bucket.tools.sumOf { tally[it] ?: 0 }
            if (count > 0) parts.add("$count ${if (count == 1) bucket.one else bucket.many}")
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}
