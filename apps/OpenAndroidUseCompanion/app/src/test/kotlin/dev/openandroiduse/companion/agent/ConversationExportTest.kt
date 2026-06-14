package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationExportTest {

    @Test
    fun rendersTitleAndEachKind() {
        val md = ConversationExport.toMarkdown(
            title = "Turn Bluetooth on",
            messages = listOf(
                AgentController.KIND_USER to "Turn Bluetooth on",
                AgentController.KIND_THINKING to "I should open Settings first",
                AgentController.KIND_TOOL to "▸ click Settings",
                AgentController.KIND_ASSISTANT to "Done — Bluetooth is now on.",
                AgentController.KIND_NOTE to "Stopped.",
            ),
        )
        assertTrue(md.startsWith("# Turn Bluetooth on"))
        assertTrue(md.contains("### You\n\nTurn Bluetooth on"))
        assertTrue(md.contains("### Agent\n\nDone — Bluetooth is now on."))
        assertTrue(md.contains("> _Thinking:_ I should open Settings first"))
        assertTrue(md.contains("- `▸ click Settings`"))
        assertTrue(md.contains("> Stopped."))
    }

    @Test
    fun blankTitleFallsBackToGenericHeading() {
        val md = ConversationExport.toMarkdown("   ", listOf(AgentController.KIND_USER to "Hi"))
        assertTrue(md.startsWith("# Conversation"))
    }

    @Test
    fun skipsEmptyMessagesAndKeepsMultilineInsideBlockquote() {
        val md = ConversationExport.toMarkdown(
            title = "T",
            messages = listOf(
                AgentController.KIND_ASSISTANT to "   ",
                AgentController.KIND_NOTE to "line one\nline two",
            ),
        )
        assertFalse(md.contains("### Agent"))
        // The continuation line must be re-prefixed so it stays in the quote.
        assertTrue(md.contains("> line one\n> line two"))
    }

    @Test
    fun emptyConversationIsJustTheHeader() {
        val md = ConversationExport.toMarkdown("Empty", emptyList())
        assertEquals("# Empty\n\n_Exported from Open Android Use Companion._\n", md)
    }
}
