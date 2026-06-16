package dev.openandroiduse.companion.agent

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import dev.openandroiduse.companion.agent.llm.LlmProvider
import dev.openandroiduse.companion.ui.theme.ThemeMode

/**
 * Agent configuration: the selected model provider, and **per-provider** API key
 * (AES/GCM-encrypted with a non-exportable Android Keystore key; the plaintext
 * never touches disk and leaves the device only toward that provider) + model.
 *
 * Storage is per-provider but **zero-migration**: the original Anthropic slots
 * keep their legacy pref/alias names, and additional providers (Gemini) use an
 * `_<id>`-suffixed slot — so an existing install keeps its Claude key untouched.
 * The no-argument accessors operate on [selectedProvider], so existing call
 * sites (which always mean "the current provider") need no changes.
 */
class AgentSettings(context: Context) {

    /** Application context for resolving string resources off the UI (e.g. agent notes). */
    val appContext: Context = context.applicationContext

    private val prefs = context.getSharedPreferences("agent_settings", Context.MODE_PRIVATE)

    /** The provider the agent runs on, and that the key/model accessors target by default. */
    var selectedProvider: LlmProvider
        get() = LlmProvider.fromId(prefs.getString(PREF_SELECTED_PROVIDER, null))
        set(value) {
            prefs.edit().putString(PREF_SELECTED_PROVIDER, value.id).apply()
        }

    /** Selected model for the current provider. */
    var model: String
        get() = modelFor(selectedProvider)
        set(value) {
            setModel(value, selectedProvider)
        }

    fun modelFor(provider: LlmProvider = selectedProvider): String =
        prefs.getString(modelPref(provider), provider.defaultModel) ?: provider.defaultModel

    fun setModel(value: String, provider: LlmProvider = selectedProvider) {
        prefs.edit().putString(modelPref(provider), value).apply()
    }

    /**
     * Phase 5.6 adaptive perception: whether to send the screenshot (vision) to
     * the model for [provider], vs. acting from the accessibility-tree text only.
     * Per-provider; defaults to **on** (all providers are vision-capable, incl.
     * on-device Gemma). Off = text-only — faster/cheaper/more private.
     */
    fun sendScreenshots(provider: LlmProvider = selectedProvider): Boolean =
        prefs.getBoolean(sendScreenshotsPref(provider), true)

    fun setSendScreenshots(value: Boolean, provider: LlmProvider = selectedProvider) {
        prefs.edit().putBoolean(sendScreenshotsPref(provider), value).apply()
    }

    /**
     * Phase 5.7 Privacy / Local-Only Mode: a global umbrella that forces the
     * on-device provider so nothing leaves the device. Default off. Enforced at a
     * single chokepoint in [AgentController] via [effectiveProvider].
     */
    var localOnlyMode: Boolean
        get() = prefs.getBoolean(PREF_LOCAL_ONLY_MODE, false)
        set(value) {
            prefs.edit().putBoolean(PREF_LOCAL_ONLY_MODE, value).apply()
        }

    /** Phase 3.1b consent ladder: show a confirmation sheet before action batches. */
    var confirmActions: Boolean
        get() = prefs.getBoolean(PREF_CONFIRM_ACTIONS, false)
        set(value) {
            prefs.edit().putBoolean(PREF_CONFIRM_ACTIONS, value).apply()
        }

    /**
     * Phase 6.5: auto-decline action tools on a password/payment screen so secrets
     * are never typed or tapped. Default on — disabling is a conscious opt-out.
     */
    var sensitiveScreenGuard: Boolean
        get() = prefs.getBoolean(PREF_SENSITIVE_SCREEN_GUARD, true)
        set(value) {
            prefs.edit().putBoolean(PREF_SENSITIVE_SCREEN_GUARD, value).apply()
        }

    /** Phase 3.1c: speak the agent's narration via TTS while it works. */
    var speakNarration: Boolean
        get() = prefs.getBoolean(PREF_SPEAK_NARRATION, false)
        set(value) {
            prefs.edit().putBoolean(PREF_SPEAK_NARRATION, value).apply()
        }

    /** Phase 4.1: whether the first-run onboarding wizard has been completed. */
    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(PREF_ONBOARDING_COMPLETED, false)
        set(value) {
            prefs.edit().putBoolean(PREF_ONBOARDING_COMPLETED, value).apply()
        }

    /**
     * Phase 4.5: opt into Material You dynamic color (Android 12+). Off by
     * default so the app leads with the brand "Aurora" palette that matches the
     * icon; honored by OpenAndroidUseTheme at every Compose surface.
     */
    var dynamicColor: Boolean
        get() = prefs.getBoolean(PREF_DYNAMIC_COLOR, false)
        set(value) {
            prefs.edit().putBoolean(PREF_DYNAMIC_COLOR, value).apply()
        }

    /** Phase 4.7e: light/dark preference; defaults to following the system. */
    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString(PREF_THEME_MODE, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
        set(value) {
            prefs.edit().putString(PREF_THEME_MODE, value.name).apply()
        }

    /**
     * Test/diagnostic hook with no UI: overrides the API base URL so the
     * emulator/JVM smoke can run the real agent loop against a loopback stub
     * model server. Null means the provider SDK's default endpoint. A single
     * hook is enough: only one provider is exercised per test run.
     */
    var baseUrlOverride: String?
        get() = prefs.getString(PREF_BASE_URL, null)?.ifBlank { null }
        set(value) {
            prefs.edit().putString(PREF_BASE_URL, value).apply()
        }

    /** Models offered in settings for [provider]: the last `models` fetch, else the built-in list. */
    fun availableModels(provider: LlmProvider = selectedProvider): List<String> {
        val cached = prefs.getString(modelsPref(provider), null)
            ?.split('\n')?.filter { it.isNotBlank() }
        return if (cached.isNullOrEmpty()) provider.fallbackModels else cached
    }

    fun cacheAvailableModels(ids: List<String>, provider: LlmProvider = selectedProvider) {
        prefs.edit().putString(modelsPref(provider), ids.joinToString("\n")).apply()
    }

    fun hasApiKey(provider: LlmProvider = selectedProvider): Boolean = prefs.contains(ciphertextPref(provider))

    fun storeApiKey(apiKey: String, provider: LlmProvider = selectedProvider) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, obtainSecretKey(aliasFor(provider)))
        val ciphertext = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(ciphertextPref(provider), Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(ivPref(provider), Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun loadApiKey(provider: LlmProvider = selectedProvider): String? {
        val ciphertext = prefs.getString(ciphertextPref(provider), null) ?: return null
        val iv = prefs.getString(ivPref(provider), null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                obtainSecretKey(aliasFor(provider)),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
            )
            String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            // The stored key can no longer be decrypted (e.g. the Keystore key
            // was invalidated by a device credential change or restore). Clear
            // it so hasApiKey() and the UI stop claiming it is configured and
            // the user is prompted to re-enter, instead of looping on
            // "no API key" while settings say otherwise.
            clearApiKey(provider)
            null
        }
    }

    fun clearApiKey(provider: LlmProvider = selectedProvider) {
        prefs.edit().remove(ciphertextPref(provider)).remove(ivPref(provider)).apply()
    }

    private fun obtainSecretKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    // Per-provider storage slots. Anthropic keeps the original (legacy) names so
    // an existing install needs no migration; other providers get an `_<id>` slot.
    private fun slotSuffix(provider: LlmProvider): String =
        if (provider == LlmProvider.ANTHROPIC) "" else "_${provider.id}"

    private fun aliasFor(provider: LlmProvider): String = KEY_ALIAS + slotSuffix(provider)
    private fun ciphertextPref(provider: LlmProvider): String = PREF_KEY_CIPHERTEXT + slotSuffix(provider)
    private fun ivPref(provider: LlmProvider): String = PREF_KEY_IV + slotSuffix(provider)
    private fun modelPref(provider: LlmProvider): String = PREF_MODEL + slotSuffix(provider)
    private fun modelsPref(provider: LlmProvider): String = PREF_AVAILABLE_MODELS + slotSuffix(provider)
    private fun sendScreenshotsPref(provider: LlmProvider): String = PREF_SEND_SCREENSHOTS + slotSuffix(provider)

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "oau-agent-api-key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREF_SELECTED_PROVIDER = "selected_provider"
        private const val PREF_MODEL = "model"
        private const val PREF_SEND_SCREENSHOTS = "send_screenshots"
        private const val PREF_LOCAL_ONLY_MODE = "local_only_mode"
        private const val PREF_CONFIRM_ACTIONS = "confirm_actions"
        private const val PREF_SENSITIVE_SCREEN_GUARD = "sensitive_screen_guard"
        private const val PREF_SPEAK_NARRATION = "speak_narration"
        private const val PREF_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val PREF_DYNAMIC_COLOR = "dynamic_color"
        private const val PREF_THEME_MODE = "theme_mode"
        private const val PREF_BASE_URL = "base_url_override"
        private const val PREF_AVAILABLE_MODELS = "available_models"
        private const val PREF_KEY_CIPHERTEXT = "api_key_ciphertext"
        private const val PREF_KEY_IV = "api_key_iv"
    }
}
