package dev.openandroiduse.companion.agent

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import dev.openandroiduse.companion.CompanionService

/**
 * The conversation surface for the on-device agent (Phase 3.1a): type a task,
 * watch the agent narrate and act, stop it at any time. Programmatic UI in
 * keeping with the companion's zero-resource style.
 */
class ChatActivity : Activity(), AgentController.Listener {

    private lateinit var settings: AgentSettings
    private lateinit var transcript: LinearLayout
    private lateinit var scroller: ScrollView
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private lateinit var stopButton: Button
    private lateinit var micButton: Button
    private var speechRecognizer: android.speech.SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** The bubble currently receiving streamed text; null starts a new one. */
    private var activeBubble: TextView? = null
    private var activeThinking: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AgentSettings(this)

        val padding = dp(12)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        header.addView(TextView(this).apply {
            text = "Agent"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(Button(this).apply {
            text = "Settings"
            setOnClickListener { showSettingsDialog() }
        })
        root.addView(header)

        scroller = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            isFillViewport = true
        }
        transcript = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroller.addView(transcript)
        root.addView(scroller)

        val composer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            hint = "What should I do on this phone?"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        composer.addView(input)
        micButton = Button(this).apply {
            text = "🎤"
            setOnClickListener { startListening() }
        }
        composer.addView(micButton)
        sendButton = Button(this).apply {
            text = "Send"
            setOnClickListener { sendTask() }
        }
        composer.addView(sendButton)
        stopButton = Button(this).apply {
            text = "Stop"
            isEnabled = false
            setOnClickListener { AgentController.requestStop() }
        }
        composer.addView(stopButton)
        root.addView(composer)

        setContentView(root)
        addSystemNote(
            "The agent sees the screen and acts through the accessibility service. " +
                "Press Stop at any time; disabling the service in system settings is the hard kill switch.",
        )
    }

    override fun onResume() {
        super.onResume()
        AgentController.listener = this
        refreshControls(AgentController.isRunning)
        renderTranscript()
        if (!CompanionService.isRunning) {
            addSystemNote("The companion accessibility service is OFF — enable it before starting a task.")
        }
        if (!settings.hasApiKey()) {
            showSettingsDialog()
        }
    }

    /**
     * Rebuilds the transcript from the controller's log. The Activity is
     * backgrounded while the agent drives other apps, so resume re-renders
     * everything it missed.
     */
    private fun renderTranscript() {
        transcript.removeAllViews()
        activeBubble = null
        activeThinking = null
        for ((kind, text) in AgentController.transcriptSnapshot()) {
            when (kind) {
                AgentController.KIND_USER -> addBubble(text, user = true)
                AgentController.KIND_ASSISTANT -> activeBubble = addBubble(text, user = false)
                AgentController.KIND_THINKING -> activeThinking = addThinkingView().also { it.text = text }
                AgentController.KIND_TOOL -> addToolChip(text)
                AgentController.KIND_NOTE -> addSystemNote(text)
            }
        }
        // Only the last entry may still be streaming; older views are final.
        val last = AgentController.transcriptSnapshot().lastOrNull()?.first
        if (last != AgentController.KIND_ASSISTANT) activeBubble = null
        if (last != AgentController.KIND_THINKING) activeThinking = null
        scrollToBottom()
    }

    override fun onPause() {
        super.onPause()
        if (AgentController.listener === this) {
            AgentController.listener = null
        }
    }

    private fun sendTask() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.setText("")
        activeBubble = null
        activeThinking = null
        if (AgentController.startTask(text, settings)) {
            addBubble(text, user = true)
            scrollToBottom()
        }
    }

    // --- AgentController.Listener (loop thread → main) ---

    override fun onTaskStateChanged(running: Boolean) {
        mainHandler.post { refreshControls(running) }
    }

    override fun onAssistantDelta(text: String) {
        mainHandler.post {
            val bubble = activeBubble ?: addBubble("", user = false).also { activeBubble = it }
            bubble.append(text)
            activeThinking = null
            scrollToBottom()
        }
    }

    override fun onThinkingDelta(text: String) {
        mainHandler.post {
            val view = activeThinking ?: addThinkingView().also { activeThinking = it }
            view.append(text)
            scrollToBottom()
        }
    }

    override fun onToolCall(name: String, summary: String) {
        mainHandler.post {
            activeBubble = null
            activeThinking = null
            addToolChip("▸ $name ${summary.ifEmpty { "" }}".trimEnd())
            scrollToBottom()
        }
    }

    override fun onToolResult(name: String, isError: Boolean) {
        if (!isError) return
        mainHandler.post {
            addToolChip("✗ $name failed — the agent will see the error and adapt")
            scrollToBottom()
        }
    }

    override fun onTaskFinished(reason: String) {
        // The controller logs the user-facing note; re-render to pick it up
        // along with anything missed while backgrounded.
        mainHandler.post { renderTranscript() }
    }

    override fun onError(message: String) {
        mainHandler.post {
            addSystemNote("⚠ $message")
            scrollToBottom()
        }
    }

    // --- UI helpers ---

    private fun refreshControls(running: Boolean) {
        sendButton.isEnabled = !running
        input.isEnabled = !running
        stopButton.isEnabled = running
    }

    private fun addBubble(text: String, user: Boolean): TextView {
        val bubble = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.BLACK)
            setBackgroundColor(if (user) 0xFFD7E8FF.toInt() else 0xFFF1F1F1.toInt())
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(6)
            gravity = if (user) Gravity.END else Gravity.START
        }
        transcript.addView(bubble, params)
        return bubble
    }

    private fun addThinkingView(): TextView {
        val view = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF888888.toInt())
            setTypeface(typeface, Typeface.ITALIC)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        transcript.addView(view, marginParams())
        return view
    }

    private fun addToolChip(text: String) {
        transcript.addView(
            TextView(this).apply {
                this.text = text
                textSize = 13f
                setTextColor(0xFF555555.toInt())
                setTypeface(Typeface.MONOSPACE)
                setPadding(dp(10), dp(2), dp(10), dp(2))
            },
            marginParams(),
        )
    }

    private fun addSystemNote(text: String) {
        transcript.addView(
            TextView(this).apply {
                this.text = text
                textSize = 13f
                setTextColor(0xFF777777.toInt())
                setPadding(dp(4), dp(6), dp(4), dp(6))
            },
            marginParams(),
        )
    }

    private fun marginParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(2) }

    private fun scrollToBottom() {
        scroller.post { scroller.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun showSettingsDialog() {
        val padding = dp(16)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        layout.addView(TextView(this).apply {
            text = if (settings.hasApiKey()) "API key: configured (enter a new one to replace)" else "Anthropic API key"
            textSize = 14f
        })
        val keyInput = EditText(this).apply {
            hint = "sk-ant-..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(keyInput)
        layout.addView(TextView(this).apply {
            text = "Model"
            textSize = 14f
            setPadding(0, dp(8), 0, 0)
        })
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@ChatActivity,
                android.R.layout.simple_spinner_dropdown_item,
                AgentSettings.AVAILABLE_MODELS,
            )
            setSelection(AgentSettings.AVAILABLE_MODELS.indexOf(settings.model).coerceAtLeast(0))
        }
        layout.addView(spinner)
        val confirmBox = android.widget.CheckBox(this).apply {
            text = "Ask before each action batch (confirmation sheet)"
            isChecked = settings.confirmActions
        }
        layout.addView(confirmBox)
        val speakBox = android.widget.CheckBox(this).apply {
            text = "Speak narration aloud while working"
            isChecked = settings.speakNarration
        }
        layout.addView(speakBox)

        AlertDialog.Builder(this)
            .setTitle("Agent settings")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val key = keyInput.text.toString().trim()
                if (key.isNotEmpty()) {
                    settings.storeApiKey(key)
                }
                settings.model = spinner.selectedItem as String
                settings.confirmActions = confirmBox.isChecked
                settings.speakNarration = speakBox.isChecked
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- push-to-talk (Phase 3.1c) ---

    private fun startListening() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            return
        }
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            addSystemNote("Speech recognition is not available on this device.")
            return
        }
        val recognizer = speechRecognizer ?: android.speech.SpeechRecognizer.createSpeechRecognizer(this).also {
            speechRecognizer = it
            it.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onResults(results: Bundle?) {
                    micButton.text = "🎤"
                    val text = results
                        ?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        input.setText(text)
                        input.setSelection(text.length)
                    }
                }

                override fun onError(error: Int) {
                    micButton.text = "🎤"
                    if (error != android.speech.SpeechRecognizer.ERROR_NO_MATCH &&
                        error != android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ) {
                        addSystemNote("Speech recognition failed (code $error).")
                    }
                }

                override fun onReadyForSpeech(params: Bundle?) {
                    micButton.text = "…"
                }

                override fun onEndOfSpeech() {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        recognizer.startListening(
            android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(
                android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            ),
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO &&
            grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQUEST_RECORD_AUDIO = 41
    }
}
