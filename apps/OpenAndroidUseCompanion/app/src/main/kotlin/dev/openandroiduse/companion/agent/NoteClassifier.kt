package dev.openandroiduse.companion.agent

/**
 * Classifies a KIND_NOTE transcript string so the chat can render a styled card
 * with the right tone and (where useful) a one-tap fix. Pure UI-side logic — the
 * transcript text is not changed (keeps the emulator smoke green). Unit-tested.
 */
enum class NoteStyle { INFO, PAUSED, NEEDS_KEY, NEEDS_ACCESSIBILITY, ERROR }

object NoteClassifier {
    fun classify(text: String): NoteStyle {
        val t = text.lowercase()
        return when {
            "paused" in t && "touch" in t -> NoteStyle.PAUSED
            "api key" in t -> NoteStyle.NEEDS_KEY
            "accessibility service" in t -> NoteStyle.NEEDS_ACCESSIBILITY
            "authentication" in t || "unauthorized" in t || "401" in t ->
                NoteStyle.ERROR
            "error" in t || "failed" in t || "could not" in t || "couldn't" in t ->
                NoteStyle.ERROR
            else -> NoteStyle.INFO
        }
    }
}
