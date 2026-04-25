package com.kupol.keyboard

import java.util.concurrent.atomic.AtomicLong

/**
 * Состояние IME: язык ввода (раскладка), цель перевода, Shift, сессия для сетевых ответов.
 */
class ImeSessionState {

    /** Язык ввода — раскладка + локаль распознавания речи. */
    enum class InputLanguage {
        EN,
        RU,
        ZH,
        ;

        fun next(): InputLanguage = when (this) {
            EN -> RU
            RU -> ZH
            ZH -> EN
        }

        /** Тег для [android.speech.RecognizerIntent]. */
        fun speechLocaleTag(): String = when (this) {
            EN -> "en-US"
            RU -> "ru-RU"
            ZH -> "zh-CN"
        }

        fun toTargetLanguage(): TargetLanguage = when (this) {
            EN -> TargetLanguage.EN
            RU -> TargetLanguage.RU
            ZH -> TargetLanguage.ZH
        }
    }

    /** Язык перевода (цель) всегда синхронизирован с языком раскладки. */
    enum class TargetLanguage { RU, EN, ZH }

    enum class ShiftMode { OFF, ON, CAPS_LOCK }

    var inputLanguage: InputLanguage = InputLanguage.EN
        private set

    var targetLanguage: TargetLanguage = inputLanguage.toTargetLanguage()
        private set

    fun cycleInputLanguage(): InputLanguage {
        inputLanguage = inputLanguage.next()
        targetLanguage = inputLanguage.toTargetLanguage()
        shiftMode = ShiftMode.OFF
        return inputLanguage
    }

    var shiftMode: ShiftMode = ShiftMode.OFF
        private set

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

    private val sessionCounter = AtomicLong(0)
    var activeSessionId: Long = 0
        private set

    fun startNewSession(): Long {
        activeSessionId = sessionCounter.incrementAndGet()
        shiftMode = ShiftMode.OFF
        return activeSessionId
    }

    fun isSessionValid(capturedId: Long): Boolean = capturedId == activeSessionId

    var isTranslating: Boolean = false
        private set

    fun setTranslating(value: Boolean) {
        isTranslating = value
    }
}
