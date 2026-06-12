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

        /** Hardcoded for 3.1a; a models-API fetch is planned for a later slice. */
        val AVAILABLE_MODELS = listOf("claude-opus-4-8", "claude-sonnet-4-6", "claude-haiku-4-5")

        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "oau-agent-api-key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREF_MODEL = "model"
        private const val PREF_CONFIRM_ACTIONS = "confirm_actions"
        private const val PREF_SPEAK_NARRATION = "speak_narration"
        private const val PREF_KEY_CIPHERTEXT = "api_key_ciphertext"
        private const val PREF_KEY_IV = "api_key_iv"
    }
}
