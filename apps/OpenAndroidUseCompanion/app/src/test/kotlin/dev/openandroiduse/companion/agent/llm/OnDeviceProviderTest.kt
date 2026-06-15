package dev.openandroiduse.companion.agent.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The on-device provider arm (Phase 5.5): it needs no API key, dispatches to the
 * on-device backend, and its "models" are the single local model. Cloud providers
 * keep requiring a key.
 */
class OnDeviceProviderTest {

    @Test
    fun onlyTheOnDeviceProviderSkipsTheApiKey() {
        assertTrue(LlmProvider.ANTHROPIC.requiresApiKey)
        assertTrue(LlmProvider.GEMINI.requiresApiKey)
        assertFalse(LlmProvider.ON_DEVICE.requiresApiKey)
    }

    @Test
    fun fromIdResolvesOnDevice() {
        assertEquals(LlmProvider.ON_DEVICE, LlmProvider.fromId("ondevice"))
    }

    @Test
    fun onDeviceCreatesTheOnDeviceBackend() {
        val backend = LlmProvider.ON_DEVICE.createBackend("/data/models/gemma-4-E2B-it.litertlm", null)
        assertTrue(backend is OnDeviceBackend)
    }

    @Test
    fun onDeviceModelsListsTheSingleLocalModelAndValidatesWithoutThrowing() {
        assertEquals(listOf("gemma-4-E2B"), OnDeviceModels.listModels("", null))
        OnDeviceModels.validateKey("", null) // no-op: must not throw
    }

    @Test
    fun onDeviceBackendPlaceholderEndsTheTurnWithANote() {
        val sink = object : BackendSink {
            val text = StringBuilder()
            override fun onTextDelta(text: String) { this.text.append(text) }
            override fun onThinkingDelta(text: String) {}
        }
        val turn = LlmProvider.ON_DEVICE.createBackend("/tmp/model.litertlm", null)
            .streamTurn(BackendRequest("gemma-4-E2B", "sys", emptyList(), emptyList()), sink)
        assertEquals(AgentStopReason.END_TURN, turn.stopReason)
        assertTrue(sink.text.isNotEmpty())
        assertTrue(turn.assistant.content.filterIsInstance<AgentContent.Text>().isNotEmpty())
    }
}
