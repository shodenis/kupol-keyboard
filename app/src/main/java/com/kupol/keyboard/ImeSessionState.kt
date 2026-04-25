package com.kupol.keyboard

import java.util.concurrent.atomic.AtomicLong

/**
 * Единый источник правды для состояния IME-сессии.
 * Живёт внутри сервиса, не во View — выдерживает пересоздание UI (поворот, split-screen).
 */
class ImeSessionState {

    /** Язык раскладки — какие буквы на кнопках. */
    enum class InputLanguage { EN, RU }

    /** Язык перевода — куда переводим при нажатии ⟳. */
    enum class TargetLanguage { RU, EN, ZH }

    /** Режим Shift для буквенных клавиш. */
    enum class ShiftMode { OFF, ON, CAPS_LOCK }

    // ─── Язык раскладки ─────────────────────────────────────────
    var inputLanguage: InputLanguage = InputLanguage.EN

    fun cycleInputLanguage(): InputLanguage {
        inputLanguage = when (inputLanguage) {
            InputLanguage.EN -> InputLanguage.RU
            InputLanguage.RU -> InputLanguage.EN
        }
        return inputLanguage
    }

    // ─── Язык перевода ──────────────────────────────────────────
    var targetLanguage: TargetLanguage = TargetLanguage.ZH

    fun cycleTargetLanguage(): TargetLanguage {
        targetLanguage = when (targetLanguage) {
            TargetLanguage.RU -> TargetLanguage.EN
            TargetLanguage.EN -> TargetLanguage.ZH
            TargetLanguage.ZH -> TargetLanguage.RU
        }
        return targetLanguage
    }

    // ─── Shift / Caps Lock ──────────────────────────────────────
    var shiftMode: ShiftMode = ShiftMode.OFF

    fun toggleShift() {
        shiftMode = when (shiftMode) {
            ShiftMode.OFF -> ShiftMode.ON
            ShiftMode.ON -> ShiftMode.CAPS_LOCK
            ShiftMode.CAPS_LOCK -> ShiftMode.OFF
        }
    }

    fun resetShiftAfterKey() {
        if (shiftMode == ShiftMode.ON) shiftMode = ShiftMode.OFF
    }

    val isUppercase: Boolean
        get() = shiftMode != ShiftMode.OFF

    // ─── Локальный composing-буфер ─────────────────────────────
    /**
     * Текст живёт ЗДЕСЬ, а не в поле ввода.
     * В editor попадает только через commitText() — по нажатию ▶ или ⟳.
     */
    private val buffer = StringBuilder()

    val composingText: String
        get() = buffer.toString()

    val isBufferEmpty: Boolean
        get() = buffer.isEmpty()

    fun appendChar(char: String) {
        buffer.append(char)
    }

    fun deleteLastChar(): Boolean {
        if (buffer.isEmpty()) return false
        buffer.deleteCharAt(buffer.length - 1)
        return true
    }

    fun clearBuffer() {
        buffer.clear()
    }

    // ─── Session ID для защиты от stale API-ответов ─────────────
    /**
     * Каждая IME-сессия получает уникальный ID.
     * Если API-ответ приходит с устаревшим ID — результат отбрасывается.
     */
    private val sessionCounter = AtomicLong(0)
    var activeSessionId: Long = 0
        private set

    fun startNewSession(): Long {
        activeSessionId = sessionCounter.incrementAndGet()
        clearBuffer()
        shiftMode = ShiftMode.OFF
        return activeSessionId
    }

    fun isSessionValid(capturedId: Long): Boolean {
        return capturedId == activeSessionId
    }

    // ─── Состояние перевода ────────────────────────────────────
    var isTranslating: Boolean = false
        private set

    fun setTranslating(value: Boolean) {
        isTranslating = value
    }
}
