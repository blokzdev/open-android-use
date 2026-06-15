package dev.openandroiduse.companion.agent.llm

import org.json.JSONObject

/**
 * The on-device function-calling layer (Phase 5.5b): a **pure, unit-testable**
 * render+parse pair that gives Gemma 4 the 9-tool surface via structured prompting
 * (the LiteRT-LM Kotlin tool API exists but is under-documented, and a
 * prompt-based scheme is runtime-portable and fully testable without a device).
 *
 * [render] flattens the system prompt + tool declarations + conversation into one
 * prompt; the model is asked to emit a tool call as a fenced JSON block:
 * ```tool_call
 * {"name": "click", "arguments": {"app": "settings", "element_index": "5"}}
 * ```
 * [parse] pulls those blocks back out into neutral [AgentContent.ToolUse]s and
 * returns the remaining prose as the visible answer.
 *
 * The exact wording/format is a starting point to tune against the real model on
 * hardware (Phase 5.5b verification); the render/parse contract is pinned by tests.
 */
object GemmaToolPrompt {

    private val CALL_REGEX = Regex("```tool_call\\s*(\\{.*?})\\s*```", RegexOption.DOT_MATCHES_ALL)

    data class ParsedTurn(val text: String, val toolUses: List<AgentContent.ToolUse>)

    fun render(systemPrompt: String, tools: List<ToolSpec>, messages: List<AgentMessage>): String {
        val sb = StringBuilder()
        sb.append(systemPrompt.trim()).append("\n\n")
        sb.append("You can call tools. To call one, output exactly one fenced block:\n")
        sb.append("```tool_call\n{\"name\": \"<tool>\", \"arguments\": { ... }}\n```\n")
        sb.append("Otherwise, reply normally. Available tools:\n")
        for (tool in tools) {
            sb.append("- ").append(tool.name).append(": ").append(tool.description)
            sb.append(" | parameters: ").append(JSONObject(tool.properties as Map<*, *>).toString())
            if (tool.required.isNotEmpty()) sb.append(" | required: ").append(tool.required.joinToString(","))
            sb.append('\n')
        }
        sb.append("\nConversation:\n")
        for (message in messages) renderMessage(sb, message)
        sb.append("Assistant: ")
        return sb.toString()
    }

    private fun renderMessage(sb: StringBuilder, message: AgentMessage) {
        for (content in message.content) {
            when (content) {
                is AgentContent.Text ->
                    sb.append(if (message.role == AgentRole.USER) "User: " else "Assistant: ")
                        .append(content.text).append('\n')
                is AgentContent.ToolUse ->
                    sb.append("Assistant called ").append(content.name).append(' ').append(content.argsJson).append('\n')
                is AgentContent.ToolResult ->
                    sb.append("Tool result (").append(content.toolUseId).append("): ").append(content.text).append('\n')
            }
        }
    }

    fun parse(output: String): ParsedTurn {
        val toolUses = mutableListOf<AgentContent.ToolUse>()
        val text = StringBuilder()
        var lastEnd = 0
        var index = 0
        for (match in CALL_REGEX.findAll(output)) {
            text.append(output, lastEnd, match.range.first)
            lastEnd = match.range.last + 1
            try {
                val obj = JSONObject(match.groupValues[1])
                val name = obj.optString("name")
                if (name.isNotEmpty()) {
                    val args = obj.optJSONObject("arguments")?.toString() ?: "{}"
                    toolUses.add(AgentContent.ToolUse(id = "$name@${index++}", name = name, argsJson = args))
                }
            } catch (_: Exception) {
                // A malformed block is left as visible text rather than dropped.
                text.append(output, match.range.first, match.range.last + 1)
            }
        }
        if (lastEnd < output.length) text.append(output, lastEnd, output.length)
        return ParsedTurn(text.toString().trim(), toolUses)
    }
}
