package dev.openandroiduse.companion.agent

/**
 * Renders a conversation transcript to a self-contained Markdown document for
 * "export/share the whole conversation" (Phase 4.5). Pure and Android-free so it
 * unit-tests on the JVM; the Activity writes the result to a cache file and
 * shares it via FileProvider.
 *
 * The transcript is the chat UI's source of truth — a list of (kind, text)
 * pairs using [AgentController]'s KIND_* constants. Screenshots are never part
 * of the transcript (they are in-memory only), so the export is text-only by
 * construction, matching the privacy story.
 */
object ConversationExport {

    /**
     * @param title human-readable session title (auto-derived from the first
     *   prompt); blank falls back to a generic heading.
     * @param messages the transcript as (kind, text) pairs.
     */
    fun toMarkdown(title: String, messages: List<Pair<String, String>>): String {
        val heading = title.trim().ifEmpty { "Conversation" }
        val out = StringBuilder()
        out.append("# ").append(heading).append("\n\n")
        out.append("_Exported from Open Android Use Companion._\n")
        for ((kind, raw) in messages) {
            val text = raw.trim()
            if (text.isEmpty()) continue
            out.append('\n')
            when (kind) {
                AgentController.KIND_USER -> out.append("### You\n\n").append(text).append('\n')
                AgentController.KIND_ASSISTANT -> out.append("### Agent\n\n").append(text).append('\n')
                AgentController.KIND_THINKING ->
                    out.append("> _Thinking:_ ").append(blockquote(text)).append('\n')
                AgentController.KIND_TOOL -> out.append("- `").append(oneLine(text)).append("`\n")
                else -> out.append("> ").append(blockquote(text)).append('\n')
            }
        }
        return out.toString()
    }

    /** Prefix every line after the first so multi-line text stays inside the blockquote. */
    private fun blockquote(text: String): String = text.replace("\n", "\n> ")

    /** Collapse a tool chip's text onto a single backticked line. */
    private fun oneLine(text: String): String = text.replace("\n", " ").trim()
}
