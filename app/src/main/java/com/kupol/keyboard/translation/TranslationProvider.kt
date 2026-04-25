package com.kupol.keyboard.translation

import com.kupol.keyboard.ImeSessionState

/**
 * Единый интерфейс для всех провайдеров перевода.
 * Добавление нового провайдера = новый класс, реализующий этот интерфейс.
 * Регистрация — в TranslationManager.
 */
interface TranslationProvider {

    /** Уникальный ID провайдера — для логов и сообщений об ошибках */
    val id: String

    /** Имя для отображения пользователю */
    val displayName: String

    /**
     * Перевести текст на целевой язык.
     * Должен быть suspend — вызывается из coroutine scope сервиса.
     * Бросает исключение при ошибке (сеть, API, парсинг) — обрабатывается в TranslationManager.
     */
    suspend fun translate(
        text: String,
        targetLanguage: ImeSessionState.TargetLanguage,
        apiKey: String,
    ): String
}

/**
 * Хелпер: получить название языка на английском для системного промпта.
 */
internal fun ImeSessionState.TargetLanguage.toEnglishName(): String {
    return when (this) {
        ImeSessionState.TargetLanguage.RU -> "Russian"
        ImeSessionState.TargetLanguage.EN -> "English"
        ImeSessionState.TargetLanguage.ZH -> "Chinese"
    }
}

/**
 * Хелпер: получить ISO-код языка (для Google Translate API).
 */
internal fun ImeSessionState.TargetLanguage.toIsoCode(): String {
    return when (this) {
        ImeSessionState.TargetLanguage.RU -> "ru"
        ImeSessionState.TargetLanguage.EN -> "en"
        ImeSessionState.TargetLanguage.ZH -> "zh"
    }
}
