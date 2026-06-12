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

    /** Transcript kinds, mirrored by the chat UI. */
    const val KIND_USER = "user"
    const val KIND_ASSISTANT = "assistant"
    const val KIND_THINKING = "thinking"
    const val KIND_TOOL = "tool"
    const val KIND_NOTE = "note"

    @Volatile
    var listener: Listener? = null

    @Volatile
    private var cancelRequested = false

    @Volatile
    private var pausedByTouch = false

    @Volatile
    private var activeStream: StreamResponse<RawMessageStreamEvent>? = null

    @Volatile
    var isRunning = false
        private set

    private val history = mutableListOf<HistoryEntry>()
    private var worker: Thread? = null

    /**
     * Append-only transcript backing the chat UI: the Activity is backgrounded
     * for most of a task (the agent is driving other apps), so it re-renders
     * from this on resume instead of relying on live callbacks alone.
     */
    private val transcript = mutableListOf<Pair<String, StringBuilder>>()

    fun transcriptSnapshot(): List<Pair<String, String>> = synchronized(transcript) {
        transcript.map { it.first to it.second.toString() }
    }

    private fun log(kind: String, text: String, append: Boolean = false) {
        synchronized(transcript) {
            val last = transcript.lastOrNull()
            if (append && last != null && last.first == kind) {
                last.second.append(text)
            } else {
                transcript.add(kind to StringBuilder(text))
            }
        }
    }

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
        pausedByTouch = false
        isRunning = true
        listener?.onTaskStateChanged(true)
        log(KIND_USER, userText)
        history.add(HistoryEntry(userMessage(userText), pruned = null))
        TouchPauseMonitor.reset()
        GestureTrail.attach(service)
        speakNarration = settings.speakNarration
        if (speakNarration) {
            VoiceNarrator.ensureInitialized(service)
        }
        val confirmActions = settings.confirmActions
        worker = Thread(
            {
                try {
                    runLoop(service, apiKey, settings.model, confirmActions)
                } finally {
                    GestureTrail.detach(service)
                    VoiceNarrator.stop()
                }
            },
            "oau-agent-loop",
        ).also { it.start() }
        return true
    }

    @Volatile
    private var speakNarration = false

    fun requestStop() {
        cancelRequested = true
        try {
            activeStream?.close()
        } catch (_: Exception) {
        }
    }

    /**
     * Touch-to-pause entry point, fed by CompanionService's accessibility
     * events. Any direct user manipulation outside the agent's own gesture
     * window suspends the task.
     */
    fun onInteractionEvent(eventPackage: String?, ownPackage: String) {
        if (TouchPauseMonitor.shouldPause(isRunning, eventPackage, ownPackage)) {
            pausedByTouch = true
            requestStop()
        }
    }

    @Synchronized
    fun resetConversation() {
        if (isRunning) return
        history.clear()
        synchronized(transcript) { transcript.clear() }
    }

    private fun runLoop(service: CompanionService, apiKey: String, model: String, confirmActions: Boolean) {
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
        val executor = ToolExecutor(service)
        try {
            var turns = 0
            while (turns < MAX_TOOL_TURNS) {
                turns++
                if (cancelRequested) return finish(stopReasonLabel())

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
                if (cancelRequested) return finish(stopReasonLabel())

                val message = try {
                    accumulator.message()
                } catch (error: Exception) {
                    return fail("The response stream ended unexpectedly: ${error.message}")
                }
                if (speakNarration) {
                    VoiceNarrator.onMessageEnd()
                }
                appendAssistant(message.toParam())

                val stopReason = message.stopReason().orElse(null)
                when (stopReason) {
                    StopReason.REFUSAL -> {
                        val category = message.stopDetails()
                            .map { it._additionalProperties().toString() }
                            .orElse("")
                        val refusalText = "The request was declined by the model's safety system$category. " +
                            "Rephrase the task rather than retrying it as-is."
                        log(KIND_NOTE, "⚠ $refusalText")
                        listener?.onError(refusalText)
                        return finish("refusal")
                    }
                    StopReason.TOOL_USE -> {
                        val toolUses = message.content().mapNotNull { it.toolUse().orElse(null) }
                        if (toolUses.isEmpty()) return finish("end_turn")
                        val denied = confirmActions && needsConfirmation(toolUses) &&
                            !ConfirmationSheet.ask(service, batchSummary(toolUses))
                        val results = mutableListOf<ContentBlockParam>()
                        for (toolUse in toolUses) {
                            if (cancelRequested) return finish(stopReasonLabel())
                            results.add(
                                if (denied) deniedResult(toolUse) else executeTool(executor, toolUse),
                            )
                        }
                        if (denied) {
                            log(KIND_NOTE, "Action batch denied by the user.")
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
                finish(stopReasonLabel())
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
        delta.text().orElse(null)?.let {
            log(KIND_ASSISTANT, it.text(), append = true)
            if (speakNarration) {
                VoiceNarrator.onAssistantDelta(it.text())
            }
            listener?.onAssistantDelta(it.text())
        }
        delta.thinking().orElse(null)?.let {
            log(KIND_THINKING, it.thinking(), append = true)
            listener?.onThinkingDelta(it.thinking())
        }
    }

    private fun stopReasonLabel(): String = if (pausedByTouch) "touched" else "stopped"

    private val mutatingTools = setOf(
        "click", "drag", "scroll", "type_text", "press_key", "set_value", "perform_secondary_action",
    )

    private fun needsConfirmation(toolUses: List<ToolUseBlock>): Boolean =
        toolUses.any { it.name() in mutatingTools }

    private fun batchSummary(toolUses: List<ToolUseBlock>): String =
        toolUses.joinToString("\n") { toolUse ->
            val args = try {
                @Suppress("UNCHECKED_CAST")
                JSONObject(toolUse._input().convert(Map::class.java) as Map<String, Any?>)
            } catch (_: Exception) {
                JSONObject()
            }
            "• ${toolUse.name()} ${summarizeArgs(toolUse.name(), args)}".trimEnd()
        }

    private fun deniedResult(toolUse: ToolUseBlock): ContentBlockParam =
        ContentBlockParam.ofToolResult(
            ToolResultBlockParam.builder()
                .toolUseId(toolUse.id())
                .content("The user declined this action. Ask how they would like to proceed instead of retrying.")
                .isError(true)
                .build(),
        )

    private fun executeTool(executor: ToolExecutor, toolUse: ToolUseBlock): ContentBlockParam {
        val args = try {
            @Suppress("UNCHECKED_CAST")
            JSONObject(toolUse._input().convert(Map::class.java) as Map<String, Any?>)
        } catch (_: Exception) {
            JSONObject()
        }
        val summary = summarizeArgs(toolUse.name(), args)
        log(KIND_TOOL, "▸ ${toolUse.name()} $summary".trimEnd())
        listener?.onToolCall(toolUse.name(), summary)
        val outcome = executor.callTool(toolUse.name(), args)
        if (outcome.isError) {
            log(KIND_TOOL, "✗ ${toolUse.name()} failed")
        }
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
        when (reason) {
            "touched" -> log(KIND_NOTE, "Paused — you touched the screen. Send a message to continue.")
            "stopped" -> log(KIND_NOTE, "Stopped.")
            "max_tokens" -> log(KIND_NOTE, "The turn hit its output limit; say \"continue\" to resume.")
        }
        listener?.onTaskFinished(reason)
    }

    private fun fail(message: String) {
        log(KIND_NOTE, "⚠ $message")
        listener?.onError(message)
        listener?.onTaskFinished("error")
    }
}
