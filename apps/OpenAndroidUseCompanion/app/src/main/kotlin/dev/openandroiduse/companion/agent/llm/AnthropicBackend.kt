package dev.openandroiduse.companion.agent.llm

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.http.StreamResponse
import com.anthropic.helpers.MessageAccumulator
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.RawMessageStreamEvent
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigAdaptive

/**
 * The Anthropic (Claude) [AgentBackend] (Phase 5.1): the existing, working path
 * extracted behind the provider-neutral seam. Owns the SDK client, the streaming
 * request, prompt-cache + adaptive-thinking config, and the accumulate loop;
 * translation to/from neutral types lives in [AnthropicMessageMapping].
 *
 * This is the only place (with its mapping sibling) under the `agent` tree that
 * imports `com.anthropic`. [streamTurn] blocks the worker thread and [cancel]
 * force-closes the in-flight stream from the stop-button thread — the same
 * cancellation mechanism the agent loop has always used.
 */
class AnthropicBackend(apiKey: String, baseUrl: String?) : AgentBackend {

    private val client = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .apply { if (baseUrl != null) baseUrl(baseUrl) }
        .build()

    @Volatile
    private var activeStream: StreamResponse<RawMessageStreamEvent>? = null

    @Volatile
    private var cancelled = false

    override fun streamTurn(request: BackendRequest, sink: BackendSink): CompletedTurn {
        cancelled = false
        val accumulator = MessageAccumulator.create()
        try {
            client.messages().createStreaming(buildParams(request)).use { stream ->
                activeStream = stream
                for (event in stream.stream()) {
                    if (cancelled) break
                    accumulator.accumulate(event)
                    emitDelta(event, sink)
                }
            }
        } finally {
            activeStream = null
        }
        val message = try {
            accumulator.message()
        } catch (error: Exception) {
            throw BackendStreamException(error.message ?: "", error)
        }
        return CompletedTurn(
            assistant = AnthropicMessageMapping.toAssistantMessage(message),
            stopReason = mapStopReason(message.stopReason().orElse(null)),
            refusalDetail = message.stopDetails()
                .map { it._additionalProperties().toString() }
                .orElse(""),
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
        client.close()
    }

    private fun buildParams(request: BackendRequest): MessageCreateParams {
        val builder = MessageCreateParams.builder()
            .model(request.model)
            .maxTokens(MAX_TOKENS)
            // Cache the whole conversation prefix, not just tools+system: the
            // tree-text and (windowed) screenshots dominate input tokens, and
            // auto-caching the last block lets every turn read the prior prefix.
            .cacheControl(CacheControlEphemeral.builder().build())
            .thinking(
                ThinkingConfigAdaptive.builder()
                    .display(ThinkingConfigAdaptive.Display.SUMMARIZED)
                    .build(),
            )
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.HIGH).build())
            // Cache breakpoint on the last (only) system block caches the
            // frozen tools + system prefix across turns.
            .systemOfTextBlockParams(
                listOf(
                    TextBlockParam.builder()
                        .text(request.systemPrompt)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build(),
                ),
            )
        for (tool in request.tools) {
            builder.addTool(AnthropicMessageMapping.toAnthropicTool(tool))
        }
        for (message in request.messages) {
            builder.addMessage(AnthropicMessageMapping.toMessageParam(message))
        }
        return builder.build()
    }

    private fun emitDelta(event: RawMessageStreamEvent, sink: BackendSink) {
        val delta = event.contentBlockDelta().orElse(null)?.delta() ?: return
        delta.text().orElse(null)?.let { sink.onTextDelta(it.text()) }
        delta.thinking().orElse(null)?.let { sink.onThinkingDelta(it.thinking()) }
    }

    private fun mapStopReason(reason: StopReason?): AgentStopReason = when (reason) {
        StopReason.REFUSAL -> AgentStopReason.REFUSAL
        StopReason.TOOL_USE -> AgentStopReason.TOOL_USE
        StopReason.PAUSE_TURN -> AgentStopReason.PAUSE_TURN
        StopReason.MAX_TOKENS -> AgentStopReason.MAX_TOKENS
        else -> AgentStopReason.END_TURN
    }

    companion object {
        private const val MAX_TOKENS = 64_000L
    }
}
