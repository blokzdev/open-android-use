package dev.openandroiduse.companion.agent

import com.anthropic.models.messages.MessageParam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionHistoryTest {

    private fun textOf(param: MessageParam): String =
        param.content().blockParams().get()
            .mapNotNull { it.text().orElse(null)?.text() }
            .joinToString("\n\n")

    @Test
    fun keepsOnlyUserAndAssistantTextAlternating() {
        val history = SessionHistory.rebuild(
            listOf(
                AgentController.KIND_USER to "Turn Bluetooth on",
                AgentController.KIND_THINKING to "I should open Settings",
                AgentController.KIND_TOOL to "▸ click Settings",
                AgentController.KIND_ASSISTANT to "Done — Bluetooth is on.",
            ),
        )
        assertEquals(2, history.size)
        assertEquals(MessageParam.Role.USER, history[0].role())
        assertEquals("Turn Bluetooth on", textOf(history[0]))
        assertEquals(MessageParam.Role.ASSISTANT, history[1].role())
        assertEquals("Done — Bluetooth is on.", textOf(history[1]))
    }

    @Test
    fun mergesConsecutiveSameRoleTurns() {
        val history = SessionHistory.rebuild(
            listOf(
                AgentController.KIND_USER to "Open Settings",
                AgentController.KIND_ASSISTANT to "Opening Settings.",
                AgentController.KIND_TOOL to "▸ get_app_state settings",
                AgentController.KIND_ASSISTANT to "It's open.",
            ),
        )
        assertEquals(2, history.size)
        assertEquals(MessageParam.Role.ASSISTANT, history[1].role())
        assertTrue(textOf(history[1]).contains("Opening Settings."))
        assertTrue(textOf(history[1]).contains("It's open."))
    }

    @Test
    fun dropsTrailingUnansweredUserTurnSoNextPromptStaysWellFormed() {
        val history = SessionHistory.rebuild(
            listOf(
                AgentController.KIND_USER to "First task",
                AgentController.KIND_ASSISTANT to "Did it.",
                AgentController.KIND_USER to "Stopped before I answered",
            ),
        )
        assertEquals(2, history.size)
        assertEquals(MessageParam.Role.ASSISTANT, history.last().role())
    }

    @Test
    fun emptyOrNoteOnlyTranscriptYieldsEmptyHistory() {
        val history = SessionHistory.rebuild(
            listOf(
                AgentController.KIND_NOTE to "Stopped.",
                AgentController.KIND_USER to "   ",
            ),
        )
        assertTrue(history.isEmpty())
    }
}
