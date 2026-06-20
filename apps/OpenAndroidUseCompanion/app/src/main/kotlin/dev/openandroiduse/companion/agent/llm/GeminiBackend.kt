package dev.openandroiduse.companion.agent.llm

import com.google.genai.Client
import com.google.genai.ResponseStream
import com.google.genai.types.Content
import com.google.genai.types.FinishReason
import com.google.genai.types.FunctionCall
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.HttpOptions
import com.google.genai.types.Part
import com.google.genai.types.ThinkingConfig

/**
 * The Gemini (Google) [AgentBackend] (Phase 5.2): the second provider behind the
 * provider-neutral seam. Owns the Google Gen AI SDK client, the streaming
 * request, and the accumulate loop; translation to/from neutral types lives in
 * [GeminiMessageMapping]. With [AnthropicBackend] and its mapping, these are the
 * only files under `agent` that import `com.google.genai`.
 *
 * [streamTurn] blocks the worker thread and [cancel] force-closes the in-flight
 * stream from the stop-button thread — the same cancellation mechanism the loop
 * has always used. With thinking enabled, Gemini 2.5 attaches a `thoughtSignature`
 * to each `functionCall` part that the API requires echoed back on later requests;
 * the assistant message stashes the raw model `Content` in `replayPayload` so those
 * signatures round-trip verbatim (mirroring the Anthropic thinking-signature path).
 */
class GeminiBackend(apiKey: String, baseUrl: String?) : AgentBackend {

    private val client = Client.builder()
        .apiKey(apiKey)
        .apply { baseUrl?.let { httpOptions(HttpOptions.builder().baseUrl(it).build()) } }
        .build()

    @Volatile
    private var activeStream: ResponseStream<GenerateContentResponse>? = null

    @Volatile
    private var cancelled = false

    override fun streamTurn(request: BackendRequest, sink: BackendSink): CompletedTurn {
        cancelled = false
        val config = buildConfig(request)
        val contents = GeminiMessageMapping.toContents(request.messages)
        val text = StringBuilder()
        val functionCalls = mutableListOf<FunctionCall>()
        // The raw functionCall parts carry Gemini 2.5's `thoughtSignature`; keep them so the assistant
        // turn can be replayed verbatim (see replayPayload below).
        val functionCallParts = mutableListOf<Part>()
        var finishReason: FinishReason? = null

        try {
            val stream = client.models.generateContentStream(request.model, contents, config)
            activeStream = stream
            stream.use { responses ->
                for (chunk in responses) {
                    if (cancelled) break
                    for (part in chunk.parts() ?: emptyList()) {
                        part.text().ifPresent { delta ->
                            if (part.thought().orElse(false)) {
                                sink.onThinkingDelta(delta)
                            } else {
                                sink.onTextDelta(delta)
                                text.append(delta)
                            }
                        }
                        part.functionCall().ifPresent {
                            functionCalls.add(it)
                            functionCallParts.add(part)
                        }
                    }
                    chunk.finishReason()?.let { finishReason = it }
                }
            }
        } finally {
            activeStream = null
        }

        val content = buildList {
            if (text.isNotEmpty()) add(AgentContent.Text(text.toString()))
            addAll(GeminiMessageMapping.toToolUses(functionCalls))
        }
        // Stash the model turn (narration + signed functionCall parts) for verbatim replay, so the
        // required `thoughtSignature` is echoed back on the next request. Null when no call was made.
        val replay = if (functionCallParts.isEmpty()) {
            null
        } else {
            GeminiMessageMapping.replayContent(text.toString(), functionCallParts)
        }
        return CompletedTurn(
            assistant = AgentMessage(AgentRole.ASSISTANT, content, replayPayload = replay),
            stopReason = mapStopReason(functionCalls.isNotEmpty(), finishReason),
            refusalDetail = finishReason?.toString().orEmpty(),
        )
    }

    override fun cancel() {
        cancelled = true
        try {
            activeStream?.close()
        } catch (_: Exception) {
        }
    }

    override fun close() {
        try {
            client.close()
        } catch (_: Exception) {
        }
    }

    private fun buildConfig(request: BackendRequest): GenerateContentConfig =
        GenerateContentConfig.builder()
            .systemInstruction(Content.fromParts(Part.fromText(request.systemPrompt)))
            .tools(GeminiMessageMapping.toTools(request.tools))
            // Surface thought summaries (Gemini 2.5+) as thinking deltas; the model
            // chooses its own thinking budget.
            .thinkingConfig(ThinkingConfig.builder().includeThoughts(true).build())
            .build()

    private fun mapStopReason(hasToolCalls: Boolean, reason: FinishReason?): AgentStopReason {
        // A turn with function calls is a tool-use turn regardless of finishReason
        // (Gemini reports STOP even when it emits function calls).
        if (hasToolCalls) return AgentStopReason.TOOL_USE
        return when (reason?.knownEnum()) {
            FinishReason.Known.MAX_TOKENS -> AgentStopReason.MAX_TOKENS
            FinishReason.Known.SAFETY,
            FinishReason.Known.PROHIBITED_CONTENT,
            FinishReason.Known.BLOCKLIST,
            FinishReason.Known.RECITATION,
            FinishReason.Known.IMAGE_SAFETY,
            FinishReason.Known.IMAGE_PROHIBITED_CONTENT,
            // A malformed tool call would otherwise end the turn silently; surface it as a refusal
            // so the reason reaches the transcript instead of the agent appearing to just stop.
            FinishReason.Known.MALFORMED_FUNCTION_CALL,
            -> AgentStopReason.REFUSAL
            else -> AgentStopReason.END_TURN
        }
    }
}
