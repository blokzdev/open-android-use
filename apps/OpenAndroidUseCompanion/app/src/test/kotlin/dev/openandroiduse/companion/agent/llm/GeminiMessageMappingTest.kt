package dev.openandroiduse.companion.agent.llm

import com.google.genai.types.FunctionCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gemini message-mapping guard (Phase 5.2): neutral history → Gemini Contents,
 * and Gemini function calls → neutral tool_use. Covers the tool-result wire
 * (functionResponse + inline image), the screenshot-pruning marker, and the
 * id↔name encoding that lets a name-matched functionResponse round-trip.
 */
class GeminiMessageMappingTest {

    @Test
    fun userTextBecomesUserContentWithTextPart() {
        val content = GeminiMessageMapping.toContents(
            listOf(AgentMessage(AgentRole.USER, listOf(AgentContent.Text("Turn Bluetooth on")))),
        ).single()
        assertEquals("user", content.role().orElseThrow())
        val parts = content.parts().orElseThrow()
        assertEquals(1, parts.size)
        assertEquals("Turn Bluetooth on", parts[0].text().orElseThrow())
    }

    @Test
    fun assistantTextAndToolUseBecomeModelContentWithFunctionCall() {
        val content = GeminiMessageMapping.toContents(
            listOf(
                AgentMessage(
                    AgentRole.ASSISTANT,
                    listOf(
                        AgentContent.Text("Opening Settings."),
                        AgentContent.ToolUse("get_app_state@0", "get_app_state", """{"app":"settings"}"""),
                    ),
                ),
            ),
        ).single()
        assertEquals("model", content.role().orElseThrow())
        val parts = content.parts().orElseThrow()
        assertEquals("Opening Settings.", parts[0].text().orElseThrow())
        val call = parts[1].functionCall().orElseThrow()
        assertEquals("get_app_state", call.name().orElseThrow())
        assertEquals("settings", call.args().orElseThrow()["app"])
    }

    @Test
    fun toolResultWithImageBecomesFunctionResponsePlusInlineImage() {
        // "aGVsbG8=" decodes to "hello"; we only assert an inline image part exists.
        val content = GeminiMessageMapping.toContents(
            listOf(
                AgentMessage(
                    AgentRole.USER,
                    listOf(
                        AgentContent.ToolResult(
                            toolUseId = "get_app_state@0",
                            text = "tree text",
                            isError = false,
                            image = ToolImage("aGVsbG8="),
                        ),
                    ),
                ),
            ),
        ).single()
        val parts = content.parts().orElseThrow()
        val response = parts[0].functionResponse().orElseThrow()
        assertEquals("get_app_state", response.name().orElseThrow())
        assertEquals("tree text", response.response().orElseThrow()["output"])
        assertTrue(parts[1].inlineData().isPresent)
    }

    @Test
    fun prunedResultCarriesMarkerAndDropsImage() {
        val content = GeminiMessageMapping.toContents(
            listOf(
                AgentMessage(
                    AgentRole.USER,
                    listOf(
                        AgentContent.ToolResult(
                            toolUseId = "click@0",
                            text = "tree text",
                            isError = false,
                            image = null,
                            omittedImage = true,
                        ),
                    ),
                ),
            ),
        ).single()
        val parts = content.parts().orElseThrow()
        assertEquals(1, parts.size)
        val response = parts[0].functionResponse().orElseThrow().response().orElseThrow()
        assertEquals(GeminiMessageMapping.SCREENSHOT_OMITTED, response["note"])
        assertFalse(parts.any { it.inlineData().isPresent })
    }

    @Test
    fun errorResultUsesErrorKey() {
        val content = GeminiMessageMapping.toContents(
            listOf(
                AgentMessage(
                    AgentRole.USER,
                    listOf(AgentContent.ToolResult("press_key@0", "declined", isError = true)),
                ),
            ),
        ).single()
        val response = content.parts().orElseThrow()[0].functionResponse().orElseThrow().response().orElseThrow()
        assertEquals("declined", response["error"])
    }

    @Test
    fun functionCallsBecomeNeutralToolUsesWithEncodedId() {
        val calls = listOf(
            FunctionCall.builder().name("click").args(mapOf("app" to "settings", "element_index" to "5")).build(),
            FunctionCall.builder().name("click").args(mapOf("app" to "settings", "element_index" to "9")).build(),
        )
        val toolUses = GeminiMessageMapping.toToolUses(calls)
        assertEquals(listOf("click@0", "click@1"), toolUses.map { it.id })
        assertEquals("click", toolUses[0].name)
        // Parallel calls to the same tool stay distinguishable, and the name round-trips.
        assertEquals("click", GeminiMessageMapping.nameFromToolUseId(toolUses[1].id))
        assertTrue(toolUses[0].argsJson.contains("\"app\""))
    }
}
