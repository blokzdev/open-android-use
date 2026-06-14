package dev.openandroiduse.companion.agent

/**
 * Derives a one-line preview snippet for the History list (Phase 4.7c) — the gist of a
 * conversation without opening it. Prefers the agent's last substantive reply, falling back to
 * the last user prompt. Pure (no Android/SDK) so it unit-tests on the JVM.
 */
object SessionPreview {

    private const val MAX_LENGTH = 100

    fun derive(transcript: List<TranscriptEntry>): String {
        val line = transcript.lastOrNull { it.kind == AgentController.KIND_ASSISTANT && it.text.isNotBlank() }?.text
            ?: transcript.lastOrNull { it.kind == AgentController.KIND_USER && it.text.isNotBlank() }?.text
            ?: return ""
        val cleaned = line.replace(Regex("\\s+"), " ").trim()
        if (cleaned.length <= MAX_LENGTH) return cleaned
        val cut = cleaned.take(MAX_LENGTH)
        val lastSpace = cut.lastIndexOf(' ')
        val base = if (lastSpace >= MAX_LENGTH / 2) cut.take(lastSpace) else cut.trimEnd()
        return "$base…"
    }
}
