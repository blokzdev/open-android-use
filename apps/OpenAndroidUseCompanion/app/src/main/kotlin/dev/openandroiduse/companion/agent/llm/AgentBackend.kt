package dev.openandroiduse.companion.agent.llm

/**
 * The provider-agnostic seam for one streaming, tool-using assistant turn over
 * the frozen 9-tool Computer Use schema (Phase 5.1,
 * docs/design-docs/phase5-multi-provider-byok.md). The agent loop
 * (`AgentController`) drives this interface; everything provider-specific —
 * auth, wire format, streaming decode, tool-call mapping, prompt-cache
 * semantics — lives behind an implementation (the first being
 * [AnthropicBackend]).
 *
 * The shape is a blocking sink rather than a Flow: the loop has a single
 * sequential consumer on its own worker thread and cancels by force-closing the
 * in-flight stream, so Flow's backpressure/composition machinery would be
 * unused. (Rationale + the deferred Flow alternative: docs/BACKLOG.md.)
 */
interface AgentBackend {

    /**
     * Streams one assistant turn, blocking the calling (worker) thread until it
     * completes. Live text/thinking deltas are pushed to [sink] as they arrive;
     * the fully-accumulated turn is returned. Implementations must register
     * their in-flight stream so [cancel] can force-close it mid-turn.
     */
    fun streamTurn(request: BackendRequest, sink: BackendSink): CompletedTurn

    /**
     * Force-close any in-flight stream from another thread (the stop-button
     * path). Safe to call when no turn is running.
     */
    fun cancel()

    /** Release the underlying client. Called once when the loop ends. */
    fun close()
}

/** Everything a backend needs to build one request. Provider-neutral. */
data class BackendRequest(
    val model: String,
    val systemPrompt: String,
    val tools: List<ToolSpec>,
    val messages: List<AgentMessage>,
)

/**
 * Live stream callbacks, invoked on the worker thread as deltas arrive. Mirrors
 * exactly the two delta kinds the loop renders: assistant text and thinking.
 */
interface BackendSink {
    fun onTextDelta(text: String)
    fun onThinkingDelta(text: String)
}

/** The terminal result of a turn: the assistant message plus a neutral stop reason. */
data class CompletedTurn(
    val assistant: AgentMessage,
    val stopReason: AgentStopReason,
    /** Provider-specific refusal detail surfaced to the transcript; empty when n/a. */
    val refusalDetail: String = "",
)

/**
 * Neutral stop reasons. [PAUSE_TURN] is Anthropic-specific (server-side pause);
 * other providers simply never emit it and the loop treats its absence as a
 * normal terminal turn.
 */
enum class AgentStopReason { END_TURN, TOOL_USE, REFUSAL, MAX_TOKENS, PAUSE_TURN }

/**
 * Thrown when a stream arrives but cannot be assembled into a complete turn
 * (malformed or partial accumulation). The loop surfaces this as a stream-error
 * note, distinct from transport/auth failures which carry their own cause chain.
 */
class BackendStreamException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
