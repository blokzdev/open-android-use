package dev.openandroiduse.companion.agent.llm

/**
 * Per-provider key validation + live model listing (Phase 5.3). Each provider's
 * implementation lives in this package alongside its [AgentBackend], so the
 * provider SDKs (`com.anthropic`, `com.google.genai`) stay confined to
 * `agent/llm`. `ModelCatalog` (provider-agnostic, in `agent/`) drives these via
 * [LlmProvider.models].
 *
 * Network calls — invoke from a background thread. [baseUrl] honors the debug
 * loopback override when set (null = the provider SDK's default endpoint).
 */
interface ProviderModels {

    /** Make a minimal authenticated call; throws if the key is rejected. */
    fun validateKey(apiKey: String, baseUrl: String?)

    /** List the account's selectable text-generation models (bare ids). */
    fun listModels(apiKey: String, baseUrl: String?): List<String>
}
