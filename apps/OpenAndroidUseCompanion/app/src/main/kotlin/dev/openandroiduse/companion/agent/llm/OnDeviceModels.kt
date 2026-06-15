package dev.openandroiduse.companion.agent.llm

/**
 * The on-device provider's "models" capability (Phase 5.5). There is no network
 * and no API key: the single model is a local `.litertlm` file managed by
 * `OnDeviceModelManager`. Readiness/availability is decided by file presence at
 * the manager (the UI/controller consult it directly), so this capability is a
 * thin, offline stand-in that satisfies the [ProviderModels] seam.
 */
object OnDeviceModels : ProviderModels {

    /** No key to validate — the on-device tier gates on the downloaded model, not a key. */
    override fun validateKey(apiKey: String, baseUrl: String?) {
        // Intentionally a no-op: there is nothing to authenticate on-device.
    }

    /** The single bundled-by-download model id; the live list never changes. */
    override fun listModels(apiKey: String, baseUrl: String?): List<String> =
        LlmProvider.ON_DEVICE.fallbackModels
}
