package dev.openandroiduse.companion.agent.llm

/**
 * The model providers the on-device agent can run on (Phase 5.2). Each provider
 * is BYOK: the user supplies their own API key, stored per-provider. The
 * [AgentBackend] implementation for each lives in this package.
 *
 * [fallbackModels] seeds the model picker offline / before the first live
 * `models` fetch; [defaultModel] is the selection a provider starts on. We
 * default Gemini to a **stable** id (not a `-preview` flagship) because Google
 * retires preview ids; the live picker still surfaces the full current lineup.
 */
enum class LlmProvider(
    val id: String,
    val displayName: String,
    val defaultModel: String,
    val keyHelpUrl: String,
    val fallbackModels: List<String>,
) {
    ANTHROPIC(
        id = "anthropic",
        displayName = "Claude (Anthropic)",
        defaultModel = "claude-opus-4-8",
        keyHelpUrl = "https://console.anthropic.com/settings/keys",
        fallbackModels = listOf("claude-opus-4-8", "claude-sonnet-4-6", "claude-haiku-4-5"),
    ),
    GEMINI(
        id = "gemini",
        displayName = "Gemini (Google)",
        defaultModel = "gemini-2.5-pro",
        keyHelpUrl = "https://aistudio.google.com/apikey",
        fallbackModels = listOf(
            "gemini-2.5-pro",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-3.5-flash",
            "gemini-3.1-flash-lite",
            "gemini-3.1-pro-preview",
        ),
    ),
    ;

    /** This provider's key-validation + model-listing capability (Phase 5.3). */
    val models: ProviderModels
        get() = when (this) {
            ANTHROPIC -> AnthropicModels
            GEMINI -> GeminiModels
        }

    /** Construct the agent-loop backend for this provider (the loop's only provider-specific step). */
    fun createBackend(apiKey: String, baseUrl: String?): AgentBackend = when (this) {
        ANTHROPIC -> AnthropicBackend(apiKey, baseUrl)
        GEMINI -> GeminiBackend(apiKey, baseUrl)
    }

    companion object {
        /** Resolve a stored id back to a provider; unknown / null falls back to the original Claude path. */
        fun fromId(id: String?): LlmProvider = entries.firstOrNull { it.id == id } ?: ANTHROPIC
    }
}
