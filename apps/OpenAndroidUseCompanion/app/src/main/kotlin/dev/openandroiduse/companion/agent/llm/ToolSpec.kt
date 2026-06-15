package dev.openandroiduse.companion.agent.llm

/**
 * A provider-neutral tool definition for the frozen 9-tool Computer Use surface
 * (Phase 5.1). Each backend maps these to its own wire format — e.g.
 * [AnthropicBackend] maps them to `com.anthropic` `Tool` objects.
 *
 * [properties] is insertion-ordered (property name -> a JSON-schema fragment
 * such as `{"type":"string","description":...}`); order is preserved so the
 * mapped schema is byte-identical every turn, which the prompt cache depends on.
 */
data class ToolSpec(
    val name: String,
    val description: String,
    val properties: LinkedHashMap<String, Map<String, Any>>,
    val required: List<String>,
)
