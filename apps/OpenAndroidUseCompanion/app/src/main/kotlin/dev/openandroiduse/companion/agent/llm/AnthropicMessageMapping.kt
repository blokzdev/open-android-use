package dev.openandroiduse.companion.agent.llm

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Tool

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
}
