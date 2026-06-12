package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolsTest {

    @Test
    fun definitionsExposeTheNineToolSurface() {
        val names = AgentTools.definitions().map { it.name() }
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
    fun definitionsAreDeterministicAcrossCalls() {
        // Prompt caching depends on a byte-identical tools prefix every turn.
        val first = AgentTools.definitions().map { it.toString() }
        val second = AgentTools.definitions().map { it.toString() }
        assertEquals(first, second)
    }

    @Test
    fun clickRequiresOnlyApp() {
        val click = AgentTools.definitions().first { it.name() == "click" }
        assertEquals(listOf("app"), click.inputSchema().required().orElse(emptyList()))
    }

    @Test
    fun dragRequiresAllCoordinates() {
        val drag = AgentTools.definitions().first { it.name() == "drag" }
        assertEquals(
            listOf("app", "from_x", "from_y", "to_x", "to_y"),
            drag.inputSchema().required().orElse(emptyList()),
        )
    }

    @Test
    fun systemPromptIsFrozenAndMentionsEveryTool() {
        for (tool in AgentTools.definitions()) {
            assertTrue(
                "system prompt must mention ${tool.name()}",
                AgentTools.SYSTEM_PROMPT.contains(tool.name()),
            )
        }
    }
}
