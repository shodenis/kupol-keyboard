package com.kupol.keyboard.storage

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Простое хранение API-ключей и выбора провайдера в [SharedPreferences].
 * Имя файла: [PREFS_NAME] (то же, что ожидает цепочка перевода).
 */
object KeyStorage {
    private const val PREFS_NAME = "kupol_prefs"

    private const val KEY_DEEPSEEK = "key_deepseek"
    private const val KEY_CLAUDE = "key_claude"
    private const val KEY_OPENAI = "key_openai"
    private const val KEY_GOOGLE = "key_google"

    private const val PREF_PRIMARY = "primary_provider"
    private const val PREF_FALLBACK = "fallback_provider"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val MASTER_ALIAS = "kupol_keyboard_api_master"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveKey(context: Context, provider: String, key: String) {
        val prefs = getPrefs(context)
        if (key.isBlank()) {
            prefs.edit().remove(provider).remove(ivName(provider)).apply()
            return
        }
        val encrypted = encrypt(key)
        prefs.edit()
            .putString(provider, encrypted.ciphertextB64)
            .putString(ivName(provider), encrypted.ivB64)
            .apply()
    }

    fun getKey(context: Context, provider: String): String? {
        val prefs = getPrefs(context)
        val cipherB64 = prefs.getString(provider, null)
        if (cipherB64.isNullOrBlank()) return null
        val ivB64 = prefs.getString(ivName(provider), null)
        if (ivB64.isNullOrBlank()) {
            // Миграция со старой версии: plaintext в prefs -> шифруем при первом чтении.
            saveKey(context, provider, cipherB64)
            return cipherB64
        }
        return try {
            decrypt(cipherB64, ivB64)
        } catch (_: Exception) {
            null
        }
    }

    fun saveDeepSeekKey(context: Context, key: String) = saveKey(context, KEY_DEEPSEEK, key)
    fun getDeepSeekKey(context: Context): String? = getKey(context, KEY_DEEPSEEK)

    fun saveClaudeKey(context: Context, key: String) = saveKey(context, KEY_CLAUDE, key)
    fun getClaudeKey(context: Context): String? = getKey(context, KEY_CLAUDE)

    fun saveOpenAIKey(context: Context, key: String) = saveKey(context, KEY_OPENAI, key)
    fun getOpenAIKey(context: Context): String? = getKey(context, KEY_OPENAI)

    fun saveGoogleKey(context: Context, key: String) = saveKey(context, KEY_GOOGLE, key)
    fun getGoogleKey(context: Context): String? = getKey(context, KEY_GOOGLE)

    fun getPrimaryProvider(context: Context): String {
        return getPrefs(context).getString(PREF_PRIMARY, "deepseek") ?: "deepseek"
    }

    fun setPrimaryProvider(context: Context, providerId: String) {
        getPrefs(context).edit().putString(PREF_PRIMARY, providerId).apply()
    }

    fun getFallbackProvider(context: Context): String {
        return getPrefs(context).getString(PREF_FALLBACK, "") ?: ""
    }

    fun setFallbackProvider(context: Context, providerId: String) {
        getPrefs(context).edit().putString(PREF_FALLBACK, providerId).apply()
    }

    /** Ключ API для id провайдера (`deepseek`, `claude`, …), как в [TranslationManager]. */
    fun getApiKeyForProvider(context: Context, providerId: String): String {
        return when (providerId) {
            "deepseek" -> getDeepSeekKey(context).orEmpty()
            "claude" -> getClaudeKey(context).orEmpty()
            "openai" -> getOpenAIKey(context).orEmpty()
            "google" -> getGoogleKey(context).orEmpty()
            else -> ""
        }
    }

    private fun ivName(provider: String): String = "${provider}_iv"

    private data class Encrypted(val ciphertextB64: String, val ivB64: String)

    private fun encrypt(plainText: String): Encrypted {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return Encrypted(
            ciphertextB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP),
        )
    }

    private fun decrypt(ciphertextB64: String, ivB64: String): String {
        val cipherText = Base64.decode(ciphertextB64, Base64.NO_WRAP)
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
        val plain = cipher.doFinal(cipherText)
        return String(plain, StandardCharsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = ks.getKey(MASTER_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val specBuilder = KeyGenParameterSpec.Builder(
            MASTER_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            specBuilder.setUnlockedDeviceRequired(false)
        }

        keyGenerator.init(specBuilder.build())
        return keyGenerator.generateKey()
    }
}
