package dev.openandroiduse.companion.agent

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
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

    private var messages by mutableStateOf<List<Pair<String, String>>>(emptyList())
    private var running by mutableStateOf(false)
    private var serviceOn by mutableStateOf(false)
    private var hasKey by mutableStateOf(false)
    private var listening by mutableStateOf(false)
    private var input by mutableStateOf("")
    private var agentView by mutableStateOf<ImageBitmap?>(null)
    private var showSettings by mutableStateOf(false)
    private var expandView by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AgentSettings(this)
        setContent {
            OpenAndroidUseTheme {
                ChatScreen(
                    messages = messages,
                    running = running,
                    readiness = readiness(serviceOn, hasKey),
                    listening = listening,
                    input = input,
                    agentView = agentView,
                    modelLabel = settings.model,
                    onInputChange = { input = it },
                    onSend = ::sendTask,
                    onStop = {
                        AgentController.requestStop()
                    },
                    onMic = ::startListening,
                    onNewConversation = {
                        AgentController.resetConversation()
                        messages = emptyList()
                        agentView = null
                    },
                    onShare = ::shareLastAnswer,
                    onOpenSettings = { showSettings = true },
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onExpandView = { expandView = true },
                )
                if (showSettings) {
                    SettingsDialog(settings = settings, onDismiss = { showSettings = false })
                }
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
        if (hasKey) {
            Thread({ ModelCatalog.refresh(settings) }, "oau-model-refresh").start()
        }
    }

    override fun onPause() {
        super.onPause()
        if (AgentController.listener === this) AgentController.listener = null
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    // --- AgentController.Listener (loop thread → main) ---

    override fun onTaskStateChanged(running: Boolean) {
        mainHandler.post { this.running = running }
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
            Readiness.NEEDS_KEY, Readiness.NEEDS_BOTH -> { showSettings = true; return }
            Readiness.NEEDS_ACCESSIBILITY -> {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); return
            }
            Readiness.READY -> Unit
        }
        input = ""
        AgentController.startTask(text, settings)
    }

    private fun shareLastAnswer() {
        val answer = messages.lastOrNull { it.first == AgentController.KIND_ASSISTANT }?.second
            ?: messages.lastOrNull()?.second ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, answer)
        }
        startActivity(Intent.createChooser(intent, "Share"))
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
    modelLabel: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMic: () -> Unit,
    onNewConversation: () -> Unit,
    onShare: () -> Unit,
    onOpenSettings: () -> Unit,
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
                    TextButton(onClick = onShare) { Text("Share") }
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
                AgentViewCard(agentView, running, onStop, onExpandView)
            }
            if (messages.isEmpty()) {
                EmptyState(onPrompt = onInputChange)
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
private fun AgentViewCard(view: ImageBitmap?, running: Boolean, onStop: () -> Unit, onExpand: () -> Unit) {
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
                Image(
                    bitmap = view,
                    contentDescription = "Latest screenshot the agent captured",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clickable { onExpand() },
                )
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
private fun EmptyState(onPrompt: (String) -> Unit) {
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

@Composable
private fun SettingsDialog(settings: AgentSettings, onDismiss: () -> Unit) {
    var apiKey by remember { mutableStateOf("") }
    val models = remember { settings.availableModels().let { if (settings.model in it) it else listOf(settings.model) + it } }
    var model by remember { mutableStateOf(settings.model) }
    var confirmActions by remember { mutableStateOf(settings.confirmActions) }
    var speak by remember { mutableStateOf(settings.speakNarration) }
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Agent settings", style = MaterialTheme.typography.titleMedium)
                Text(if (settings.hasApiKey()) "API key: configured (enter a new one to replace)" else "Anthropic API key", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    placeholder = { Text("sk-ant-…") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Model", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) { Text(model) }
                androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    models.forEach { id ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(id) }, onClick = { model = id; menuOpen = false })
                    }
                }
                ToggleRow("Ask before each action batch", confirmActions) { confirmActions = it }
                ToggleRow("Speak narration aloud", speak) { speak = it }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = {
                        val key = apiKey.trim()
                        if (key.isNotEmpty()) {
                            settings.storeApiKey(key)
                            Thread({ ModelCatalog.refresh(settings) }, "oau-model-refresh").start()
                        }
                        settings.model = model
                        settings.confirmActions = confirmActions
                        settings.speakNarration = speak
                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
