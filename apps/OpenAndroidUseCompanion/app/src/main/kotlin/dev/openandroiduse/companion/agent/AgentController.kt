package dev.openandroiduse.companion.agent

import android.util.Log
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.http.StreamResponse
import com.anthropic.helpers.MessageAccumulator
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.RawMessageStreamEvent
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigAdaptive
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUseBlock
import dev.openandroiduse.companion.BuildConfig
import dev.openandroiduse.companion.CompanionService
import org.json.JSONObject

/**
 * The manual agentic loop (docs/exec-plans/completed/20260612-phase3-on-device-
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

    /**
     * The transcript ([transcriptSnapshot]) is the single source of truth for
     * the UI. The listener only signals that it advanced (so the chat renders
     * incrementally) and that the running state changed — there is no second,
     * drift-prone per-event render path.
     */
    interface Listener {
        fun onTaskStateChanged(running: Boolean)
        fun onTranscriptChanged()

        /**
         * The agent just captured a screenshot it is about to send to the model.
         * Lets the chat show "what the agent sees". Default no-op so non-UI
         * listeners (e.g. the emulator smoke) are unaffected. [pngBase64] is the
         * already-downscaled PNG; it is in-memory only, never written to disk.
         */
        fun onScreenshotCaptured(pngBase64: String) {}
    }

    /**
     * One history entry. [param] is mutated in place when an image-bearing
     * tool result leaves the recent-image window: the screenshot is stripped
     * and the heavy full variant is dropped (it is never sent again), bounding
     * heap growth on an always-on service. [hadImage] records that the entry
     * originally carried a screenshot, so pruning counts only those.
     */
    private class HistoryEntry(var param: MessageParam, val hadImage: Boolean = false) {
        var pruned = false
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

    /**
     * The latest screenshot the agent captured (already-downscaled PNG, Base64),
     * for the chat's "Agent's view". In-memory only, never persisted; replaced on
     * each capture and cleared on conversation reset.
     */
    @Volatile
    var latestScreenshotBase64: String? = null
        private set

    /**
     * Where the agent last tapped, as a normalized 0..1 fraction of the latest
     * screenshot, for the chat's "Agent's view" marker. In-memory only; paired
     * with [latestScreenshotBase64] and cleared on reset.
     */
    @Volatile
    var latestTapNormalized: Pair<Float, Float>? = null
        private set

    private val history = mutableListOf<HistoryEntry>()
    private var worker: Thread? = null

    // --- Session identity (Phase 4.5 multi-session persistence) ---
    // The conversation in memory belongs to a session; the UI layer persists it
    // via SessionStore (on task end / pause) and can restore a past one.

    /** Stable id of the in-memory conversation, for SessionStore. */
    @Volatile
    var currentSessionId: String = newSessionId()
        private set

    private var sessionCreatedAt: Long = System.currentTimeMillis()

    /** Title: auto-derived from the first prompt, or carried over on restore. */
    private var sessionTitle: String? = null

    /**
     * Bumped whenever the transcript changes (a logged line or a restore), so the UI
     * can skip re-persisting an unchanged conversation on a routine pause.
     */
    @Volatile
    var transcriptRevision: Int = 0
        private set

    private fun newSessionId(): String = java.util.UUID.randomUUID().toString()

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
        transcriptRevision++
        listener?.onTranscriptChanged()
    }

    @Synchronized
    fun startTask(userText: String, settings: AgentSettings): Boolean {
        if (isRunning) return false
        val service = CompanionService.instance ?: run {
            log(KIND_NOTE, "⚠ The companion accessibility service is not running. Enable it first.")
            return false
        }
        val apiKey = settings.loadApiKey() ?: run {
            log(KIND_NOTE, "⚠ No API key configured. Add your Anthropic API key in settings.")
            return false
        }
        cancelRequested = false
        pausedByTouch = false
        isRunning = true
        listener?.onTaskStateChanged(true)
        // First prompt of a fresh session names it (like modern chat apps).
        if (sessionTitle == null && history.isEmpty()) {
            sessionTitle = SessionTitle.derive(userText)
        }
        log(KIND_USER, userText)
        history.add(HistoryEntry(userMessage(userText)))
        TouchPauseMonitor.reset()
        // Inversion: the control surface (CompanionService) exposes a neutral
        // interaction hook; the agent registers here. This keeps the
        // dependency-free control surface from importing the agent/SDK.
        service.interactionListener = { eventPackage, ownPackage ->
            onInteractionEvent(eventPackage, ownPackage)
        }
        GestureTrail.attach(service)
        InControlOverlay.attach(service) { requestStop() }
        AgentNotification.show(service)
        speakNarration = settings.speakNarration
        if (speakNarration) {
            VoiceNarrator.ensureInitialized(service)
        }
        val confirmActions = settings.confirmActions
        // The loopback base-URL override is a debug-only test hook: honoring a
        // persisted pref in release would let anything that can write prefs
        // redirect the API-key-bearing client. Release ignores it entirely.
        val baseUrl = if (BuildConfig.DEBUG) loopbackOrNull(settings.baseUrlOverride) else null
        worker = Thread(
            {
                try {
                    runLoop(service, apiKey, settings.model, confirmActions, baseUrl)
                } finally {
                    service.interactionListener = null
                    GestureTrail.detach(service)
                    InControlOverlay.detach(service)
                    AgentNotification.cancel(service)
                    VoiceNarrator.stop()
                }
            },
            "oau-agent-loop",
        ).also { it.start() }
        return true
    }

    /** Accepts only http loopback URLs; anything else (incl. https/remote) is rejected. */
    private fun loopbackOrNull(url: String?): String? {
        if (url == null) return null
        return if (url.startsWith("http://127.0.0.1:") || url.startsWith("http://localhost:")) url else null
    }

    @Volatile
    private var speakNarration = false

    fun requestStop() {
        cancelRequested = true
        // Release a loop thread parked in the consent sheet so Stop is
        // responsive even mid-confirmation.
        ConfirmationSheet.cancel()
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
        latestScreenshotBase64 = null
        latestTapNormalized = null
        synchronized(transcript) { transcript.clear() }
        // Start a fresh session so the next task is saved separately and the old
        // one (already persisted by the UI) is left intact.
        currentSessionId = newSessionId()
        sessionCreatedAt = System.currentTimeMillis()
        sessionTitle = null
    }

    /** Clearer name for [resetConversation] now that conversations are sessions. */
    fun newConversation() = resetConversation()

    /**
     * A snapshot of the in-memory conversation for SessionStore, or null when
     * there is nothing worth saving. Only the transcript is persisted; the model
     * history is rebuilt from it on [restore].
     */
    @Synchronized
    fun snapshotForPersistence(): SessionPayload? {
        val lines = transcriptSnapshot()
        if (lines.isEmpty()) return null
        val title = sessionTitle
            ?: lines.firstOrNull { it.first == KIND_USER }?.second?.let { SessionTitle.derive(it) }
            ?: SessionTitle.FALLBACK
        return SessionPayload(
            id = currentSessionId,
            title = title,
            createdAt = sessionCreatedAt,
            updatedAt = System.currentTimeMillis(),
            archived = false,
            transcript = lines.map { StoredMessage(it.first, it.second) },
        )
    }

    /**
     * Replaces the in-memory conversation with a saved session so the agent
     * resumes it with its conversational context (history rebuilt from the
     * transcript; the device is re-observed live). Ignored while a task runs.
     */
    @Synchronized
    fun restore(payload: SessionPayload): Boolean {
        if (isRunning) return false
        history.clear()
        for (param in SessionHistory.rebuild(payload.transcript.map { it.kind to it.text })) {
            history.add(HistoryEntry(param))
        }
        synchronized(transcript) {
            transcript.clear()
            for (line in payload.transcript) {
                transcript.add(line.kind to StringBuilder(line.text))
            }
        }
        latestScreenshotBase64 = null
        latestTapNormalized = null
        currentSessionId = payload.id
        sessionCreatedAt = payload.createdAt
        sessionTitle = payload.title
        transcriptRevision++
        listener?.onTranscriptChanged()
        return true
    }

    /** Keeps the live title in sync when the user renames the active session. */
    @Synchronized
    fun noteRenamed(id: String, title: String) {
        if (id == currentSessionId) sessionTitle = title
    }

    private fun runLoop(
        service: CompanionService,
        apiKey: String,
        model: String,
        confirmActions: Boolean,
        baseUrl: String?,
    ) {
        val clientBuilder = AnthropicOkHttpClient.builder().apiKey(apiKey)
        if (baseUrl != null) {
            clientBuilder.baseUrl(baseUrl)
        }
        val client = clientBuilder.build()
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
                        return finish("refusal")
                    }
                    StopReason.TOOL_USE -> {
                        val toolUses = message.content().mapNotNull { it.toolUse().orElse(null) }
                        if (toolUses.isEmpty()) return finish("end_turn")
                        val denied = confirmActions && needsConfirmation(toolUses) &&
                            !ConfirmationSheet.ask(service, batchSummary(toolUses))
                        val results = mutableListOf<ContentBlockParam>()
                        var interrupted = false
                        for (toolUse in toolUses) {
                            if (cancelRequested) {
                                interrupted = true
                                break
                            }
                            results.add(
                                if (denied) deniedResult(toolUse) else executeTool(executor, toolUse),
                            )
                        }
                        // Every tool_use must get a tool_result or the next
                        // request 400s ("tool_use without tool_result"); fill
                        // any the interruption skipped so the conversation
                        // stays resumable.
                        for (index in results.size until toolUses.size) {
                            results.add(interruptedResult(toolUses[index]))
                        }
                        appendToolResults(results)
                        if (denied) {
                            log(KIND_NOTE, "Action batch denied by the user.")
                        }
                        if (interrupted) return finish(stopReasonLabel())
                    }
                    StopReason.PAUSE_TURN -> {
                        // Server-side pause: re-send and the API resumes the turn.
                    }
                    StopReason.MAX_TOKENS -> {
                        // A truncated turn may end in a tool_use; close it out
                        // so "continue" doesn't 400.
                        fillDanglingToolUses(message)
                        return finish("max_tokens")
                    }
                    else -> return finish("end_turn")
                }
            }
            fail("The task exceeded $MAX_TOOL_TURNS tool turns and was stopped.")
        } catch (error: Exception) {
            if (!cancelRequested) {
                Log.w(CompanionService.TAG, "agent loop failed", error)
                // Surface the whole cause chain: SDK wrappers like
                // "Request failed" are useless to the user on their own.
                val chain = generateSequence<Throwable>(error) { it.cause }
                    .joinToString(" ← ") { "${it.javaClass.simpleName}: ${it.message ?: ""}".trim(' ', ':') }
                fail(chain)
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
                builder.addMessage(entry.param)
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
        }
        delta.thinking().orElse(null)?.let {
            log(KIND_THINKING, it.thinking(), append = true)
        }
    }

    private fun stopReasonLabel(): String = if (pausedByTouch) "touched" else "stopped"

    private val mutatingTools = setOf(
        "click", "drag", "scroll", "type_text", "press_key", "set_value", "perform_secondary_action",
    )

    private fun needsConfirmation(toolUses: List<ToolUseBlock>): Boolean =
        toolUses.any { it.name() in mutatingTools }

    /** Single decode of a tool_use's input, shared by the summary and execution paths. */
    private fun argsOf(toolUse: ToolUseBlock): JSONObject = try {
        @Suppress("UNCHECKED_CAST")
        JSONObject(toolUse._input().convert(Map::class.java) as Map<String, Any?>)
    } catch (_: Exception) {
        JSONObject()
    }

    private fun batchSummary(toolUses: List<ToolUseBlock>): String =
        toolUses.joinToString("\n") { toolUse ->
            "• ${toolUse.name()} ${summarizeArgs(toolUse.name(), argsOf(toolUse))}".trimEnd()
        }

    private fun deniedResult(toolUse: ToolUseBlock): ContentBlockParam =
        errorResult(toolUse, "The user declined this action. Ask how they would like to proceed instead of retrying.")

    private fun interruptedResult(toolUse: ToolUseBlock): ContentBlockParam =
        errorResult(toolUse, "This action was not performed because the task was interrupted.")

    private fun errorResult(toolUse: ToolUseBlock, text: String): ContentBlockParam =
        ContentBlockParam.ofToolResult(
            ToolResultBlockParam.builder()
                .toolUseId(toolUse.id())
                .content(text)
                .isError(true)
                .build(),
        )

    /** Closes out any tool_use blocks in [message] with interrupted results. */
    private fun fillDanglingToolUses(message: Message) {
        val toolUses = message.content().mapNotNull { it.toolUse().orElse(null) }
        if (toolUses.isNotEmpty()) {
            appendToolResults(toolUses.map { interruptedResult(it) })
        }
    }

    private fun executeTool(executor: ToolExecutor, toolUse: ToolUseBlock): ContentBlockParam {
        val args = argsOf(toolUse)
        val summary = summarizeArgs(toolUse.name(), args)
        log(KIND_TOOL, "▸ ${toolUse.name()} $summary".trimEnd())
        val outcome = executor.callTool(toolUse.name(), args)
        if (outcome.isError) {
            log(KIND_TOOL, "✗ ${toolUse.name()} failed — the agent will see the error and adapt")
        }
        outcome.screenshotPngBase64?.let { png ->
            latestScreenshotBase64 = png
            latestTapNormalized = executor.lastTapNormalized
            listener?.onScreenshotCaptured(png)
        }

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
        history.add(HistoryEntry(param))
    }

    /**
     * Appends a tool-result user message. Only entries that actually carry a
     * screenshot count toward [RECENT_IMAGE_WINDOW]; older image-bearing
     * entries are rewritten in place to drop the screenshot (and the heavy
     * full param with it), bounding context and heap growth.
     */
    @Synchronized
    private fun appendToolResults(blocks: List<ContentBlockParam>) {
        val hadImage = blocks.any { block ->
            block.toolResult().orElse(null)
                ?.content()?.orElse(null)?.blocks()?.orElse(null)
                ?.any { it.image().isPresent } == true
        }
        val message = MessageParam.builder()
            .role(MessageParam.Role.USER)
            .contentOfBlockParams(blocks)
            .build()
        history.add(HistoryEntry(message, hadImage))

        val live = history.filter { it.hadImage && !it.pruned }
        if (live.size > RECENT_IMAGE_WINDOW) {
            for (entry in live.dropLast(RECENT_IMAGE_WINDOW)) {
                entry.param = stripScreenshots(entry.param)
                entry.pruned = true
            }
        }
    }

    /** Rewrites a tool-result message without image blocks, noting the removal only where one occurred. */
    private fun stripScreenshots(param: MessageParam): MessageParam {
        val content = param.content().blockParams().orElse(null) ?: return param
        val rewritten = content.map { block ->
            val toolResult = block.toolResult().orElse(null) ?: return@map block
            val source = toolResult.content().orElse(null)?.blocks()?.orElse(null) ?: return@map block
            if (source.none { it.image().isPresent }) return@map block
            val textOnly = source.filter { it.text().isPresent }.toMutableList()
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
        return MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(rewritten).build()
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
    }

    private fun fail(message: String) {
        log(KIND_NOTE, "⚠ $message")
    }
}
