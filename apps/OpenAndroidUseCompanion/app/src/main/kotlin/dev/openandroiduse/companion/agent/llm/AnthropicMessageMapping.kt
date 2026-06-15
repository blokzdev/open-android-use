package dev.openandroiduse.companion.agent.llm

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUseBlock
import org.json.JSONObject

/**
 * Translation between the provider-neutral agent model (ToolSpec, AgentMessage)
 * and the Anthropic `com.anthropic` wire types (Phase 5.1). Kept SDK-side, in
 * the `agent.llm` package alongside [AnthropicBackend], so the neutral types and
 * the agent loop never import `com.anthropic`.
 *
 * Pure functions, no client — so the mapping is unit-testable without a network
 * call or an API key.
 */
object AnthropicMessageMapping {

    /**
     * Map a neutral [ToolSpec] to an Anthropic [Tool]. Reproduces the schema
     * shape the agent has always sent (`additionalProperties: false`, `required`
     * only when non-empty, insertion-ordered properties) so the tools prefix
     * stays byte-identical for prompt caching.
     */
    fun toAnthropicTool(spec: ToolSpec): Tool {
        val propertiesBuilder = Tool.InputSchema.Properties.builder()
        for ((key, schema) in spec.properties) {
            propertiesBuilder.putAdditionalProperty(key, JsonValue.from(schema))
        }
        val schemaBuilder = Tool.InputSchema.builder()
            .properties(propertiesBuilder.build())
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
        if (spec.required.isNotEmpty()) {
            schemaBuilder.required(spec.required)
        }
        return Tool.builder()
            .name(spec.name)
            .description(spec.description)
            .inputSchema(schemaBuilder.build())
            .build()
    }

    /**
     * Convert a finished Anthropic [Message] into a neutral assistant
     * [AgentMessage]. The neutral content exposes only what the agent loop acts
     * on — text and tool_use blocks; thinking blocks are intentionally not
     * surfaced (they were already streamed live). The original turn is stashed
     * in [AgentMessage.replayPayload] so the next request replays it verbatim,
     * preserving extended-thinking `signature` / `redacted_thinking` data that
     * Anthropic requires to be echoed back unchanged.
     */
    fun toAssistantMessage(message: Message): AgentMessage {
        val content = message.content().mapNotNull { block ->
            val text = block.text().orElse(null)
            if (text != null) {
                return@mapNotNull AgentContent.Text(text.text())
            }
            val toolUse = block.toolUse().orElse(null)
            if (toolUse != null) {
                return@mapNotNull AgentContent.ToolUse(toolUse.id(), toolUse.name(), inputToJson(toolUse))
            }
            null
        }
        return AgentMessage(AgentRole.ASSISTANT, content, replayPayload = message.toParam())
    }

    /**
     * Convert a neutral [AgentMessage] into an Anthropic [MessageParam]. Assistant
     * turns produced by [toAssistantMessage] carry their original param in
     * [AgentMessage.replayPayload] and are returned verbatim — the lossless path
     * for thinking signatures. Everything else (user prompts, tool-result
     * messages, transcript-rebuilt history) is built from the neutral blocks.
     */
    fun toMessageParam(message: AgentMessage): MessageParam {
        (message.replayPayload as? MessageParam)?.let { return it }
        val role = when (message.role) {
            AgentRole.USER -> MessageParam.Role.USER
            AgentRole.ASSISTANT -> MessageParam.Role.ASSISTANT
        }
        return MessageParam.builder()
            .role(role)
            .contentOfBlockParams(message.content.map { toContentBlockParam(it) })
            .build()
    }

    private fun toContentBlockParam(content: AgentContent): ContentBlockParam = when (content) {
        is AgentContent.Text ->
            ContentBlockParam.ofText(TextBlockParam.builder().text(content.text).build())
        is AgentContent.ToolResult ->
            ContentBlockParam.ofToolResult(toToolResultBlockParam(content))
        is AgentContent.ToolUse ->
            // Assistant tool_use turns always round-trip via replayPayload, so a
            // bare neutral ToolUse never reaches the builder on the Anthropic path.
            error("ToolUse must be replayed via AgentMessage.replayPayload, not rebuilt")
    }

    private fun toToolResultBlockParam(result: AgentContent.ToolResult): ToolResultBlockParam {
        val blocks = mutableListOf(
            ToolResultBlockParam.Content.Block.ofText(
                TextBlockParam.builder().text(result.text).build(),
            ),
        )
        result.image?.let { image ->
            blocks.add(
                ToolResultBlockParam.Content.Block.ofImage(
                    ImageBlockParam.builder()
                        .source(
                            Base64ImageSource.builder()
                                .data(image.pngBase64)
                                .mediaType(Base64ImageSource.MediaType.IMAGE_PNG)
                                .build(),
                        )
                        .build(),
                ),
            )
        }
        if (result.omittedImage) {
            blocks.add(
                ToolResultBlockParam.Content.Block.ofText(
                    TextBlockParam.builder().text(SCREENSHOT_OMITTED).build(),
                ),
            )
        }
        return ToolResultBlockParam.builder()
            .toolUseId(result.toolUseId)
            .contentOfBlocks(blocks)
            .isError(result.isError)
            .build()
    }

    /** Serialize a tool_use's input to a JSON object string the loop can re-parse. */
    private fun inputToJson(toolUse: ToolUseBlock): String = try {
        @Suppress("UNCHECKED_CAST")
        JSONObject(toolUse._input().convert(Map::class.java) as Map<String, Any?>).toString()
    } catch (_: Exception) {
        "{}"
    }

    /** The marker left in place of a pruned screenshot; must match the historical wire. */
    const val SCREENSHOT_OMITTED = "(screenshot omitted to save context)"
}
