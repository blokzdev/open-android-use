package dev.openandroiduse.companion.agent.llm

import com.google.genai.Client
import com.google.genai.types.HttpOptions
import com.google.genai.types.ListModelsConfig

/**
 * Gemini key validation + live model listing (Phase 5.2), kept in the `agent/llm`
 * package so `com.google.genai` stays out of the rest of the `agent` package.
 * `ModelCatalog` dispatches here for the Gemini provider. Network calls — invoke
 * from a background thread.
 *
 * (Phase 5.3 will fold both providers' validate/list behind the backend; for now
 * this mirrors the inline Anthropic path in `ModelCatalog`.)
 */
object GeminiModels {

    private const val MAX_MODELS = 12

    /** Make a minimal authenticated call; throws if the key is rejected. */
    fun validateKey(apiKey: String, baseUrl: String?) {
        clientFor(apiKey, baseUrl).use { client ->
            // Force the first page to fetch so an invalid key actually surfaces.
            client.models.list(ListModelsConfig.builder().build()).iterator().hasNext()
        }
    }

    /** List the account's text-generation Gemini models (bare ids, e.g. "gemini-2.5-pro"). */
    fun listModels(apiKey: String, baseUrl: String?): List<String> {
        clientFor(apiKey, baseUrl).use { client ->
            val ids = mutableListOf<String>()
            for (model in client.models.list(ListModelsConfig.builder().build())) {
                val id = model.name().orElse(null)?.removePrefix("models/") ?: continue
                if (!id.startsWith("gemini-")) continue
                // When the API reports actions, require text generation; otherwise keep it.
                val actions = model.supportedActions().orElse(emptyList())
                if (actions.isNotEmpty() && "generateContent" !in actions) continue
                if (id !in ids) ids.add(id)
                if (ids.size >= MAX_MODELS) break
            }
            return ids
        }
    }

    private fun clientFor(apiKey: String, baseUrl: String?): Client =
        Client.builder()
            .apiKey(apiKey)
            .apply { baseUrl?.let { httpOptions(HttpOptions.builder().baseUrl(it).build()) } }
            .build()
}
