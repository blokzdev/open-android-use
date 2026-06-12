package dev.openandroiduse.companion.agent

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.concurrent.atomic.AtomicInteger

/**
 * Speaks the agent's narration (Phase 3.1c) so the user can follow the task
 * without watching the chat — the Activity is backgrounded while the agent
 * works. Owned by the controller side, not the Activity, for exactly that
 * reason. Sentences are queued as they complete; stopping a task silences it.
 */
object VoiceNarrator {

    private var tts: TextToSpeech? = null

    @Volatile
    private var ready = false
    private val pending = mutableListOf<String>()
    private val utteranceId = AtomicInteger()
    private val sentences = SentenceBuffer()

    @Synchronized
    fun ensureInitialized(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            synchronized(this) {
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    for (text in pending) {
                        enqueue(text)
                    }
                }
                pending.clear()
            }
        }
    }

    fun onAssistantDelta(delta: String) {
        for (sentence in sentences.add(delta)) {
            speak(sentence)
        }
    }

    fun onMessageEnd() {
        sentences.flush()?.let { speak(it) }
    }

    @Synchronized
    fun stop() {
        sentences.flush()
        pending.clear()
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    @Synchronized
    private fun speak(text: String) {
        if (ready) {
            enqueue(text)
        } else if (tts != null) {
            pending.add(text)
        }
    }

    private fun enqueue(text: String) {
        try {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "oau-${utteranceId.incrementAndGet()}")
        } catch (_: Exception) {
        }
    }
}
