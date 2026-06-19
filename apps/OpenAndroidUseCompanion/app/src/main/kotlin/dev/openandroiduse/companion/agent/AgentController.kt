package dev.openandroiduse.companion.agent

import android.content.Context
import android.util.Log
import dev.openandroiduse.companion.ActionExecutor
import dev.openandroiduse.companion.BuildConfig
import dev.openandroiduse.companion.CompanionService
import dev.openandroiduse.companion.R
import dev.openandroiduse.companion.agent.llm.AgentBackend
import dev.openandroiduse.companion.agent.llm.AgentContent
import dev.openandroiduse.companion.agent.llm.AgentMessage
import dev.openandroiduse.companion.agent.llm.AgentRole
import dev.openandroiduse.companion.agent.llm.AgentStopReason
import dev.openandroiduse.companion.agent.llm.BackendRequest
import dev.openandroiduse.companion.agent.llm.BackendStreamException
import dev.openandroiduse.companion.agent.llm.CompletedTurn
import dev.openandroiduse.companion.agent.llm.EgressPolicy
import dev.openandroiduse.companion.agent.llm.LlmProvider
import dev.openandroiduse.companion.agent.llm.BackendSink
import dev.openandroiduse.companion.agent.llm.ToolImage
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

        /**
         * The agent's transient connection status changed (Phase 6.3a): non-null
         * while it is backing off / retrying a flaky model stream ("Rate-limited,
         * retrying… (2/3)"), null otherwise. Default no-op so non-UI listeners
         * (e.g. the emulator smoke) are unaffected.
         */
        fun onStatusChanged(status: String?) {}
    }

    /**
     * One history entry. [param] is mutated in place when an image-bearing
     * tool result leaves the recent-image window: the screenshot is stripped
     * and the heavy full variant is dropped (it is never sent again), bounding
     * heap growth on an always-on service. [hadImage] records that the entry
     * originally carried a screenshot, so pruning counts only those.
     */
    private class HistoryEntry(var message: AgentMessage, val hadImage: Boolean = false) {
        var pruned = false
    }

    private const val MAX_TOKENS = 64_000L
    private const val MAX_TOOL_TURNS = 60

    // Phase 6.3b: consecutive action-turns with no screen change before the loop
    // stops gracefully (vs. spinning a no-op to MAX_TOOL_TURNS).
    private const val STUCK_THRESHOLD = 3
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
    private var backend: AgentBackend? = null

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

    /**
     * The latest action's gestures (taps + swipes) in normalized screenshot space,
     * for the chat's "Agent's view" overlay. In-memory only; paired with
     * [latestScreenshotBase64] and cleared on reset.
     */
    @Volatile
    var latestGesturesNormalized: List<GestureMark> = emptyList()
        private set

    /**
     * Transient connection status (Phase 6.3a): non-null while the loop is backing
     * off / retrying a flaky model stream, surfaced as a badge in the chat's
     * Agent's-view. In-memory only; cleared on success and when the loop ends.
     */
    @Volatile
    var transientStatus: String? = null
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
     * Whether the in-memory session is pinned (Phase 4.7c). Tracked here, mirroring
     * [sessionTitle], so a snapshot save from the agent loop doesn't clobber a pin the user set;
     * the UI keeps it in sync for the active session via [notePinned].
     */
    private var sessionPinned: Boolean = false

    /**
     * Bumped whenever the transcript changes (a logged line or a restore), so the UI
     * can skip re-persisting an unchanged conversation on a routine pause.
     */
    @Volatile
    var transcriptRevision: Int = 0
        private set

    private fun newSessionId(): String = java.util.UUID.randomUUID().toString()

    /** App context for localizing transcript notes; set when a task starts. */
    @Volatile
    private var appContext: Context? = null

    // Phase 6.5c-3b scoped trust. SESSION grants are in-memory and cleared on reset/task end,
    // so trust never silently outlives a task; persistent grants (3c) come from [trustStore].
    private val sessionGrants = mutableSetOf<String>()

    @Volatile
    private var trustStore: TrustStore? = null

    @Volatile
    private var auditLog: TrustAuditLog? = null

    // 6.5c-5: whether the most recent perception result tripped the injection heuristic,
    // so the next action is risk-confirmed even under a grant.
    @Volatile
    private var lastPerceptionFlagged = false

    /** Resolve a localized note string off the agent loop thread. */
    private fun str(resId: Int, vararg args: Any): String =
        (appContext ?: CompanionService.instance)?.getString(resId, *args) ?: ""

    /** One transcript line with its start time (epoch millis); text grows in place on append. */
    private class Line(val kind: String, val text: StringBuilder, val createdAt: Long)

    /**
     * Append-only transcript backing the chat UI: the Activity is backgrounded
     * for most of a task (the agent is driving other apps), so it re-renders
     * from this on resume instead of relying on live callbacks alone.
     */
    private val transcript = mutableListOf<Line>()

    /** Timed snapshot (Phase 4.7b-3): each line with its KIND_*, text, and start time. */
    fun transcriptSnapshot(): List<TranscriptEntry> = synchronized(transcript) {
        transcript.map { TranscriptEntry(it.kind, it.text.toString(), it.createdAt) }
    }

    private fun log(kind: String, text: String, append: Boolean = false) {
        synchronized(transcript) {
            val last = transcript.lastOrNull()
            if (append && last != null && last.kind == kind) {
                last.text.append(text)
            } else {
                transcript.add(Line(kind, StringBuilder(text), System.currentTimeMillis()))
            }
        }
        transcriptRevision++
        listener?.onTranscriptChanged()
    }

    @Synchronized
    fun startTask(userText: String, settings: AgentSettings): Boolean {
        if (isRunning) return false
        appContext = settings.appContext
        if (trustStore == null) trustStore = TrustStore(settings.appContext)
        if (auditLog == null) auditLog = TrustAuditLog(settings.appContext)
        val service = CompanionService.instance ?: run {
            log(KIND_NOTE, str(R.string.agent_note_no_service))
            return false
        }
        // Phase 5.7: Local-Only Mode forces the on-device provider here — the single
        // chokepoint that makes "no cloud egress" structural, independent of any
        // stale UI selection (no cloud backend is ever constructed below).
        val provider = effectiveProvider(settings.localOnlyMode, settings.selectedProvider)
        // Cloud providers need a BYOK key; the on-device provider needs its model
        // downloaded. Resolve the right credential (key, or local model path).
        val credential: String = if (provider.requiresApiKey) {
            settings.loadApiKey(provider) ?: run {
                log(KIND_NOTE, str(R.string.agent_note_no_key))
                return false
            }
        } else {
            OnDeviceModelManager.modelPath(settings.appContext) ?: run {
                log(KIND_NOTE, str(R.string.agent_note_no_model))
                return false
            }
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
        // Reduce-motion: the gesture trail is decorative; skip it when the user
        // has turned animations off (the custom Canvas loop won't self-honor it).
        if (!Motion.animationsDisabled(service)) {
            GestureTrail.attach(service)
        }
        InControlOverlay.attach(service) { requestStop() }
        AgentNotification.show(service)
        speakNarration = settings.speakNarration
        if (speakNarration) {
            VoiceNarrator.ensureInitialized(service)
        }
        val confirmActions = settings.confirmActions
        // Phase 6.5: user-controllable password/payment guard (default on).
        val sensitiveScreenGuard = settings.sensitiveScreenGuard
        // Phase 5.6 adaptive perception: vision vs text-only for this provider.
        val captureScreenshots = perceptionMode(settings.sendScreenshots(provider)) == PerceptionMode.VISION
        // The loopback base-URL override is a debug-only test hook: honoring a
        // persisted pref in release would let anything that can write prefs
        // redirect the API-key-bearing client. Release ignores it entirely.
        val baseUrl = if (BuildConfig.DEBUG) EgressPolicy.loopbackOrNull(settings.baseUrlOverride) else null
        worker = Thread(
            {
                try {
                    runLoop(service, provider, credential, settings.model, confirmActions, baseUrl, captureScreenshots, sensitiveScreenGuard)
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

    @Volatile
    private var speakNarration = false

    fun requestStop() {
        cancelRequested = true
        // Release a loop thread parked in the consent sheet or the sensitive-screen
        // handoff overlay so Stop is responsive even mid-confirmation / mid-handoff.
        ConfirmationSheet.cancel()
        HandoffSheet.cancel()
        backend?.cancel()
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
        latestGesturesNormalized = emptyList()
        transientStatus = null
        // 6.5c-3b: session trust is task-scoped — a new conversation re-earns it.
        synchronized(sessionGrants) { sessionGrants.clear() }
        synchronized(transcript) { transcript.clear() }
        // Start a fresh session so the next task is saved separately and the old
        // one (already persisted by the UI) is left intact.
        currentSessionId = newSessionId()
        sessionCreatedAt = System.currentTimeMillis()
        sessionTitle = null
        sessionPinned = false
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
            ?: lines.firstOrNull { it.kind == KIND_USER }?.text?.let { SessionTitle.derive(it) }
            ?: SessionTitle.FALLBACK
        return SessionPayload(
            id = currentSessionId,
            title = title,
            createdAt = sessionCreatedAt,
            updatedAt = System.currentTimeMillis(),
            archived = false,
            transcript = lines.map { StoredMessage(it.kind, it.text, it.createdAt) },
            pinned = sessionPinned,
            preview = SessionPreview.derive(lines),
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
        for (message in SessionHistory.rebuild(payload.transcript.map { it.kind to it.text })) {
            history.add(HistoryEntry(message))
        }
        synchronized(transcript) {
            transcript.clear()
            for (line in payload.transcript) {
                transcript.add(Line(line.kind, StringBuilder(line.text), line.createdAt))
            }
        }
        latestScreenshotBase64 = null
        latestTapNormalized = null
        latestGesturesNormalized = emptyList()
        transientStatus = null
        currentSessionId = payload.id
        sessionCreatedAt = payload.createdAt
        sessionTitle = payload.title
        sessionPinned = payload.pinned
        transcriptRevision++
        listener?.onTranscriptChanged()
        return true
    }

    /** Keeps the live title in sync when the user renames the active session. */
    @Synchronized
    fun noteRenamed(id: String, title: String) {
        if (id == currentSessionId) sessionTitle = title
    }

    /** Keeps the live pin state in sync when the user pins/unpins the active session. */
    @Synchronized
    fun notePinned(id: String, pinned: Boolean) {
        if (id == currentSessionId) sessionPinned = pinned
    }

    private fun runLoop(
        service: CompanionService,
        provider: LlmProvider,
        credential: String,
        model: String,
        confirmActions: Boolean,
        baseUrl: String?,
        captureScreenshots: Boolean,
        sensitiveScreenGuard: Boolean,
    ) {
        // The only provider-specific line in the loop: the provider builds its
        // backend. Every other step works in neutral types.
        val backend: AgentBackend = provider.createBackend(credential, baseUrl)
        this.backend = backend
        val executor = ToolExecutor(service, captureScreenshots, sensitiveScreenGuard)
        // Live deltas render exactly as before: text to the assistant line (and
        // narration), thinking to the thinking line. Runs on the loop thread.
        val sink = object : BackendSink {
            override fun onTextDelta(text: String) {
                log(KIND_ASSISTANT, text, append = true)
                if (speakNarration) {
                    VoiceNarrator.onAssistantDelta(text)
                }
            }

            override fun onThinkingDelta(text: String) {
                log(KIND_THINKING, text, append = true)
            }
        }
        try {
            var turns = 0
            var noProgressTurns = 0
            while (turns < MAX_TOOL_TURNS) {
                turns++
                if (cancelRequested) return finish(stopReasonLabel())

                val turn = try {
                    streamTurnWithRetry(backend, model, sink)
                } catch (error: BackendStreamException) {
                    if (cancelRequested) return finish(stopReasonLabel())
                    return fail(str(R.string.agent_note_stream_error, error.message ?: ""))
                }
                if (cancelRequested) return finish(stopReasonLabel())

                if (speakNarration) {
                    VoiceNarrator.onMessageEnd()
                }
                appendAssistant(turn.assistant)

                when (turn.stopReason) {
                    AgentStopReason.REFUSAL -> {
                        // Surface the model's actual reason (when present) so the user can
                        // see why it declined, not just that it did.
                        val reason = turn.refusalDetail.trim().let { if (it.isEmpty()) "" else " ($it)" }
                        log(KIND_NOTE, "⚠ " + str(R.string.agent_note_refusal, reason))
                        return finish("refusal")
                    }
                    AgentStopReason.TOOL_USE -> {
                        val toolUses = turn.assistant.content.filterIsInstance<AgentContent.ToolUse>()
                        if (toolUses.isEmpty()) return finish("end_turn")
                        val denied = confirmActions && needsConfirmation(toolUses) &&
                            !ConfirmationSheet.ask(service, batchSummary(executor, toolUses))
                        val results = mutableListOf<AgentContent.ToolResult>()
                        var interrupted = false
                        var handedOff = false
                        for (toolUse in toolUses) {
                            if (cancelRequested) {
                                interrupted = true
                                break
                            }
                            if (denied) {
                                results.add(deniedResult(toolUse))
                                continue
                            }
                            // Phase 6.5c-2/3b: on a password/payment screen, either act under a
                            // per-app trust grant (non-secret control only) or hand off to the human.
                            // The agent never types the secret. Continue ends the batch so the model
                            // re-observes; Stop ends the task; Allow once/session grants trust.
                            if (executor.sensitivityBlock(toolUse.name, argsOf(toolUse))) {
                                val args = argsOf(toolUse)
                                val pkg = executor.snapshotPackage(args)
                                // A grant relaxes only the SCREEN-level gate; an action aimed at a
                                // real secret field always hands off, trusted app or not.
                                val elementSecret = executor.targetsSecretField(toolUse.name, args)
                                if (!elementSecret && pkg != null && isTrusted(pkg)) {
                                    if (!riskGatePasses(service, executor, toolUse, confirmActions)) {
                                        results.add(deniedResult(toolUse))
                                        continue
                                    }
                                    results.add(executeTool(executor, toolUse, bypassGuard = true))
                                    recordTrustedAction(executor, toolUse, pkg, scopeFor(pkg))
                                    continue
                                }
                                val kind = executor.sensitivityKind(args)
                                // 6.5c-2b login tap-to-fill: focus the credential field so the user's
                                // OS autofill chip appears (focus only — never reads/types the secret).
                                if (kind == SensitiveScreenDetector.Kind.PASSWORD ||
                                    kind == SensitiveScreenDetector.Kind.BOTH
                                ) {
                                    ActionExecutor.focusFirstSecureField(service)
                                }
                                log(KIND_NOTE, "🔒 " + str(R.string.agent_note_handoff))
                                when (HandoffSheet.await(service, handoffBody(kind), allowGrants = !elementSecret)) {
                                    HandoffSheet.Result.STOP -> {
                                        interrupted = true
                                        break
                                    }
                                    HandoffSheet.Result.CONTINUE -> {
                                        log(KIND_NOTE, str(R.string.agent_note_handoff_done))
                                        results.add(handedOffResult(toolUse))
                                        handedOff = true
                                        break
                                    }
                                    HandoffSheet.Result.GRANT_SESSION -> {
                                        if (pkg != null) synchronized(sessionGrants) { sessionGrants.add(pkg) }
                                        results.add(executeTool(executor, toolUse, bypassGuard = true))
                                        if (pkg != null) recordTrustedAction(executor, toolUse, pkg, GrantScope.SESSION)
                                    }
                                    HandoffSheet.Result.GRANT_ONCE -> {
                                        results.add(executeTool(executor, toolUse, bypassGuard = true))
                                        if (pkg != null) recordTrustedAction(executor, toolUse, pkg, GrantScope.ONCE)
                                    }
                                    HandoffSheet.Result.GRANT_PERSISTENT -> {
                                        if (pkg != null) trustStore?.grant(pkg, System.currentTimeMillis())
                                        results.add(executeTool(executor, toolUse, bypassGuard = true))
                                        if (pkg != null) recordTrustedAction(executor, toolUse, pkg, GrantScope.PERSISTENT)
                                    }
                                }
                                if (cancelRequested) {
                                    interrupted = true
                                    break
                                }
                                continue
                            }
                            // 6.5c-5: even on a non-sensitive screen, an irreversible/flagged action
                            // gets a one-tap confirm when per-action confirmation is otherwise off.
                            if (!riskGatePasses(service, executor, toolUse, confirmActions)) {
                                results.add(deniedResult(toolUse))
                                continue
                            }
                            results.add(executeTool(executor, toolUse))
                        }
                        // Every tool_use must get a tool_result or the next
                        // request 400s ("tool_use without tool_result"); fill any
                        // the interruption / handoff skipped so the conversation stays
                        // resumable (handoff fills with a non-error "user handled it").
                        val filler: (AgentContent.ToolUse) -> AgentContent.ToolResult =
                            if (interrupted) ::interruptedResult else ::handedOffResult
                        for (index in results.size until toolUses.size) {
                            results.add(filler(toolUses[index]))
                        }
                        appendToolResults(results)
                        if (denied) {
                            log(KIND_NOTE, str(R.string.agent_note_denied))
                        } else if (!interrupted && !handedOff) {
                            // Phase 6.3b: stop gracefully if actions stop moving the screen,
                            // rather than spinning the same no-op to the turn cap.
                            when (executor.actionChanged) {
                                true -> noProgressTurns = 0
                                false -> {
                                    noProgressTurns++
                                    if (noProgressTurns >= STUCK_THRESHOLD) {
                                        log(KIND_NOTE, "⚠ " + str(R.string.agent_note_stuck))
                                        return finish("stuck")
                                    }
                                }
                                null -> {} // no action ran this turn (reads only)
                            }
                        }
                        if (interrupted) return finish(stopReasonLabel())
                    }
                    AgentStopReason.PAUSE_TURN -> {
                        // Server-side pause: re-send and the API resumes the turn.
                    }
                    AgentStopReason.MAX_TOKENS -> {
                        // A truncated turn may end in a tool_use; close it out
                        // so "continue" doesn't 400.
                        fillDanglingToolUses(turn.assistant)
                        return finish("max_tokens")
                    }
                    AgentStopReason.END_TURN -> return finish("end_turn")
                }
            }
            fail(str(R.string.agent_note_max_turns, MAX_TOOL_TURNS))
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
            clearTransientStatus()
            backend.close()
            this.backend = null
            isRunning = false
            listener?.onTaskStateChanged(false)
        }
    }

    /** Snapshot the neutral history for one request, under the same lock buildParams held. */
    @Synchronized
    private fun snapshotHistory(): List<AgentMessage> = history.map { it.message }

    private fun stopReasonLabel(): String = if (pausedByTouch) "touched" else "stopped"

    private val mutatingTools = setOf(
        "click", "drag", "scroll", "type_text", "press_key", "set_value", "perform_secondary_action",
    )

    private fun needsConfirmation(toolUses: List<AgentContent.ToolUse>): Boolean =
        toolUses.any { it.name in mutatingTools }

    /** Single decode of a tool_use's input, shared by the summary and execution paths. */
    private fun argsOf(toolUse: AgentContent.ToolUse): JSONObject = try {
        JSONObject(toolUse.argsJson)
    } catch (_: Exception) {
        JSONObject()
    }

    private fun batchSummary(executor: ToolExecutor, toolUses: List<AgentContent.ToolUse>): String =
        toolUses.joinToString("\n") { toolUse ->
            "• ${toolUse.name} ${executor.describeAction(toolUse.name, argsOf(toolUse))}".trimEnd()
        }

    private fun deniedResult(toolUse: AgentContent.ToolUse): AgentContent.ToolResult =
        errorResult(toolUse, "The user declined this action. Ask how they would like to proceed instead of retrying.")

    private fun interruptedResult(toolUse: AgentContent.ToolUse): AgentContent.ToolResult =
        errorResult(toolUse, "This action was not performed because the task was interrupted.")

    /**
     * Phase 6.5c-2: the result for a tool the human took over. Not an error — the handoff
     * succeeded — so the model doesn't retry the gated action; it re-observes and continues.
     */
    private fun handedOffResult(toolUse: AgentContent.ToolUse): AgentContent.ToolResult =
        AgentContent.ToolResult(
            toolUseId = toolUse.id,
            text = "A password/payment field was on screen, so the user entered it themselves — " +
                "its contents are hidden from you. Call get_app_state to see the current screen, " +
                "then continue with the next non-secret step or finish.",
            isError = false,
        )

    /**
     * 6.5c-5 risk gate: an irreversible/externally-visible action (send/pay/delete/post…) or an
     * action on an injection-flagged screen is confirmed with a one-tap prompt — **even under a
     * per-app grant**. Skipped when [confirmActions] is on (the per-batch confirm already covered
     * this turn). Returns true to proceed, false if the user declined.
     */
    private fun riskGatePasses(
        service: CompanionService,
        executor: ToolExecutor,
        toolUse: AgentContent.ToolUse,
        confirmActions: Boolean,
    ): Boolean {
        if (confirmActions) return true
        val label = executor.describeAction(toolUse.name, argsOf(toolUse))
        val needsConfirm = RiskClassifier.isHighRisk(toolUse.name, label) || lastPerceptionFlagged
        if (!needsConfirm) return true
        log(KIND_NOTE, "⚠ " + str(R.string.agent_note_risk_confirm))
        return ConfirmationSheet.ask(service, label)
    }

    /** 6.5c-3b: true when [pkg] has an active session or persistent trust grant. */
    private fun isTrusted(pkg: String): Boolean {
        val session = synchronized(sessionGrants) { sessionGrants.contains(pkg) }
        if (session) return true
        return trustStore?.activeGrants(System.currentTimeMillis())?.contains(pkg) == true
    }

    private fun scopeFor(pkg: String): GrantScope =
        if (synchronized(sessionGrants) { sessionGrants.contains(pkg) }) GrantScope.SESSION else GrantScope.PERSISTENT

    /** Audit a trusted-app action (text-only) and keep a persistent grant from decaying. */
    private fun recordTrustedAction(
        executor: ToolExecutor,
        toolUse: AgentContent.ToolUse,
        pkg: String,
        scope: GrantScope,
    ) {
        val now = System.currentTimeMillis()
        trustStore?.touch(pkg, now) // no-op unless pkg has a persistent grant
        auditLog?.record(
            AuditEntry(pkg, toolUse.name, executor.describeAction(toolUse.name, argsOf(toolUse)), now, scope),
        )
        log(KIND_NOTE, str(R.string.agent_note_trusted_action, pkg))
    }

    /** Auth-aware takeover copy for the handoff overlay (6.5c-2). */
    private fun handoffBody(kind: SensitiveScreenDetector.Kind?): String =
        when (kind) {
            SensitiveScreenDetector.Kind.PASSWORD -> str(R.string.handoff_body_password)
            SensitiveScreenDetector.Kind.PAYMENT -> str(R.string.handoff_body_payment)
            else -> str(R.string.handoff_body_both)
        }

    private fun errorResult(toolUse: AgentContent.ToolUse, text: String): AgentContent.ToolResult =
        AgentContent.ToolResult(toolUseId = toolUse.id, text = text, isError = true)

    /** Closes out any tool_use blocks in [message] with interrupted results. */
    private fun fillDanglingToolUses(message: AgentMessage) {
        val toolUses = message.content.filterIsInstance<AgentContent.ToolUse>()
        if (toolUses.isNotEmpty()) {
            appendToolResults(toolUses.map { interruptedResult(it) })
        }
    }

    private fun executeTool(
        executor: ToolExecutor,
        toolUse: AgentContent.ToolUse,
        bypassGuard: Boolean = false,
    ): AgentContent.ToolResult {
        val args = argsOf(toolUse)
        val summary = executor.describeAction(toolUse.name, args)
        log(KIND_TOOL, "▸ ${toolUse.name} $summary".trimEnd())
        val outcome = executor.callTool(toolUse.name, args, bypassSensitivityGuard = bypassGuard)
        if (outcome.isError) {
            log(KIND_TOOL, "✗ ${toolUse.name} failed — the agent will see the error and adapt")
        }
        outcome.screenshotPngBase64?.let { png ->
            latestScreenshotBase64 = png
            latestTapNormalized = executor.lastTapNormalized
            latestGesturesNormalized = executor.lastGesturesNormalized
            listener?.onScreenshotCaptured(png)
        }
        // 6.5c-4: flag indirect prompt injection in the (untrusted) screen text the model
        // is about to read. Only annotates a tripped result, so normal snapshots are unchanged.
        // 6.5c-5: remember the flag so the next action is risk-confirmed.
        val rawText = outcome.text.ifEmpty { "(empty)" }
        val flagged = !outcome.isError && InjectionHeuristic.isSuspicious(rawText)
        lastPerceptionFlagged = flagged
        val resultText = if (flagged) InjectionHeuristic.WARNING + rawText else rawText
        return AgentContent.ToolResult(
            toolUseId = toolUse.id,
            text = resultText,
            isError = outcome.isError,
            image = outcome.screenshotPngBase64?.let { ToolImage(it) },
        )
    }

    @Synchronized
    private fun appendAssistant(message: AgentMessage) {
        history.add(HistoryEntry(message))
    }

    /**
     * Appends a tool-result user message. Only entries that actually carry a
     * screenshot count toward [RECENT_IMAGE_WINDOW]; older image-bearing
     * entries are rewritten in place to drop the screenshot (and the heavy
     * image bytes with it), bounding context and heap growth.
     */
    @Synchronized
    private fun appendToolResults(results: List<AgentContent.ToolResult>) {
        val hadImage = results.any { it.image != null }
        history.add(HistoryEntry(AgentMessage(AgentRole.USER, results), hadImage))

        val live = history.filter { it.hadImage && !it.pruned }
        if (live.size > RECENT_IMAGE_WINDOW) {
            for (entry in live.dropLast(RECENT_IMAGE_WINDOW)) {
                entry.message = stripScreenshots(entry.message)
                entry.pruned = true
            }
        }
    }

    /** Rewrites a tool-result message without image bytes, marking each result that lost one. */
    private fun stripScreenshots(message: AgentMessage): AgentMessage {
        val rewritten = message.content.map { block ->
            if (block is AgentContent.ToolResult && block.image != null) {
                block.copy(image = null, omittedImage = true)
            } else {
                block
            }
        }
        return AgentMessage(AgentRole.USER, rewritten)
    }

    private fun userMessage(text: String): AgentMessage =
        AgentMessage(AgentRole.USER, listOf(AgentContent.Text(text)))

    /**
     * Phase 6.3a: stream a turn, retrying transient failures (rate-limit / 5xx /
     * connection drops) with exponential backoff. Retries only on a *clean* attempt
     * — one that streamed no partial output — so a mid-stream failure never
     * double-emits text; those, and permanent errors, propagate to the caller's
     * existing fail paths. Wakes promptly on Stop. Per [RetryPolicy.MAX_ATTEMPTS].
     */
    private fun streamTurnWithRetry(backend: AgentBackend, model: String, sink: BackendSink): CompletedTurn {
        var attempt = 0
        while (true) {
            attempt++
            var emitted = false
            val guarded = object : BackendSink {
                override fun onTextDelta(text: String) {
                    emitted = true
                    sink.onTextDelta(text)
                }

                override fun onThinkingDelta(text: String) {
                    emitted = true
                    sink.onThinkingDelta(text)
                }
            }
            try {
                val turn = backend.streamTurn(
                    BackendRequest(model, AgentTools.SYSTEM_PROMPT, AgentTools.specs(), snapshotHistory()),
                    guarded,
                )
                clearTransientStatus()
                return turn
            } catch (error: Exception) {
                val retryable = !cancelRequested && !emitted &&
                    attempt < RetryPolicy.MAX_ATTEMPTS && RetryPolicy.isTransient(error)
                if (!retryable) {
                    clearTransientStatus()
                    throw error
                }
                setTransientStatus(retryStatus(error, attempt + 1))
                if (!interruptibleWait(RetryPolicy.backoffMs(attempt))) {
                    clearTransientStatus()
                    throw error
                }
            }
        }
    }

    private fun retryStatus(error: Throwable, nextAttempt: Int): String {
        val rateLimited = (error.message ?: "").contains("429") ||
            (error.message ?: "").contains("rate", ignoreCase = true)
        val res = if (rateLimited) R.string.agent_status_rate_limited else R.string.agent_status_retrying
        return str(res, nextAttempt, RetryPolicy.MAX_ATTEMPTS)
    }

    /** Sleep [totalMs] in small slices; returns false if [cancelRequested] trips. */
    private fun interruptibleWait(totalMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + totalMs
        while (System.currentTimeMillis() < deadline) {
            if (cancelRequested) return false
            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {
                return false
            }
        }
        return !cancelRequested
    }

    private fun setTransientStatus(status: String) {
        transientStatus = status
        listener?.onStatusChanged(status)
    }

    private fun clearTransientStatus() {
        if (transientStatus != null) {
            transientStatus = null
            listener?.onStatusChanged(null)
        }
    }

    private fun finish(reason: String) {
        when (reason) {
            "touched" -> log(KIND_NOTE, str(R.string.agent_note_paused))
            "stopped" -> log(KIND_NOTE, str(R.string.agent_note_stopped))
            "max_tokens" -> log(KIND_NOTE, str(R.string.agent_note_max_tokens))
        }
    }

    private fun fail(message: String) {
        log(KIND_NOTE, "⚠ $message")
    }
}
