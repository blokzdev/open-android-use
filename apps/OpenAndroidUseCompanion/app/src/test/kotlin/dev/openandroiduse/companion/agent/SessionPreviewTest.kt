package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPreviewTest {

    @Test
    fun prefersLastAssistantReply() {
        val transcript = listOf(
            AgentController.KIND_USER to "Turn Bluetooth on",
            AgentController.KIND_ASSISTANT to "Done — Bluetooth is on.",
        )
        assertEquals("Done — Bluetooth is on.", SessionPreview.derive(transcript))
    }

    @Test
    fun fallsBackToUserWhenNoAssistant() {
        val transcript = listOf(
            AgentController.KIND_USER to "Open Settings",
            AgentController.KIND_TOOL to "Tap [12]",
        )
        assertEquals("Open Settings", SessionPreview.derive(transcript))
    }

    @Test
    fun blankWhenNothingSubstantive() {
        assertEquals("", SessionPreview.derive(emptyList()))
        assertEquals("", SessionPreview.derive(listOf(AgentController.KIND_TOOL to "Tap [1]")))
    }

    @Test
    fun collapsesWhitespaceAndTruncatesLongText() {
        val long = "word ".repeat(60).trim()
        val preview = SessionPreview.derive(listOf(AgentController.KIND_ASSISTANT to long))
        assertTrue("ends with ellipsis: $preview", preview.endsWith("…"))
        assertTrue("trimmed: ${preview.length}", preview.length <= 101)
        assertTrue("no newlines", !preview.contains("\n"))
    }
}
