package dev.openandroiduse.companion.agent

import android.util.Log
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.http.StreamResponse
import com.anthropic.helpers.MessageAccumulator
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.RawMessageStreamEvent
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigAdaptive
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUseBlock
import dev.openandroiduse.companion.CompanionService
import org.json.JSONObject

/**
 * The manual agentic loop (docs/exec-plans/active/20260612-phase3-on-device-
 * agent.md): stream a turn, execute tool batches in-process via ToolExecutor,
 * feed results back, repeat until end_turn — with a cancel gate checked
 * between stream events and before every tool execution so the stop button
 * stays responsive. Deliberately not the SDK tool runner: pause/consent
 * control between batches is the product.
 *
 * Hosted by the companion process (kept alive by the accessibility service);
 * one task at a time. Listener callbacks arrive on the loop thread.
 */
object AgentController {

    interface Listener {
        fun onTaskStateChanged(running: Boolean)
        fun onAssistantDelta(text: String)
        fun onThinkingDelta(text: String)
        fun onToolCall(name: String, summary: String)
        fun onToolResult(name: String, isError: Boolean)
        fun onTaskFinished(reason: String)
        fun onError(message: String)
    }

    /** One history entry; [pruned] replaces [full] once it leaves the recent-image window. */
    private class HistoryEntry(val full: MessageParam, val pruned: MessageParam?, var usePruned: Boolean = false) {
        fun param(): MessageParam = if (usePruned && pruned != null) pruned else full
    }

    private const val MAX_TOKENS = 64_000L
    private const val MAX_TOOL_TURNS = 60
    private const val RECENT_IMAGE_WINDOW = 2

    @Volatile
    var listener: Listener? = null

    @Volatile
    private var cancelRequested = false

    @Volatile
    private var activeStream: StreamResponse<RawMessageStreamEvent>? = null

    @Volatile
    var isRunning = false
        private set

    private val history = mutableListOf<HistoryEntry>()
    private var worker: Thread? = null

    @Synchronized
    fun startTask(userText: String, settings: AgentSettings): Boolean {
        if (isRunning) return false
        val service = CompanionService.instance ?: run {
            listener?.onError("The companion accessibility service is not running. Enable it first.")
            return false
        }
        val apiKey = settings.loadApiKey() ?: run {
            listener?.onError("No API key configured. Add your Anthropic API key in settings.")
            return false
        }
        cancelRequested = false
        isRunning = true
        listener?.onTaskStateChanged(true)
        history.add(HistoryEntry(userMessage(userText), pruned = null))
        worker = Thread({ runLoop(service, apiKey, settings.model) }, "oau-agent-loop").also { it.start() }
        return true
    }

    fun requestStop() {
        cancelRequested = true
        try {
            activeStream?.close()
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun resetConversation() {
        if (isRunning) return
        history.clear()
    }

    private fun runLoop(service: CompanionService, apiKey: String, model: String) {
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
        val executor = ToolExecutor(service)
        try {
            var turns = 0
            while (turns < MAX_TOOL_TURNS) {
                turns++
                if (cancelRequested) return finish("stopped")

                val accumulator = MessageAccumulator.create()
                try {
                    client.messages().createStreaming(buildParams(model)).use { stream ->
                        activeStream = stream
                        for (event in stream.stream()) {
                            if (cancelRequested) break
                            accumulator.accumulate(event)
                            emitDelta(event)
                        }
                    }
                } finally {
                    activeStream = null
                }
                if (cancelRequested) return finish("stopped")

                val message = try {
                    accumulator.message()
                } catch (error: Exception) {
                    return fail("The response stream ended unexpectedly: ${error.message}")
                }
                appendAssistant(message.toParam())

                val stopReason = message.stopReason().orElse(null)
                when (stopReason) {
                    StopReason.REFUSAL -> {
                        val category = message.stopDetails()
                            .map { it._additionalProperties().toString() }
                            .orElse("")
                        listener?.onError(
                            "The request was declined by the model's safety system$category. " +
                                "Rephrase the task rather than retrying it as-is.",
                        )
                        return finish("refusal")
                    }
                    StopReason.TOOL_USE -> {
                        val toolUses = message.content().mapNotNull { it.toolUse().orElse(null) }
                        if (toolUses.isEmpty()) return finish("end_turn")
                        val results = mutableListOf<ContentBlockParam>()
                        for (toolUse in toolUses) {
                            if (cancelRequested) return finish("stopped")
                            results.add(executeTool(executor, toolUse))
                        }
                        appendToolResults(results)
                    }
                    StopReason.PAUSE_TURN -> {
                        // Server-side pause: re-send and the API resumes the turn.
                    }
                    StopReason.MAX_TOKENS -> return finish("max_tokens")
                    else -> return finish("end_turn")
                }
            }
            fail("The task exceeded $MAX_TOOL_TURNS tool turns and was stopped.")
        } catch (error: Exception) {
            if (!cancelRequested) {
                Log.w(CompanionService.TAG, "agent loop failed", error)
                fail(error.message ?: error.javaClass.simpleName)
            } else {
                finish("stopped")
            }
        } finally {
            client.close()
            isRunning = false
            listener?.onTaskStateChanged(false)
        }
    }

    private fun buildParams(model: String): MessageCreateParams {
        val builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(MAX_TOKENS)
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
                        .text(AgentTools.SYSTEM_PROMPT)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build(),
                ),
            )
        for (tool in AgentTools.definitions()) {
            builder.addTool(tool)
        }
        synchronized(this) {
            for (entry in history) {
                builder.addMessage(entry.param())
            }
        }
        return builder.build()
    }

    private fun emitDelta(event: RawMessageStreamEvent) {
        val delta = event.contentBlockDelta().orElse(null)?.delta() ?: return
        delta.text().orElse(null)?.let { listener?.onAssistantDelta(it.text()) }
        delta.thinking().orElse(null)?.let { listener?.onThinkingDelta(it.thinking()) }
    }

    private fun executeTool(executor: ToolExecutor, toolUse: ToolUseBlock): ContentBlockParam {
        val args = try {
            @Suppress("UNCHECKED_CAST")
            JSONObject(toolUse._input().convert(Map::class.java) as Map<String, Any?>)
        } catch (_: Exception) {
            JSONObject()
        }
        listener?.onToolCall(toolUse.name(), summarizeArgs(toolUse.name(), args))
        val outcome = executor.callTool(toolUse.name(), args)
        listener?.onToolResult(toolUse.name(), outcome.isError)

        val blocks = mutableListOf(
            ToolResultBlockParam.Content.Block.ofText(
                TextBlockParam.builder().text(outcome.text.ifEmpty { "(empty)" }).build(),
            ),
        )
        outcome.screenshotPngBase64?.let { png ->
            blocks.add(
                ToolResultBlockParam.Content.Block.ofImage(
                    ImageBlockParam.builder()
                        .source(
                            Base64ImageSource.builder()
                                .data(png)
                                .mediaType(Base64ImageSource.MediaType.IMAGE_PNG)
                                .build(),
                        )
                        .build(),
                ),
            )
        }
        return ContentBlockParam.ofToolResult(
            ToolResultBlockParam.builder()
                .toolUseId(toolUse.id())
                .contentOfBlocks(blocks)
                .isError(outcome.isError)
                .build(),
        )
    }

    private fun summarizeArgs(name: String, args: JSONObject): String = when (name) {
        "click" -> args.optString("element_index").ifBlank {
            "(${args.optDouble("x", 0.0).toInt()}, ${args.optDouble("y", 0.0).toInt()})"
        }
        "get_app_state", "list_apps" -> args.optString("app")
        "type_text" -> "\"${args.optString("text").take(40)}\""
        "set_value" -> "element ${args.optString("element_index")}"
        "press_key" -> args.optString("key")
        "scroll" -> "${args.optString("direction")} ×${args.optDouble("pages", 1.0)}"
        else -> ""
    }

    @Synchronized
    private fun appendAssistant(param: MessageParam) {
        history.add(HistoryEntry(param, pruned = null))
    }

    /**
     * Appends a tool-result user message, keeping screenshots only in the
     * [RECENT_IMAGE_WINDOW] most recent tool results. Older entries swap to a
     * text-only variant with a stable placeholder — the same screenshot-pruning
     * pattern Anthropic's computer-use reference uses to bound context growth.
     */
    @Synchronized
    private fun appendToolResults(blocks: List<ContentBlockParam>) {
        val prunedBlocks = blocks.map { block ->
            val toolResult = block.toolResult().orElse(null) ?: return@map block
            val sourceBlocks = toolResult.content().orElse(null)?.blocks()?.orElse(null) ?: emptyList()
            val textOnly = sourceBlocks.filter { it.text().isPresent }.toMutableList()
            textOnly.add(
                ToolResultBlockParam.Content.Block.ofText(
                    TextBlockParam.builder().text("(screenshot omitted to save context)").build(),
                ),
            )
            ContentBlockParam.ofToolResult(
                ToolResultBlockParam.builder()
                    .toolUseId(toolResult.toolUseId())
                    .contentOfBlocks(textOnly)
                    .isError(toolResult.isError().orElse(false))
                    .build(),
            )
        }
        val full = MessageParam.builder()
            .role(MessageParam.Role.USER)
            .contentOfBlockParams(blocks)
            .build()
        val pruned = MessageParam.builder()
            .role(MessageParam.Role.USER)
            .contentOfBlockParams(prunedBlocks)
            .build()
        history.add(HistoryEntry(full, pruned))

        val imageBearing = history.filter { it.pruned != null }
        if (imageBearing.size > RECENT_IMAGE_WINDOW) {
            for (entry in imageBearing.dropLast(RECENT_IMAGE_WINDOW)) {
                entry.usePruned = true
            }
        }
    }

    private fun userMessage(text: String): MessageParam =
        MessageParam.builder()
            .role(MessageParam.Role.USER)
            .contentOfBlockParams(
                listOf(ContentBlockParam.ofText(TextBlockParam.builder().text(text).build())),
            )
            .build()

    private fun finish(reason: String) {
        listener?.onTaskFinished(reason)
    }

    private fun fail(message: String) {
        listener?.onError(message)
        listener?.onTaskFinished("error")
    }
}
