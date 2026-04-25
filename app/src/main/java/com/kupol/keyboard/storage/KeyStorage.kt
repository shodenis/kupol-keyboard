package com.kupol.keyboard.storage

import android.content.Context
import android.content.SharedPreferences

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

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveKey(context: Context, provider: String, key: String) {
        val prefs = getPrefs(context)
        prefs.edit().putString(provider, key).apply()
    }

    fun getKey(context: Context, provider: String): String? {
        val prefs = getPrefs(context)
        return prefs.getString(provider, null)
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
}
