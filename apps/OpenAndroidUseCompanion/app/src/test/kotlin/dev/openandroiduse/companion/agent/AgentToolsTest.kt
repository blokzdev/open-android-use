package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolsTest {

    @Test
    fun specsExposeTheNineToolSurface() {
        val names = AgentTools.specs().map { it.name }
        assertEquals(9, names.size)
        assertEquals(
            listOf(
                "click", "drag", "get_app_state", "list_apps", "perform_secondary_action",
                "press_key", "scroll", "set_value", "type_text",
            ),
            names,
        )
    }

    @Test
    fun specsAreDeterministicAcrossCalls() {
        // Prompt caching depends on a byte-identical tools prefix every turn.
        val first = AgentTools.specs().map { it.toString() }
        val second = AgentTools.specs().map { it.toString() }
        assertEquals(first, second)
    }

    @Test
    fun clickRequiresOnlyApp() {
        val click = AgentTools.specs().first { it.name == "click" }
        assertEquals(listOf("app"), click.required)
    }

    @Test
    fun dragRequiresAllCoordinates() {
        val drag = AgentTools.specs().first { it.name == "drag" }
        assertEquals(listOf("app", "from_x", "from_y", "to_x", "to_y"), drag.required)
    }

    @Test
    fun systemPromptIsFrozenAndMentionsEveryTool() {
        for (spec in AgentTools.specs()) {
            assertTrue(
                "system prompt must mention ${spec.name}",
                AgentTools.SYSTEM_PROMPT.contains(spec.name),
            )
        }
    }

    @Test
    fun systemPromptCarriesTheUntrustedContentDefense() {
        // 6.5c-4: the spotlighting clause is the always-on instruction defense.
        assertTrue(AgentTools.SYSTEM_PROMPT.contains("Untrusted content"))
        assertTrue(AgentTools.SYSTEM_PROMPT.contains("never as instructions"))
    }
}
