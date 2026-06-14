package dev.openandroiduse.companion.agent

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.openandroiduse.companion.CompanionService
import dev.openandroiduse.companion.R
import dev.openandroiduse.companion.Readiness
import dev.openandroiduse.companion.readiness
import dev.openandroiduse.companion.ui.markHeading
import dev.openandroiduse.companion.ui.theme.OpenAndroidUseTheme
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The conversation surface for the on-device agent (Phase 4.3, Compose). The
 * agent core (AgentController) stays the source of truth; this renders its
 * transcript, streams it live, shows "what the agent sees", and degrades
 * gracefully when a prerequisite is missing.
 */
class ChatActivity : ComponentActivity(), AgentController.Listener {

    private lateinit var settings: AgentSettings
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingSync = AtomicBoolean(false)
    private var speechRecognizer: android.speech.SpeechRecognizer? = null
    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListening()
        }
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var messages by mutableStateOf<List<Pair<String, String>>>(emptyList())
    private var running by mutableStateOf(false)
    private var serviceOn by mutableStateOf(false)
    private var hasKey by mutableStateOf(false)
    private var listening by mutableStateOf(false)
    private var input by mutableStateOf("")
    private var agentView by mutableStateOf<ImageBitmap?>(null)
    private var tapPoint by mutableStateOf<Pair<Float, Float>?>(null)
    private var expandView by mutableStateOf(false)
    private var recentSessions by mutableStateOf<List<SessionMeta>>(emptyList())
    /** Full session list for the tablet/foldable two-pane History pane (4.6e). */
    private var sessionList by mutableStateOf<List<SessionMeta>>(emptyList())
    private lateinit var sessions: SessionStore

    /** The dynamic-color value this instance was themed with, to detect a Settings toggle. */
    private var appliedDynamicColor = false

    /** Transcript revision last written to SessionStore, so onPause doesn't re-save no-ops. */
    private var lastSavedRevision = -1

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AgentSettings(this)
        sessions = SessionStore(this)
        appliedDynamicColor = settings.dynamicColor
        enableEdgeToEdge()
        // Resume a saved session if launched from History (rebuilds context).
        intent.getStringExtra(EXTRA_SESSION_ID)?.let { id ->
            if (!AgentController.isRunning) sessions.load(id)?.let { AgentController.restore(it) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            OpenAndroidUseTheme(dynamicColor = settings.dynamicColor) {
                val widthClass = calculateWindowSizeClass(this).widthSizeClass
                if (TwoPane.isTwoPane(widthClass)) {
                    Row(Modifier.fillMaxSize()) {
                        HistoryPane(
                            sessions = sessionList,
                            onResume = ::resumeSession,
                            onRename = ::renameSessionFromPane,
                            onArchive = { id, archived -> sessions.setArchived(id, archived); sessionList = sessions.list() },
                            onPin = { id, pinned -> sessions.setPinned(id, pinned); AgentController.notePinned(id, pinned); sessionList = sessions.list() },
                            onDelete = ::deleteSessionFromPane,
                            onRestore = { payload -> sessions.save(payload); sessionList = sessions.list() },
                            modifier = Modifier.width(360.dp),
                        )
                        VerticalDivider()
                        ChatContent(Modifier.weight(1f), showHistory = false)
                    }
                } else {
                    ChatContent()
                }
                val shot = agentView
                if (expandView && shot != null) {
                    Dialog(onDismissRequest = { expandView = false }) {
                        Image(
                            bitmap = shot,
                            contentDescription = stringResource(R.string.chat_agent_view_enlarged),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleTask: a resume from History re-enters this one instance instead of
        // stacking a duplicate ChatActivity. Load the requested session.
        intent.getStringExtra(EXTRA_SESSION_ID)?.let { resumeSession(it) }
    }

    override fun onResume() {
        super.onResume()
        // A Material You toggle in Settings changes the theme this instance was built
        // with; rebuild so the brand/system palette applies without an app restart.
        if (settings.dynamicColor != appliedDynamicColor) {
            recreate()
            return
        }
        AgentController.listener = this
        running = AgentController.isRunning
        serviceOn = CompanionService.isRunning
        hasKey = settings.hasApiKey()
        messages = AgentController.transcriptSnapshot()
        AgentController.latestScreenshotBase64?.let { decodeAgentView(it) }
        tapPoint = AgentController.latestTapNormalized
        recentSessions = sessions.list()
        sessionList = recentSessions
        if (hasKey) {
            Thread({ ModelCatalog.refresh(settings) }, "oau-model-refresh").start()
        }
    }

    /** The chat surface; reused as the single screen and as the detail pane in two-pane. */
    @Composable
    private fun ChatContent(modifier: Modifier = Modifier, showHistory: Boolean = true) {
        ChatScreen(
            messages = messages,
            running = running,
            readiness = readiness(serviceOn, hasKey),
            listening = listening,
            input = input,
            agentView = agentView,
            tapPoint = tapPoint,
            modelLabel = settings.model,
            recentSessions = recentSessions,
            modifier = modifier,
            showHistoryAction = showHistory,
            onInputChange = { input = it },
            onSend = ::sendTask,
            onStop = { AgentController.requestStop() },
            onMic = ::startListening,
            onNewConversation = {
                persistCurrentSession()
                AgentController.newConversation()
                messages = emptyList()
                agentView = null
                tapPoint = null
                recentSessions = sessions.list()
                sessionList = recentSessions
            },
            onExport = ::exportConversation,
            onShareMessage = ::shareMessage,
            onRetry = ::retryLastTask,
            onResumeSession = ::resumeSession,
            onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
            onOpenHistory = { startActivity(Intent(this, SessionsActivity::class.java)) },
            onOpenAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            onExpandView = { expandView = true },
        )
    }

    private fun renameSessionFromPane(id: String, title: String) {
        sessions.rename(id, title)
        AgentController.noteRenamed(id, title)
        sessionList = sessions.list()
    }

    private fun deleteSessionFromPane(id: String): SessionPayload? {
        if (AgentController.isRunning && id == AgentController.currentSessionId) {
            Toast.makeText(this, getString(R.string.sessions_busy_delete), Toast.LENGTH_SHORT).show()
            return null
        }
        val removed = sessions.load(id)
        sessions.delete(id)
        sessionList = sessions.list()
        return removed
    }

    override fun onPause() {
        super.onPause()
        // Persist the conversation so it survives the process and shows in History.
        persistCurrentSession()
        if (AgentController.listener === this) AgentController.listener = null
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    // --- AgentController.Listener (loop thread → main) ---

    override fun onTaskStateChanged(running: Boolean) {
        mainHandler.post {
            this.running = running
            // A finished task is a natural save point.
            if (!running) {
                persistCurrentSession()
                recentSessions = sessions.list()
                sessionList = recentSessions
            }
        }
    }

    override fun onTranscriptChanged() {
        // Coalesce high-frequency streaming callbacks to ~30fps.
        if (pendingSync.compareAndSet(false, true)) {
            mainHandler.postDelayed({
                pendingSync.set(false)
                messages = AgentController.transcriptSnapshot()
            }, 33)
        }
    }

    override fun onScreenshotCaptured(pngBase64: String) {
        // Already off the main thread (loop thread): decode here, post the bitmap.
        decodeAgentView(pngBase64)
        mainHandler.post { tapPoint = AgentController.latestTapNormalized }
    }

    private fun decodeAgentView(b64: String) {
        runCatching {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()?.let { bmp -> mainHandler.post { agentView = bmp } }
    }

    private fun sendTask() {
        val text = input.trim()
        if (text.isEmpty()) return
        // Graceful degradation: surface what's missing + the fix; keep the text.
        when (readiness(CompanionService.isRunning, settings.hasApiKey())) {
            Readiness.NEEDS_KEY, Readiness.NEEDS_BOTH -> {
                startActivity(Intent(this, SettingsActivity::class.java)); return
            }
            Readiness.NEEDS_ACCESSIBILITY -> {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); return
            }
            Readiness.READY -> Unit
        }
        input = ""
        AgentController.startTask(text, settings)
    }

    /** Re-run the most recent user task after a failure (Phase 4.7b-2 error → Retry). */
    private fun retryLastTask() {
        if (AgentController.isRunning) return
        val lastUser = messages.lastOrNull { it.first == AgentController.KIND_USER }?.second ?: return
        when (readiness(CompanionService.isRunning, settings.hasApiKey())) {
            Readiness.NEEDS_KEY, Readiness.NEEDS_BOTH -> {
                startActivity(Intent(this, SettingsActivity::class.java)); return
            }
            Readiness.NEEDS_ACCESSIBILITY -> {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); return
            }
            Readiness.READY -> Unit
        }
        AgentController.startTask(lastUser, settings)
    }

    /**
     * Persist the in-memory conversation (text-only) for History; no-op if empty or
     * unchanged since the last save, so merely opening Settings/History doesn't bump
     * the session's updatedAt and reshuffle the list.
     */
    private fun persistCurrentSession() {
        val revision = AgentController.transcriptRevision
        if (revision == lastSavedRevision) return
        AgentController.snapshotForPersistence()?.let {
            sessions.save(it)
            lastSavedRevision = revision
        }
    }

    private fun resumeSession(id: String) {
        if (AgentController.isRunning) {
            Toast.makeText(this, "Stop the current task first", Toast.LENGTH_SHORT).show()
            return
        }
        persistCurrentSession()
        sessions.load(id)?.let { AgentController.restore(it) }
        messages = AgentController.transcriptSnapshot()
        agentView = null
        tapPoint = null
    }

    /** Export the whole conversation as a Markdown file shared via FileProvider. */
    private fun exportConversation() {
        val lines = messages
        if (lines.isEmpty()) {
            Toast.makeText(this, getString(R.string.chat_nothing_to_export), Toast.LENGTH_SHORT).show()
            return
        }
        val title = lines.firstOrNull { it.first == AgentController.KIND_USER }
            ?.second?.let { SessionTitle.derive(it) } ?: SessionTitle.FALLBACK
        val markdown = ConversationExport.toMarkdown(title, lines)
        val uri = runCatching {
            val dir = java.io.File(cacheDir, "exports").apply { mkdirs() }
            val file = java.io.File(dir, "conversation-${System.currentTimeMillis()}.md")
            file.writeText(markdown)
            androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        }.getOrElse {
            Toast.makeText(this, getString(R.string.chat_export_failed), Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, "$title.md")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.chat_export_chooser)))
    }

    /** Share a single message's text via the system chooser (Phase 4.7b). */
    private fun shareMessage(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.chat_msg_share_chooser)))
    }

    // --- push-to-talk ---

    private fun startListening() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestAudioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, getString(R.string.chat_speech_unavailable), Toast.LENGTH_SHORT).show()
            return
        }
        val recognizer = speechRecognizer
            ?: android.speech.SpeechRecognizer.createSpeechRecognizer(this).also {
                speechRecognizer = it
                it.setRecognitionListener(object : android.speech.RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        listening = false
                        results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()?.let { if (it.isNotBlank()) input = it }
                    }

                    override fun onError(error: Int) { listening = false }
                    override fun onReadyForSpeech(params: Bundle?) { listening = true }
                    override fun onEndOfSpeech() {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        recognizer.startListening(
            Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(
                android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            ),
        )
    }

    companion object {
        /** Intent extra: a SessionStore id to resume when the chat opens. */
        const val EXTRA_SESSION_ID = "session_id"
    }

}

private val SUGGESTED_PROMPTS = listOf(
    R.string.suggested_prompt_1,
    R.string.suggested_prompt_2,
    R.string.suggested_prompt_3,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    messages: List<Pair<String, String>>,
    running: Boolean,
    readiness: Readiness,
    listening: Boolean,
    input: String,
    agentView: ImageBitmap?,
    tapPoint: Pair<Float, Float>?,
    modelLabel: String,
    recentSessions: List<SessionMeta>,
    modifier: Modifier = Modifier,
    showHistoryAction: Boolean = true,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMic: () -> Unit,
    onNewConversation: () -> Unit,
    onExport: () -> Unit,
    onShareMessage: (String) -> Unit,
    onRetry: () -> Unit,
    onResumeSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onExpandView: () -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val copiedMessage = stringResource(R.string.chat_msg_copied)
    // True when the last message is (almost) in view — used to decide whether a new
    // message should auto-scroll (don't yank the user back down while they read history)
    // and whether to offer the scroll-to-latest affordance.
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 1
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && atBottom) {
            val last = messages.size - 1
            if (Motion.animationsDisabled(context)) listState.scrollToItem(last) else listState.animateScrollToItem(last)
        }
    }
    val onCopyMessage: (String) -> Unit = { text ->
        clipboard.setText(AnnotatedString(text))
        scope.launch { snackbarHost.showSnackbar(copiedMessage) }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.chat_title))
                        Spacer(Modifier.width(8.dp))
                        ModelChip(modelLabel, onOpenSettings)
                    }
                },
                actions = {
                    // In two-pane the History list is already on screen.
                    if (showHistoryAction) {
                        IconButton(onClick = onOpenHistory) {
                            Icon(Icons.Filled.History, contentDescription = stringResource(R.string.chat_history))
                        }
                    }
                    IconButton(onClick = onExport) {
                        Icon(Icons.Filled.IosShare, contentDescription = stringResource(R.string.chat_export))
                    }
                    IconButton(onClick = onNewConversation) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.chat_new))
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.imePadding()) {
                ReadinessBanner(readiness, onOpenSettings, onOpenAccessibility)
                Composer(input, running, listening, onInputChange, onSend, onStop, onMic)
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (running || agentView != null) {
                AgentViewCard(agentView, tapPoint, running, onStop, onExpandView)
            }
            if (messages.isEmpty()) {
                EmptyState(recentSessions, onResumeSession, onPrompt = onInputChange)
            } else {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(messages) { index, entry ->
                            // Offer Retry only on the last message and only when it's an
                            // error note and the agent is idle (re-runs the last user task).
                            val onRetryHere = if (!running && index == messages.lastIndex) onRetry else null
                            MessageItem(
                                entry.first,
                                entry.second,
                                onOpenSettings,
                                onOpenAccessibility,
                                onCopy = onCopyMessage,
                                onShare = onShareMessage,
                                onRetry = onRetryHere,
                            )
                        }
                        // A typing cue where the next answer will land — only while the
                        // agent is composing (not once its reply is already streaming in).
                        if (running && messages.lastOrNull()?.first != AgentController.KIND_ASSISTANT) {
                            item("typing") { TypingIndicator() }
                        }
                    }
                    // Jump-to-latest: appears only when the newest message is off-screen,
                    // so new turns no longer drag the user down mid-read.
                    if (!atBottom) {
                        SmallFloatingActionButton(
                            onClick = { scope.launch { listState.animateScrollToItem(messages.size - 1) } },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.chat_scroll_to_latest),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryPane(
    sessions: List<SessionMeta>,
    onResume: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onArchive: (String, Boolean) -> Unit,
    onPin: (String, Boolean) -> Unit,
    onDelete: (String) -> SessionPayload?,
    onRestore: (SessionPayload) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHost = remember { SnackbarHostState() }
    val deleteWithUndo = rememberDeleteWithUndo(snackbarHost, onDelete, onRestore)
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.sessions_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        SessionsList(
            sessions = sessions,
            onResume = onResume,
            onRename = onRename,
            onArchive = onArchive,
            onDelete = deleteWithUndo,
            onPin = onPin,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun ModelChip(model: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(
            model,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun AgentViewCard(
    view: ImageBitmap?,
    tapPoint: Pair<Float, Float>?,
    running: Boolean,
    onStop: () -> Unit,
    onExpand: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val statusDesc = stringResource(if (running) R.string.chat_status_working else R.string.chat_status_idle)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.chat_agent_view_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f)
                        // Announce agent start/stop to screen readers (the running
                        // state is otherwise conveyed only by the spinner).
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                            stateDescription = statusDesc
                        },
                )
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onStop) { Text(stringResource(R.string.action_stop)) }
                }
            }
            if (view != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clickable { onExpand() },
                ) {
                    Image(
                        bitmap = view,
                        contentDescription = stringResource(R.string.chat_screenshot_desc),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (tapPoint != null) {
                        val (fx, fy) = tapPoint
                        // Decorative tap marker — keep it out of the a11y tree.
                        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                            // Map the normalized point onto the Fit-scaled image rect.
                            val s = minOf(size.width / view.width, size.height / view.height)
                            val dw = view.width * s
                            val dh = view.height * s
                            val ox = (size.width - dw) / 2f
                            val oy = (size.height - dh) / 2f
                            val center = Offset(ox + fx * dw, oy + fy * dh)
                            drawCircle(Color(0xFF5EEAD4), radius = 11.dp.toPx(), center = center, style = Stroke(width = 3.dp.toPx()))
                            drawCircle(Color(0x335EEAD4), radius = 11.dp.toPx(), center = center)
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.chat_agent_view_placeholder),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    recentSessions: List<SessionMeta>,
    onResumeSession: (String) -> Unit,
    onPrompt: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.heightIn(min = 24.dp))
        Text(stringResource(R.string.chat_empty_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.markHeading())
        Text(
            stringResource(R.string.chat_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        // Quick re-run: pick up a recent conversation right where you left off.
        val recent = recentSessions.filterNot { it.archived }.take(3)
        if (recent.isNotEmpty()) {
            Spacer(Modifier.heightIn(min = 8.dp))
            Text(stringResource(R.string.chat_recent_title), style = MaterialTheme.typography.labelLarge)
            recent.forEach { meta ->
                OutlinedButton(
                    onClick = { onResumeSession(meta.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(meta.title, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.heightIn(min = 8.dp))
        Text(stringResource(R.string.chat_try), style = MaterialTheme.typography.labelLarge)
        SUGGESTED_PROMPTS.forEach { promptId ->
            val prompt = stringResource(promptId)
            OutlinedButton(onClick = { onPrompt(prompt) }, modifier = Modifier.fillMaxWidth()) {
                Text(prompt)
            }
        }
    }
}

@Composable
private fun ReadinessBanner(readiness: Readiness, onOpenSettings: () -> Unit, onOpenAccessibility: () -> Unit) {
    if (readiness == Readiness.READY) return
    val (msg, action, onAction) = when (readiness) {
        Readiness.NEEDS_KEY -> Triple(
            stringResource(R.string.chat_readiness_needs_key),
            stringResource(R.string.chat_readiness_add_key),
            onOpenSettings,
        )
        Readiness.NEEDS_ACCESSIBILITY -> Triple(
            stringResource(R.string.chat_readiness_needs_accessibility),
            stringResource(R.string.chat_readiness_enable),
            onOpenAccessibility,
        )
        else -> Triple(
            stringResource(R.string.chat_readiness_needs_both),
            stringResource(R.string.chat_readiness_fix),
            onOpenAccessibility,
        )
    }
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun Composer(
    input: String,
    running: Boolean,
    listening: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMic: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.chat_composer_placeholder)) },
            enabled = !running,
            maxLines = 4,
        )
        val micLabel = stringResource(if (listening) R.string.chat_mic_listening else R.string.chat_mic_voice_input)
        IconButton(onClick = onMic, enabled = !running) {
            Icon(Icons.Filled.Mic, contentDescription = micLabel)
        }
        if (running) {
            Button(onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onStop() }) { Text(stringResource(R.string.action_stop)) }
        } else {
            Button(
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onSend() },
                enabled = input.isNotBlank(),
            ) { Text(stringResource(R.string.chat_send)) }
        }
    }
}

@Composable
private fun MessageItem(
    kind: String,
    text: String,
    onOpenSettings: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    when (kind) {
        AgentController.KIND_USER -> Bubble(text, user = true, onCopy = onCopy, onShare = onShare)
        AgentController.KIND_ASSISTANT -> AssistantBubble(text, onCopy = onCopy, onShare = onShare)
        AgentController.KIND_THINKING -> ThinkingBlock(text)
        AgentController.KIND_TOOL -> ToolChip(text)
        else -> NoteCard(text, onOpenSettings, onOpenAccessibility, onRetry)
    }
}

/**
 * A chat "typing" cue (Phase 4.7b-2): an assistant-aligned bubble of pulsing dots while the
 * agent composes its reply. Reduce-motion shows static dots (the pulse is gated on
 * [Motion.animationsDisabled]); a contentDescription announces the working state without adding
 * a second live region (the Agent's-view card already announces start/stop).
 */
@Composable
private fun TypingIndicator() {
    val context = LocalContext.current
    val reduced = Motion.animationsDisabled(context)
    val transition = rememberInfiniteTransition(label = "typing")
    val desc = stringResource(R.string.chat_typing)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.semantics { contentDescription = desc },
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) { i ->
                    val alpha = if (reduced) 0.5f else {
                        val a by transition.animateFloat(
                            initialValue = 0.25f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 600, delayMillis = i * 160),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "dot$i",
                        )
                        a
                    }
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

/**
 * Long-press menu (Phase 4.7b) wrapping a message bubble: Copy / Share the message text.
 * Replaces in-bubble text selection with whole-message actions (partial selection is a
 * backlog trade-off); a haptic tick confirms the long-press opened the menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageActions(
    text: String,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var open by remember { mutableStateOf(false) }
    val actionsDesc = stringResource(R.string.chat_msg_actions)
    Box {
        Box(
            Modifier
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        open = true
                    },
                    onLongClickLabel = actionsDesc,
                ),
        ) { content() }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_msg_copy)) },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                onClick = { open = false; onCopy(text) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_msg_share)) },
                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                onClick = { open = false; onShare(text) },
            )
        }
    }
}

@Composable
private fun Bubble(text: String, user: Boolean, onCopy: (String) -> Unit, onShare: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        MessageActions(text, onCopy, onShare) {
            Surface(
                color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(0.88f).widthIn(max = 560.dp),
            ) {
                Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AssistantBubble(text: String, onCopy: (String) -> Unit, onShare: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        MessageActions(text, onCopy, onShare) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 640.dp),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ChatMarkdown.parse(text).forEach { block -> MarkdownBlock(block) }
                }
            }
        }
    }
}

@Composable
private fun MarkdownBlock(block: MdBlock) {
    val linkColor = MaterialTheme.colorScheme.primary
    when (block) {
        is MdBlock.Paragraph -> Text(block.spans.toAnnotated(linkColor), style = MaterialTheme.typography.bodyMedium)
        is MdBlock.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            block.items.forEach { spans ->
                Row { Text("•  "); Text(spans.toAnnotated(linkColor), style = MaterialTheme.typography.bodyMedium) }
            }
        }
        is MdBlock.NumberedList -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            block.items.forEachIndexed { i, spans ->
                Row { Text("${i + 1}.  "); Text(spans.toAnnotated(linkColor), style = MaterialTheme.typography.bodyMedium) }
            }
        }
        is MdBlock.Table -> MarkdownTable(block, linkColor)
    }
}

@Composable
private fun MarkdownTable(table: MdBlock.Table, linkColor: Color) {
    // Horizontal scroll so wide tables don't overflow narrow screens.
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        TableRow(table.header, linkColor, header = true)
        table.rows.forEach { TableRow(it, linkColor, header = false) }
    }
}

@Composable
private fun TableRow(cells: List<List<MdSpan>>, linkColor: Color, header: Boolean) {
    Row {
        cells.forEach { spans ->
            Text(
                spans.toAnnotated(linkColor),
                modifier = Modifier.width(140.dp).padding(horizontal = 6.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (header) FontWeight.Bold else null,
            )
        }
    }
}

private fun List<MdSpan>.toAnnotated(linkColor: Color): AnnotatedString = buildAnnotatedString {
    this@toAnnotated.forEach { span ->
        val style = SpanStyle(
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            fontFamily = if (span.code) FontFamily.Monospace else null,
        )
        val url = span.url
        if (url != null) {
            val linkStyles = TextLinkStyles(
                style = style.copy(color = linkColor, textDecoration = TextDecoration.Underline),
            )
            withLink(LinkAnnotation.Url(url, linkStyles)) { append(span.text) }
        } else {
            withStyle(style) { append(span.text) }
        }
    }
}

@Composable
private fun ThinkingBlock(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(
                stringResource(if (expanded) R.string.chat_hide_thinking else R.string.chat_show_thinking),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (expanded) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

@Composable
private fun ToolChip(raw: String) {
    val error = ToolChipLabel.isError(raw)
    val label = ToolChipLabel.describe(raw)
    // Failure is otherwise conveyed only by the error-container color; spell it
    // out for screen readers.
    val desc = if (error) stringResource(R.string.chat_tool_error_prefix, label) else label
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .semantics { contentDescription = desc },
        )
    }
}

@Composable
private fun NoteCard(
    text: String,
    onOpenSettings: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    val style = NoteClassifier.classify(text)
    val color = when (style) {
        NoteStyle.ERROR, NoteStyle.NEEDS_KEY -> MaterialTheme.colorScheme.errorContainer
        NoteStyle.PAUSED, NoteStyle.NEEDS_ACCESSIBILITY -> MaterialTheme.colorScheme.tertiaryContainer
        NoteStyle.INFO -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = color, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text, style = MaterialTheme.typography.bodySmall)
            when (style) {
                NoteStyle.NEEDS_KEY -> TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.chat_note_add_key)) }
                NoteStyle.NEEDS_ACCESSIBILITY -> TextButton(onClick = onOpenAccessibility) { Text(stringResource(R.string.chat_note_enable_service)) }
                // An error with the agent idle: offer to re-run the last task.
                NoteStyle.ERROR -> if (onRetry != null) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.chat_retry)) }
                }
                else -> Unit
            }
        }
    }
}

