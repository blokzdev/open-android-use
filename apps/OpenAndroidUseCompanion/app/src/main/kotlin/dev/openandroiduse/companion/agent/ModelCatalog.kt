package dev.openandroiduse.companion.agent

import android.util.Log
import dev.openandroiduse.companion.CompanionService
import dev.openandroiduse.companion.agent.llm.LlmProvider

/**
 * Provider-agnostic coordinator for "Test key" and the live model-list refresh.
 * The per-provider network calls live behind [LlmProvider.models] (in
 * `agent/llm`), so this file imports no provider SDK. Best-effort and silent:
 * offline or unauthorized just keeps the cached/default list.
 */
object ModelCatalog {

    /** Result of a "Test key" check (Phase 4.7e). */
    sealed interface KeyTest {
        data object Valid : KeyTest
        data class Invalid(val message: String) : KeyTest
    }

    /**
     * Validates an API key for [provider] by making a minimal authenticated call. Network —
     * call from a background thread. [baseUrl] honors the debug loopback override when set.
     */
    fun validateKey(provider: LlmProvider, apiKey: String, baseUrl: String?): KeyTest {
        return try {
            provider.models.validateKey(apiKey, baseUrl)
            KeyTest.Valid
        } catch (error: Exception) {
            KeyTest.Invalid(error.message ?: error.javaClass.simpleName)
        }
    }

    @Volatile
    private var refreshing = false

    /** Refresh the cached model list for [provider]. Call from a background thread. */
    fun refresh(provider: LlmProvider, settings: AgentSettings) {
        if (refreshing) return
        // Never aim discovery at a test stub or run without a key.
        if (settings.baseUrlOverride != null) return
        val apiKey = settings.loadApiKey(provider) ?: return
        refreshing = true
        try {
            val ids = provider.models.listModels(apiKey, null)
            if (ids.isNotEmpty()) {
                settings.cacheAvailableModels(ids, provider)
            }
        } catch (error: Exception) {
            Log.i(CompanionService.TAG, "model list refresh skipped: ${error.message}")
        } finally {
            refreshing = false
        }
    }
}
