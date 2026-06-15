package dev.openandroiduse.companion.agent.llm

import dev.openandroiduse.companion.agent.AgentTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The on-device function-calling format (Phase 5.5b): render advertises the 9
 * tools + history, and parse round-trips the fenced tool-call block back into
 * neutral tool_use. The exact prose is tuned on hardware, but this contract is pinned.
 */
class GemmaToolPromptTest {

    @Test
    fun renderAdvertisesEveryToolAndTheHistory() {
        val prompt = GemmaToolPrompt.render(
            systemPrompt = "You operate an Android device.",
            tools = AgentTools.specs(),
            messages = listOf(AgentMessage(AgentRole.USER, listOf(AgentContent.Text("Open Settings")))),
        )
        assertTrue(prompt.contains("You operate an Android device."))
        assertTrue(prompt.contains("```tool_call"))
        for (spec in AgentTools.specs()) assertTrue("mentions ${spec.name}", prompt.contains(spec.name))
        assertTrue(prompt.contains("User: Open Settings"))
        assertTrue(prompt.trimEnd().endsWith("Assistant:"))
    }

    @Test
    fun parseExtractsAFencedToolCallAndKeepsProse() {
        val output = "Sure, opening it.\n```tool_call\n{\"name\": \"get_app_state\", \"arguments\": {\"app\": \"settings\"}}\n```"
        val parsed = GemmaToolPrompt.parse(output)
        assertEquals("Sure, opening it.", parsed.text)
        assertEquals(1, parsed.toolUses.size)
        assertEquals("get_app_state", parsed.toolUses[0].name)
        assertTrue(parsed.toolUses[0].argsJson.contains("settings"))
        assertEquals("get_app_state@0", parsed.toolUses[0].id)
    }

    @Test
    fun parseHandlesMultipleCallsAndPlainText() {
        val two = "```tool_call\n{\"name\":\"click\",\"arguments\":{\"app\":\"a\"}}\n```" +
            "\n```tool_call\n{\"name\":\"click\",\"arguments\":{\"app\":\"b\"}}\n```"
        val parsedTwo = GemmaToolPrompt.parse(two)
        assertEquals(listOf("click@0", "click@1"), parsedTwo.toolUses.map { it.id })

        val plain = GemmaToolPrompt.parse("Done — Bluetooth is on.")
        assertTrue(plain.toolUses.isEmpty())
        assertEquals("Done — Bluetooth is on.", plain.text)
    }
}
