package dev.openandroiduse.companion.agent.llm

import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingBlockParam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Thinking-signature round-trip guard (Phase 5.1). An assistant turn carries its
 * original [MessageParam] in [AgentMessage.replayPayload]; the mapping returns it
 * verbatim, so extended-thinking `signature` data can never be lost or rebuilt
 * incorrectly — the contract Anthropic requires for thinking + tool_use.
 */
class AnthropicMessageMappingTest {

    @Test
    fun assistantReplayPayloadIsReturnedVerbatim() {
        // A realistic assistant turn: a signed thinking block plus narration.
        val original = MessageParam.builder()
            .role(MessageParam.Role.ASSISTANT)
            .contentOfBlockParams(
                listOf(
                    ContentBlockParam.ofThinking(
                        ThinkingBlockParam.builder()
                            .thinking("Open Settings, then toggle Bluetooth.")
                            .signature("sig-abc123")
                            .build(),
                    ),
                    ContentBlockParam.ofText(TextBlockParam.builder().text("Opening Settings.").build()),
                ),
            )
            .build()
        val neutral = AgentMessage(
            role = AgentRole.ASSISTANT,
            content = listOf(AgentContent.Text("Opening Settings.")),
            replayPayload = original,
        )

        // Same object reference back — the param is never reconstructed, so the
        // thinking signature is preserved by construction.
        assertSame(original, AnthropicMessageMapping.toMessageParam(neutral))
    }

    @Test
    fun userTextMessageBuildsASingleTextBlock() {
        val param = AnthropicMessageMapping.toMessageParam(
            AgentMessage(AgentRole.USER, listOf(AgentContent.Text("Turn Bluetooth on"))),
        )
        assertEquals(MessageParam.Role.USER, param.role())
        val blocks = param.content().blockParams().get()
        assertEquals(1, blocks.size)
        assertEquals("Turn Bluetooth on", blocks[0].text().get().text())
    }
}
