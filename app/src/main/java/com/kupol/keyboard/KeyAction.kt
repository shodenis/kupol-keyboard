package com.kupol.keyboard

sealed class KeyAction {
    /** Обычный символ (буква, цифра, знак препинания) */
    data class Character(val char: String) : KeyAction()

    /** Удаление символа */
    object Backspace : KeyAction()

    /** Кнопка перевода */
    object Translate : KeyAction()

    /** Enter */
    data class Enter(val code: Int) : KeyAction()
}
