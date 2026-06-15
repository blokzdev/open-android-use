package dev.openandroiduse.companion.agent.llm

/**
 * The model providers the on-device agent can run on. Cloud providers (Phase 5.2)
 * are BYOK — the user supplies a per-provider API key; the on-device provider
 * (Phase 5.5) runs a locally-downloaded model with no key and no network. Each
 * [AgentBackend] implementation lives in this package.
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

    /**
     * Fully on-device (Phase 5.5): Gemma 4 E2B via LiteRT-LM, downloaded at
     * runtime. No API key, no network egress. [defaultModel] is the model id;
     * the actual `.litertlm` file is managed by `OnDeviceModelManager`.
     */
    ON_DEVICE(
        id = "ondevice",
        displayName = "Gemma (on-device)",
        defaultModel = "gemma-4-E2B",
        keyHelpUrl = "",
        fallbackModels = listOf("gemma-4-E2B"),
    ),
    ;

    /** Cloud providers need a BYOK key; the on-device provider needs a downloaded model instead. */
    val requiresApiKey: Boolean get() = this != ON_DEVICE

    /** This provider's key-validation + model-listing capability (Phase 5.3). */
    val models: ProviderModels
        get() = when (this) {
            ANTHROPIC -> AnthropicModels
            GEMINI -> GeminiModels
            ON_DEVICE -> OnDeviceModels
        }

    /**
     * Construct the agent-loop backend (the loop's only provider-specific step).
     * [credential] is the API key for cloud providers and the local model file
     * path for the on-device provider.
     */
    fun createBackend(credential: String, baseUrl: String?): AgentBackend = when (this) {
        ANTHROPIC -> AnthropicBackend(credential, baseUrl)
        GEMINI -> GeminiBackend(credential, baseUrl)
        ON_DEVICE -> OnDeviceBackend(credential)
    }

    companion object {
        /** Resolve a stored id back to a provider; unknown / null falls back to the original Claude path. */
        fun fromId(id: String?): LlmProvider = entries.firstOrNull { it.id == id } ?: ANTHROPIC
    }
}
