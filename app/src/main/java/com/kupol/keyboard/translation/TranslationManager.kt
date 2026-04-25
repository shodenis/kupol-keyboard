package com.kupol.keyboard.translation

import android.content.Context
import com.kupol.keyboard.ImeSessionState
import com.kupol.keyboard.storage.KeyStorage

/**
 * Управляет провайдерами перевода и логикой фолбэка.
 * Основной провайдер → ошибка → резервный → ошибка → Result.failure с описанием.
 */
class TranslationManager(private val context: Context) {

    private val providers: Map<String, TranslationProvider> = mapOf(
        "deepseek" to DeepSeekProvider(),
        "claude" to ClaudeProvider(),
        "openai" to OpenAIProvider(),
        "google" to GoogleProvider(),
    )

    /**
     * Перевести текст с фолбэком.
     * Настройки и ключи читаются из [KeyStorage] ([kupol_prefs]).
     */
    suspend fun translate(
        text: String,
        targetLanguage: ImeSessionState.TargetLanguage,
    ): Result<String> {

        val primaryId = KeyStorage.getPrimaryProvider(context)
        val fallbackId = KeyStorage.getFallbackProvider(context)

        val primaryProvider = providers[primaryId]
        val fallbackProvider = providers[fallbackId]

        if (primaryProvider != null) {
            val primaryKey = KeyStorage.getApiKeyForProvider(context, primaryId)
            if (primaryKey.isNotBlank()) {
                val primaryResult = runProviderTranslate(
                    primaryProvider,
                    text,
                    targetLanguage,
                    primaryKey,
                )
                if (primaryResult.isSuccess) return primaryResult

                if (fallbackProvider != null) {
                    val fallbackKey = KeyStorage.getApiKeyForProvider(context, fallbackId)
                    if (fallbackKey.isNotBlank()) {
                        val fallbackResult = runProviderTranslate(
                            fallbackProvider,
                            text,
                            targetLanguage,
                            fallbackKey,
                        )
                        if (fallbackResult.isSuccess) return fallbackResult

                        return Result.failure(
                            Exception(
                                "${primaryProvider.displayName} недоступен. " +
                                    "${fallbackProvider.displayName} тоже не ответил.",
                            ),
                        )
                    } else {
                        return Result.failure(
                            Exception(
                                "${primaryProvider.displayName} недоступен. " +
                                    "Добавьте резервный API-ключ в настройках.",
                            ),
                        )
                    }
                }

                return Result.failure(
                    primaryResult.exceptionOrNull()
                        ?: Exception("${primaryProvider.displayName}: неизвестная ошибка"),
                )
            } else {
                return Result.failure(
                    Exception("API-ключ для ${primaryProvider.displayName} не задан. Откройте настройки."),
                )
            }
        }

        return Result.failure(Exception("Провайдер перевода не выбран. Откройте настройки."))
    }

    fun getAvailableProviders(): List<Pair<String, String>> {
        return providers.map { (id, provider) -> id to provider.displayName }
    }

    /** `runCatching` не может вызывать suspend — оборачиваем явно. */
    private suspend fun runProviderTranslate(
        provider: TranslationProvider,
        text: String,
        targetLanguage: ImeSessionState.TargetLanguage,
        apiKey: String,
    ): Result<String> =
        try {
            Result.success(provider.translate(text, targetLanguage, apiKey))
        } catch (e: Throwable) {
            Result.failure(e)
        }
}
