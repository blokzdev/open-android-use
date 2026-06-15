package dev.openandroiduse.companion.agent

import android.util.Log
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import dev.openandroiduse.companion.CompanionService

/**
 * Refreshes the selectable model list from the live Models API (the 3.1a
 * hardcoded list was a stopgap — see the Phase 3 plan backlog). Best-effort
 * and silent: offline or unauthorized just keeps the cached/default list.
 */
object ModelCatalog {

    private const val MAX_MODELS = 12

    /** Result of a "Test key" check (Phase 4.7e). */
    sealed interface KeyTest {
        data object Valid : KeyTest
        data class Invalid(val message: String) : KeyTest
    }

    /**
     * Validates an API key by making a minimal authenticated call (the Models API). Network —
     * call from a background thread. [baseUrl] honors the debug loopback override when set.
     */
    fun validateKey(apiKey: String, baseUrl: String?): KeyTest {
        return try {
            val builder = AnthropicOkHttpClient.builder().apiKey(apiKey)
            if (baseUrl != null) builder.baseUrl(baseUrl)
            val client = builder.build()
            try {
                client.models().list()
                KeyTest.Valid
            } finally {
                client.close()
            }
        } catch (error: Exception) {
            KeyTest.Invalid(error.message ?: error.javaClass.simpleName)
        }
    }

    @Volatile
    private var refreshing = false

    /** Call from a background thread. */
    fun refresh(settings: AgentSettings) {
        if (refreshing) return
        // Never aim discovery at a test stub or run without a key.
        if (settings.baseUrlOverride != null) return
        val apiKey = settings.loadApiKey() ?: return
        refreshing = true
        try {
            val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
            try {
                val ids = client.models().list().autoPager()
                    .asSequence()
                    .map { it.id() }
                    .filter { it.startsWith("claude") }
                    .take(MAX_MODELS)
                    .toList()
                if (ids.isNotEmpty()) {
                    settings.cacheAvailableModels(ids)
                }
            } finally {
                client.close()
            }
        } catch (error: Exception) {
            Log.i(CompanionService.TAG, "model list refresh skipped: ${error.message}")
        } finally {
            refreshing = false
        }
    }
}
