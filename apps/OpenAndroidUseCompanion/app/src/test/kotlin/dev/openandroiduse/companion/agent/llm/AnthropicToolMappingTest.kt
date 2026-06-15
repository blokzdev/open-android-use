package dev.openandroiduse.companion.agent.llm

import dev.openandroiduse.companion.agent.AgentTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prompt-cache parity guard (Phase 5.1): the neutral [AgentTools.specs] mapped
 * through [AnthropicMessageMapping.toAnthropicTool] must produce a stable,
 * correctly-shaped tool list. The tools prefix is part of the cached prompt
 * prefix, so any drift would silently break caching. (The emulator smoke pins
 * the exact on-the-wire schema end-to-end.)
 */
class AnthropicToolMappingTest {

    private fun mapped() = AgentTools.specs().map { AnthropicMessageMapping.toAnthropicTool(it) }

    @Test
    fun exposesTheNineToolSurfaceInOrder() {
        assertEquals(
            listOf(
                "click", "drag", "get_app_state", "list_apps", "perform_secondary_action",
                "press_key", "scroll", "set_value", "type_text",
            ),
            mapped().map { it.name() },
        )
    }

    @Test
    fun preservesRequiredFields() {
        val byName = mapped().associateBy { it.name() }
        assertEquals(listOf("app"), byName.getValue("click").inputSchema().required().orElse(emptyList()))
        assertEquals(
            listOf("app", "from_x", "from_y", "to_x", "to_y"),
            byName.getValue("drag").inputSchema().required().orElse(emptyList()),
        )
        // list_apps takes no arguments: no required array at all.
        assertTrue(byName.getValue("list_apps").inputSchema().required().orElse(emptyList()).isEmpty())
    }

    @Test
    fun isDeterministicAcrossCalls() {
        // Prompt caching depends on a byte-identical tools prefix every turn.
        assertEquals(mapped().map { it.toString() }, mapped().map { it.toString() })
    }
}
