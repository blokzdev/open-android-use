package dev.openandroiduse.companion.agent

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.openandroiduse.companion.agent.llm.LlmProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real Android Keystore AES/GCM path for the API key on a device:
 * store → load → survives a fresh AgentSettings instance → clear. This automates
 * the storage half of VERIFICATION V33 (the key is encrypted at rest and round
 * trips), leaving only the on-screen UI parts for manual hardware checks.
 */
@RunWith(AndroidJUnit4::class)
class AgentSettingsInstrumentedTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun apiKeyEncryptsRoundTripsAndClears() {
        val settings = AgentSettings(context)
        settings.clearApiKey()
        assertFalse(settings.hasApiKey())

        settings.storeApiKey("sk-ant-test-0123456789")
        assertTrue(settings.hasApiKey())
        assertEquals("sk-ant-test-0123456789", settings.loadApiKey())

        // A fresh instance decrypts the same value from prefs + Keystore.
        assertEquals("sk-ant-test-0123456789", AgentSettings(context).loadApiKey())

        settings.clearApiKey()
        assertFalse(settings.hasApiKey())
        assertNull(settings.loadApiKey())
    }

    @Test
    fun perProviderKeysAndModelsAreIndependent() {
        val settings = AgentSettings(context)
        settings.clearApiKey(LlmProvider.ANTHROPIC)
        settings.clearApiKey(LlmProvider.GEMINI)

        // Each provider's key lives in its own Keystore-encrypted slot.
        settings.storeApiKey("sk-ant-test-0123456789", LlmProvider.ANTHROPIC)
        settings.storeApiKey("AIza-gemini-test-0123456789", LlmProvider.GEMINI)
        assertTrue(settings.hasApiKey(LlmProvider.ANTHROPIC))
        assertTrue(settings.hasApiKey(LlmProvider.GEMINI))
        assertEquals("sk-ant-test-0123456789", settings.loadApiKey(LlmProvider.ANTHROPIC))
        assertEquals("AIza-gemini-test-0123456789", settings.loadApiKey(LlmProvider.GEMINI))

        // selectedProvider drives the no-arg accessors and the model default.
        settings.selectedProvider = LlmProvider.GEMINI
        assertEquals("AIza-gemini-test-0123456789", settings.loadApiKey())
        assertEquals(LlmProvider.GEMINI.defaultModel, settings.model)
        settings.selectedProvider = LlmProvider.ANTHROPIC
        assertEquals("sk-ant-test-0123456789", settings.loadApiKey())
        assertEquals(LlmProvider.ANTHROPIC.defaultModel, settings.model)

        // Clearing one provider leaves the other intact.
        settings.clearApiKey(LlmProvider.GEMINI)
        assertFalse(settings.hasApiKey(LlmProvider.GEMINI))
        assertTrue(settings.hasApiKey(LlmProvider.ANTHROPIC))

        settings.clearApiKey(LlmProvider.ANTHROPIC)
    }
}
