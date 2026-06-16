package dev.openandroiduse.companion.agent

import org.json.JSONObject

/**
 * Phase 6.1: a human-readable, label-resolved summary of an agent action for the
 * consent sheet, the tool log/chips, and the transcript — "Tap 'Send'" instead of
 * "click [42]". Pure (snapshot + args in, string out) so it is unit-tested;
 * [ToolExecutor.describeAction] supplies the relevant app's most-recent snapshot.
 *
 * When an `element_index` resolves to a labelled element we name it; otherwise we
 * fall back to the index/coordinates so the summary is never blank or misleading.
 */
object ActionSummary {

    fun describe(snapshot: AppSnapshot?, name: String, args: JSONObject): String {
        val label = elementLabel(snapshot, args.optString("element_index"))
        return when (name) {
            "click" -> label ?: args.optString("element_index").ifBlank { coords(args) }
            "perform_secondary_action" -> label ?: "element ${args.optString("element_index")}"
            "set_value" -> {
                val target = label ?: "element ${args.optString("element_index")}"
                "$target = \"${args.optString("value").take(TEXT_MAX)}\""
            }
            "scroll" -> {
                val dir = args.optString("direction")
                if (label != null) "$dir in $label" else "$dir ×${args.optDouble("pages", 1.0)}"
            }
            "type_text" -> "\"${args.optString("text").take(TEXT_MAX)}\""
            "press_key" -> args.optString("key")
            "get_app_state", "list_apps" -> args.optString("app")
            else -> ""
        }
    }

    /** A quoted human label for [elementIndex] in [snapshot], or null to fall back. */
    private fun elementLabel(snapshot: AppSnapshot?, elementIndex: String): String? {
        if (elementIndex.isBlank()) return null
        val element = snapshot?.lookupElement(elementIndex) ?: return null
        val raw = element.name.ifBlank { element.value }
            .ifBlank { element.controlType }
            .ifBlank { element.resourceId }
            .trim()
        return raw.ifBlank { null }?.let { "'${it.take(LABEL_MAX)}'" }
    }

    private fun coords(args: JSONObject): String =
        "(${args.optDouble("x", 0.0).toInt()}, ${args.optDouble("y", 0.0).toInt()})"

    private const val LABEL_MAX = 40
    private const val TEXT_MAX = 40
}
