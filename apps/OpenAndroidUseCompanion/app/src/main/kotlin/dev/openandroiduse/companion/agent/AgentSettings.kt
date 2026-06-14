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

/**
 * Agent configuration: the Anthropic API key (AES/GCM-encrypted with a
 * non-exportable Android Keystore key; the plaintext never touches disk and
 * leaves the device only toward api.anthropic.com) and the model selection.
 */
class AgentSettings(context: Context) {

    private val prefs = context.getSharedPreferences("agent_settings", Context.MODE_PRIVATE)

    var model: String
        get() = prefs.getString(PREF_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) {
            prefs.edit().putString(PREF_MODEL, value).apply()
        }

    /** Phase 3.1b consent ladder: show a confirmation sheet before action batches. */
    var confirmActions: Boolean
        get() = prefs.getBoolean(PREF_CONFIRM_ACTIONS, false)
        set(value) {
            prefs.edit().putBoolean(PREF_CONFIRM_ACTIONS, value).apply()
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

    /**
     * Test/diagnostic hook with no UI: overrides the API base URL so the
     * emulator smoke can run the real agent loop against a loopback stub
     * model server. Null means api.anthropic.com (the SDK default).
     */
    var baseUrlOverride: String?
        get() = prefs.getString(PREF_BASE_URL, null)?.ifBlank { null }
        set(value) {
            prefs.edit().putString(PREF_BASE_URL, value).apply()
        }

    /** Models offered in settings: the last Models-API fetch, else the built-in list. */
    fun availableModels(): List<String> {
        val cached = prefs.getString(PREF_AVAILABLE_MODELS, null)
            ?.split('\n')?.filter { it.isNotBlank() }
        return if (cached.isNullOrEmpty()) AVAILABLE_MODELS else cached
    }

    fun cacheAvailableModels(ids: List<String>) {
        prefs.edit().putString(PREF_AVAILABLE_MODELS, ids.joinToString("\n")).apply()
    }

    fun hasApiKey(): Boolean = prefs.contains(PREF_KEY_CIPHERTEXT)

    fun storeApiKey(apiKey: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, obtainSecretKey())
        val ciphertext = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(PREF_KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(PREF_KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun loadApiKey(): String? {
        val ciphertext = prefs.getString(PREF_KEY_CIPHERTEXT, null) ?: return null
        val iv = prefs.getString(PREF_KEY_IV, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                obtainSecretKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
            )
            String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            // The stored key can no longer be decrypted (e.g. the Keystore key
            // was invalidated by a device credential change or restore). Clear
            // it so hasApiKey() and the UI stop claiming it is configured and
            // the user is prompted to re-enter, instead of looping on
            // "no API key" while settings say otherwise.
            clearApiKey()
            null
        }
    }

    fun clearApiKey() {
        prefs.edit().remove(PREF_KEY_CIPHERTEXT).remove(PREF_KEY_IV).apply()
    }

    private fun obtainSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val DEFAULT_MODEL = "claude-opus-4-8"

        /** Fallback list until the first Models-API fetch succeeds (ModelCatalog). */
        val AVAILABLE_MODELS = listOf("claude-opus-4-8", "claude-sonnet-4-6", "claude-haiku-4-5")

        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "oau-agent-api-key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREF_MODEL = "model"
        private const val PREF_CONFIRM_ACTIONS = "confirm_actions"
        private const val PREF_SPEAK_NARRATION = "speak_narration"
        private const val PREF_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val PREF_DYNAMIC_COLOR = "dynamic_color"
        private const val PREF_BASE_URL = "base_url_override"
        private const val PREF_AVAILABLE_MODELS = "available_models"
        private const val PREF_KEY_CIPHERTEXT = "api_key_ciphertext"
        private const val PREF_KEY_IV = "api_key_iv"
    }
}
