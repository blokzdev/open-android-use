package dev.openandroiduse.companion.agent

/**
 * Derives a human-readable session title from the first user prompt, the way
 * modern chat apps auto-name a conversation. Pure so it unit-tests on the JVM.
 */
object SessionTitle {

    private const val MAX_LENGTH = 40
    const val FALLBACK = "New conversation"

    fun derive(firstPrompt: String): String {
        // Collapse whitespace/newlines so a multi-line prompt stays one tidy line.
        val cleaned = firstPrompt.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isEmpty()) return FALLBACK
        if (cleaned.length <= MAX_LENGTH) return cleaned
        // Cut on a word boundary when there is a reasonable one, then ellipsize.
        val cut = cleaned.take(MAX_LENGTH)
        val lastSpace = cut.lastIndexOf(' ')
        val base = if (lastSpace >= MAX_LENGTH / 2) cut.take(lastSpace) else cut.trimEnd()
        return "$base…"
    }
}
