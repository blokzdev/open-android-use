package dev.openandroiduse.companion.agent.llm

/** Conversation role in the neutral history model. */
enum class AgentRole { USER, ASSISTANT }

/**
 * A provider-neutral message in the agent's history (Phase 5.1).
 *
 * For ASSISTANT turns produced by a backend, [replayPayload] holds an opaque,
 * backend-private object (the original provider message param) used for
 * byte-exact replay on the next request — this is how Anthropic's requirement
 * that extended-thinking blocks (with their `signature` / `redacted_thinking`
 * `data`) be sent back verbatim is preserved losslessly without the neutral
 * model having to represent signatures. The agent loop never inspects it; only
 * the same backend that produced it consumes it. It is null for user messages,
 * tool-result messages, and history rebuilt from a persisted transcript.
 */
data class AgentMessage(
    val role: AgentRole,
    val content: List<AgentContent>,
    val replayPayload: Any? = null,
)

/** One block of message content. */
sealed interface AgentContent {

    /** Plain text (a user prompt, or an assistant's narration). */
    data class Text(val text: String) : AgentContent

    /**
     * A model-issued tool call. [argsJson] is the raw input as a JSON object
     * string, decoded once by the loop into the arguments it executes.
     */
    data class ToolUse(val id: String, val name: String, val argsJson: String) : AgentContent

    /**
     * A tool result the loop feeds back. [image] is an optional screenshot.
     * When [omittedImage] is true the image was dropped by screenshot pruning;
     * a backend reproduces the original "(screenshot omitted to save context)"
     * marker so the on-the-wire shape matches an un-pruned result minus the
     * image bytes.
     */
    data class ToolResult(
        val toolUseId: String,
        val text: String,
        val isError: Boolean,
        val image: ToolImage? = null,
        val omittedImage: Boolean = false,
    ) : AgentContent
}

/** A PNG screenshot payload (Base64), carried by a [AgentContent.ToolResult]. */
data class ToolImage(val pngBase64: String)
