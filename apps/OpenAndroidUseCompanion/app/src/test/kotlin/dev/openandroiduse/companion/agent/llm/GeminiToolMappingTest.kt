package dev.openandroiduse.companion.agent.llm

import com.google.genai.types.Type
import dev.openandroiduse.companion.agent.AgentTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gemini tool-schema guard (Phase 5.2): the frozen [AgentTools.specs] map to
 * Gemini function declarations with the same names, order, required fields, and
 * an OBJECT parameter schema. Mirrors AnthropicToolMappingTest for the Gemini side.
 */
class GeminiToolMappingTest {

    private fun declarations() =
        GeminiMessageMapping.toTools(AgentTools.specs())
            .single()
            .functionDeclarations()
            .orElseThrow()

    @Test
    fun exposesTheNineToolSurfaceInOrder() {
        assertEquals(
            listOf(
                "click", "drag", "get_app_state", "list_apps", "perform_secondary_action",
                "press_key", "scroll", "set_value", "type_text",
            ),
            declarations().map { it.name().orElseThrow() },
        )
    }

    @Test
    fun parametersAreObjectSchemasWithRequiredPreserved() {
        val byName = declarations().associateBy { it.name().orElseThrow() }
        val click = byName.getValue("click").parameters().orElseThrow()
        assertEquals(Type.Known.OBJECT, click.type().orElseThrow().knownEnum())
        assertEquals(listOf("app"), click.required().orElse(emptyList()))
        assertEquals(
            listOf("app", "from_x", "from_y", "to_x", "to_y"),
            byName.getValue("drag").parameters().orElseThrow().required().orElse(emptyList()),
        )
        // list_apps takes no arguments → no required list.
        assertTrue(byName.getValue("list_apps").parameters().orElseThrow().required().orElse(emptyList()).isEmpty())
    }

    @Test
    fun propertyTypesMapToGeminiSchemaTypes() {
        val drag = declarations().first { it.name().orElseThrow() == "drag" }
            .parameters().orElseThrow().properties().orElseThrow()
        // Coordinates are numbers; the app target is a string.
        assertEquals(Type.Known.NUMBER, drag.getValue("from_x").type().orElseThrow().knownEnum())
        assertEquals(Type.Known.STRING, drag.getValue("app").type().orElseThrow().knownEnum())
    }

    @Test
    fun isDeterministicAcrossCalls() {
        assertEquals(
            declarations().map { it.toString() },
            declarations().map { it.toString() },
        )
    }
}
