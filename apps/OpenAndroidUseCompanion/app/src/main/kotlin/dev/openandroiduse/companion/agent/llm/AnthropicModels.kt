package dev.openandroiduse.companion.agent.llm

import com.anthropic.client.okhttp.AnthropicOkHttpClient

/**
 * Anthropic key validation + live model listing (Phase 5.3), moved out of
 * `ModelCatalog` so `com.anthropic` stays confined to `agent/llm`. Mirrors
 * [GeminiModels]. Network calls — invoke from a background thread.
 */
object AnthropicModels : ProviderModels {

    private const val MAX_MODELS = 12

    override fun validateKey(apiKey: String, baseUrl: String?) {
        val builder = AnthropicOkHttpClient.builder().apiKey(apiKey)
        if (baseUrl != null) builder.baseUrl(baseUrl)
        val client = builder.build()
        try {
            client.models().list()
        } finally {
            client.close()
        }
    }

    override fun listModels(apiKey: String, baseUrl: String?): List<String> {
        val builder = AnthropicOkHttpClient.builder().apiKey(apiKey)
        if (baseUrl != null) builder.baseUrl(baseUrl)
        val client = builder.build()
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
