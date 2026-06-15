package dev.openandroiduse.companion.agent.llm

import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.ToolResultBlockParam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tool-result wire guard (Phase 5.1): the neutral [AgentContent.ToolResult] maps
 * to the same Anthropic shape the agent has always sent — text (+ optional
 * image), the error flag, and the screenshot-pruning marker.
 */
class ToolResultMappingTest {

    private fun firstToolResult(result: AgentContent.ToolResult): ToolResultBlockParam {
        val param: MessageParam =
            AnthropicMessageMapping.toMessageParam(AgentMessage(AgentRole.USER, listOf(result)))
        return param.content().blockParams().get().first().toolResult().get()
    }

    @Test
    fun resultWithScreenshotCarriesTextThenImage() {
        val tr = firstToolResult(
            AgentContent.ToolResult("toolu_1", "tree text", isError = false, image = ToolImage("BASE64PNG")),
        )
        val blocks = tr.content().get().blocks().get()
        assertEquals(2, blocks.size)
        assertEquals("tree text", blocks[0].text().get().text())
        assertTrue(blocks[1].image().isPresent)
        assertEquals("toolu_1", tr.toolUseId())
    }

    @Test
    fun prunedResultLeavesMarkerTextAndDropsImage() {
        val tr = firstToolResult(
            AgentContent.ToolResult("toolu_2", "tree text", isError = false, image = null, omittedImage = true),
        )
        val blocks = tr.content().get().blocks().get()
        assertEquals(2, blocks.size)
        assertEquals("tree text", blocks[0].text().get().text())
        assertEquals(AnthropicMessageMapping.SCREENSHOT_OMITTED, blocks[1].text().get().text())
        assertTrue(blocks.none { it.image().isPresent })
    }

    @Test
    fun errorFlagIsPreserved() {
        val declined = firstToolResult(AgentContent.ToolResult("toolu_3", "declined", isError = true))
        assertTrue(declined.isError().get())
        val ok = firstToolResult(AgentContent.ToolResult("toolu_4", "done", isError = false))
        assertFalse(ok.isError().orElse(false))
    }
}
