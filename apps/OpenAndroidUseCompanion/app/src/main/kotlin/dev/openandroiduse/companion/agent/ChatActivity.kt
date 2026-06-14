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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.openandroiduse.companion.CompanionService
import dev.openandroiduse.companion.Readiness
import dev.openandroiduse.companion.readiness
import dev.openandroiduse.companion.ui.theme.OpenAndroidUseTheme
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
    private lateinit var sessions: SessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AgentSettings(this)
        sessions = SessionStore(this)
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
                    onInputChange = { input = it },
                    onSend = ::sendTask,
                    onStop = {
                        AgentController.requestStop()
                    },
                    onMic = ::startListening,
                    onNewConversation = {
                        persistCurrentSession()
                        AgentController.newConversation()
                        messages = emptyList()
                        agentView = null
                        tapPoint = null
                        recentSessions = sessions.list()
                    },
                    onShare = ::exportConversation,
                    onResumeSession = ::resumeSession,
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onOpenHistory = { startActivity(Intent(this, SessionsActivity::class.java)) },
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onExpandView = { expandView = true },
                )
                val shot = agentView
                if (expandView && shot != null) {
                    Dialog(onDismissRequest = { expandView = false }) {
                        Image(
                            bitmap = shot,
                            contentDescription = "What the agent sees, enlarged",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AgentController.listener = this
        running = AgentController.isRunning
        serviceOn = CompanionService.isRunning
        hasKey = settings.hasApiKey()
        messages = AgentController.transcriptSnapshot()
        AgentController.latestScreenshotBase64?.let { decodeAgentView(it) }
        tapPoint = AgentController.latestTapNormalized
        recentSessions = sessions.list()
        if (hasKey) {
            Thread({ ModelCatalog.refresh(settings) }, "oau-model-refresh").start()
        }
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

    /** Persist the in-memory conversation (text-only) for History; no-op if empty. */
    private fun persistCurrentSession() {
        AgentController.snapshotForPersistence()?.let { sessions.save(it) }
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
            Toast.makeText(this, "Nothing to export yet", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Couldn't prepare the export", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, "$title.md")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Export conversation"))
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
            Toast.makeText(this, "Speech recognition is not available", Toast.LENGTH_SHORT).show()
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
    "Open Settings and tell me the Android version",
    "Turn Bluetooth on",
    "Summarize my latest notification",
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
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMic: () -> Unit,
    onNewConversation: () -> Unit,
    onShare: () -> Unit,
    onResumeSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onExpandView: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Agent")
                        Spacer(Modifier.width(8.dp))
                        ModelChip(modelLabel, onOpenSettings)
                    }
                },
                actions = {
                    TextButton(onClick = onOpenHistory) { Text("History") }
                    TextButton(onClick = onShare) { Text("Export") }
                    TextButton(onClick = onNewConversation) { Text("New") }
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
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(messages) { _, entry ->
                        MessageItem(entry.first, entry.second, onOpenSettings, onOpenAccessibility)
                    }
                }
            }
        }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("What the agent sees", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onStop) { Text("Stop") }
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
                        contentDescription = "Latest screenshot the agent captured",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (tapPoint != null) {
                        val (fx, fy) = tapPoint
                        Canvas(Modifier.fillMaxSize()) {
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
                    "You're the agent's second pair of eyes — its view will appear here as it works.",
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
        Text("Your second pair of hands", style = MaterialTheme.typography.titleLarge)
        Text(
            "Ask me to do something on this phone — I'll narrate and act, and you can stop me any time.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        // Quick re-run: pick up a recent conversation right where you left off.
        val recent = recentSessions.filterNot { it.archived }.take(3)
        if (recent.isNotEmpty()) {
            Spacer(Modifier.heightIn(min = 8.dp))
            Text("Pick up where you left off:", style = MaterialTheme.typography.labelLarge)
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
        Text("Try:", style = MaterialTheme.typography.labelLarge)
        SUGGESTED_PROMPTS.forEach { prompt ->
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
        Readiness.NEEDS_KEY -> Triple("Add your API key to run tasks.", "Add key", onOpenSettings)
        Readiness.NEEDS_ACCESSIBILITY -> Triple("Enable the accessibility service so I can act.", "Enable", onOpenAccessibility)
        else -> Triple("Enable accessibility and add an API key to start.", "Fix", onOpenAccessibility)
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
            placeholder = { Text("What should I do on this phone?") },
            enabled = !running,
            maxLines = 4,
        )
        TextButton(onClick = onMic, enabled = !running) { Text(if (listening) "…" else "🎤") }
        if (running) {
            Button(onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onStop() }) { Text("Stop") }
        } else {
            Button(
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onSend() },
                enabled = input.isNotBlank(),
            ) { Text("Send") }
        }
    }
}

@Composable
private fun MessageItem(kind: String, text: String, onOpenSettings: () -> Unit, onOpenAccessibility: () -> Unit) {
    when (kind) {
        AgentController.KIND_USER -> Bubble(text, user = true)
        AgentController.KIND_ASSISTANT -> AssistantBubble(text)
        AgentController.KIND_THINKING -> ThinkingBlock(text)
        AgentController.KIND_TOOL -> ToolChip(text)
        else -> NoteCard(text, onOpenSettings, onOpenAccessibility)
    }
}

@Composable
private fun Bubble(text: String, user: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(0.88f),
        ) {
            Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AssistantBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            SelectionContainer {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ChatMarkdown.parse(text).forEach { block -> MarkdownBlock(block) }
                }
            }
        }
    }
}

@Composable
private fun MarkdownBlock(block: MdBlock) {
    when (block) {
        is MdBlock.Paragraph -> Text(block.spans.toAnnotated(), style = MaterialTheme.typography.bodyMedium)
        is MdBlock.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            block.items.forEach { spans ->
                Row { Text("•  "); Text(spans.toAnnotated(), style = MaterialTheme.typography.bodyMedium) }
            }
        }
        is MdBlock.NumberedList -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            block.items.forEachIndexed { i, spans ->
                Row { Text("${i + 1}.  "); Text(spans.toAnnotated(), style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

private fun List<MdSpan>.toAnnotated(): AnnotatedString = buildAnnotatedString {
    this@toAnnotated.forEach { span ->
        val style = SpanStyle(
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            fontFamily = if (span.code) FontFamily.Monospace else null,
        )
        withStyle(style) { append(span.text) }
    }
}

@Composable
private fun ThinkingBlock(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Hide thinking" else "Show thinking", style = MaterialTheme.typography.labelMedium)
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
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            ToolChipLabel.describe(raw),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun NoteCard(text: String, onOpenSettings: () -> Unit, onOpenAccessibility: () -> Unit) {
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
                NoteStyle.NEEDS_KEY -> TextButton(onClick = onOpenSettings) { Text("Add API key") }
                NoteStyle.NEEDS_ACCESSIBILITY -> TextButton(onClick = onOpenAccessibility) { Text("Enable service") }
                else -> Unit
            }
        }
    }
}

