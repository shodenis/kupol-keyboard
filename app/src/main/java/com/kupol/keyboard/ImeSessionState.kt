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
    }

    /** Язык перевода (цель). */
    enum class TargetLanguage {
        RU,
        EN,
        ZH,
        ;

        fun next(): TargetLanguage = when (this) {
            RU -> EN
            EN -> ZH
            ZH -> RU
        }
    }

    enum class ShiftMode { OFF, ON, CAPS_LOCK }

    var inputLanguage: InputLanguage = InputLanguage.EN

    fun cycleInputLanguage(): InputLanguage {
        inputLanguage = inputLanguage.next()
        shiftMode = ShiftMode.OFF
        return inputLanguage
    }

    var targetLanguage: TargetLanguage = TargetLanguage.ZH

    fun cycleTargetLanguage(): TargetLanguage {
        targetLanguage = targetLanguage.next()
        return targetLanguage
    }

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
