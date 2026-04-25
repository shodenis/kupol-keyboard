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

    /** Смена языка ввода / раскладки (поле не трогаем). */
    object CycleInputLanguage : KeyAction()

    /** Смена языка перевода (поле не трогаем). */
    object CycleTargetLanguage : KeyAction()

    /** Перевести весь текст в поле и заменить результатом. */
    object TranslateWholeField : KeyAction()

    /** Распознавание речи → вставка как есть (локаль раскладки). */
    object MicPlain : KeyAction()

    /** Распознавание → перевод на target → вставка. */
    object MicTranslate : KeyAction()
}
