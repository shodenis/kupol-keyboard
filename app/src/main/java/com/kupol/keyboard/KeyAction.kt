package com.kupol.keyboard

/**
 * Действия клавиатуры (исчерпывающий when в [MyKeyboardService]).
 */
sealed class KeyAction {
    data class TypeChar(val char: String) : KeyAction()
    object Delete : KeyAction()
    object Space : KeyAction()
    object Enter : KeyAction()
    object Shift : KeyAction()
    object CycleInputLanguage : KeyAction()
    object TranslateWholeField : KeyAction()
    object MicToggle : KeyAction()
}
