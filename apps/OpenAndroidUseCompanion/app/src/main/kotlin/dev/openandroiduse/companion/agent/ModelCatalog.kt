package dev.openandroiduse.companion.agent

import android.util.Log
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import dev.openandroiduse.companion.CompanionService
import dev.openandroiduse.companion.agent.llm.GeminiModels
import dev.openandroiduse.companion.agent.llm.LlmProvider

/**
 * Refreshes the selectable model list from each provider's live models endpoint
 * (the 3.1a hardcoded list was a stopgap — see the Phase 3 plan backlog).
 * Best-effort and silent: offline or unauthorized just keeps the cached/default
 * list. Phase 5.2 makes it provider-aware; 5.3 will fold the per-provider calls
 * behind the backend.
 */
object ModelCatalog {

    private const val MAX_MODELS = 12

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
            when (provider) {
                LlmProvider.ANTHROPIC -> {
                    val builder = AnthropicOkHttpClient.builder().apiKey(apiKey)
                    if (baseUrl != null) builder.baseUrl(baseUrl)
                    val client = builder.build()
                    try {
                        client.models().list()
                    } finally {
                        client.close()
                    }
                }
                LlmProvider.GEMINI -> GeminiModels.validateKey(apiKey, baseUrl)
            }
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
            val ids = when (provider) {
                LlmProvider.ANTHROPIC -> anthropicModels(apiKey)
                LlmProvider.GEMINI -> GeminiModels.listModels(apiKey, null)
            }
            if (ids.isNotEmpty()) {
                settings.cacheAvailableModels(ids, provider)
            }
        } catch (error: Exception) {
            Log.i(CompanionService.TAG, "model list refresh skipped: ${error.message}")
        } finally {
            refreshing = false
        }
    }

    private fun anthropicModels(apiKey: String): List<String> {
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
        try {
            return client.models().list().autoPager()
                .asSequence()
                .map { it.id() }
                .filter { it.startsWith("claude") }
                .take(MAX_MODELS)
                .toList()
        } finally {
            client.close()
        }
    }
}
