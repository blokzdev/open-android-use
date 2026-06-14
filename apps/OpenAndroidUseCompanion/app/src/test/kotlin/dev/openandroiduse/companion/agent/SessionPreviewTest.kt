package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPreviewTest {

    private fun entry(kind: String, text: String) = TranscriptEntry(kind, text, 0L)

    @Test
    fun prefersLastAssistantReply() {
        val transcript = listOf(
            entry(AgentController.KIND_USER, "Turn Bluetooth on"),
            entry(AgentController.KIND_ASSISTANT, "Done — Bluetooth is on."),
        )
        assertEquals("Done — Bluetooth is on.", SessionPreview.derive(transcript))
    }

    @Test
    fun fallsBackToUserWhenNoAssistant() {
        val transcript = listOf(
            entry(AgentController.KIND_USER, "Open Settings"),
            entry(AgentController.KIND_TOOL, "Tap [12]"),
        )
        assertEquals("Open Settings", SessionPreview.derive(transcript))
    }

    @Test
    fun blankWhenNothingSubstantive() {
        assertEquals("", SessionPreview.derive(emptyList()))
        assertEquals("", SessionPreview.derive(listOf(entry(AgentController.KIND_TOOL, "Tap [1]"))))
    }

    @Test
    fun collapsesWhitespaceAndTruncatesLongText() {
        val long = "word ".repeat(60).trim()
        val preview = SessionPreview.derive(listOf(entry(AgentController.KIND_ASSISTANT, long)))
        assertTrue("ends with ellipsis: $preview", preview.endsWith("…"))
        assertTrue("trimmed: ${preview.length}", preview.length <= 101)
        assertTrue("no newlines", !preview.contains("\n"))
    }
}
