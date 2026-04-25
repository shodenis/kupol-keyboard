package com.kupol.keyboard

/**
 * Все возможные действия клавиатуры.
 * Sealed class — исчерпывающий when без else в [MyKeyboardService].
 */
sealed class KeyAction {

    // ─── Ввод текста ────────────────────────────────────────────

    /** Обычный символ (буква, цифра, знак) */
    data class TypeChar(val char: String) : KeyAction()

    /** Удалить последний символ (буфер → поле) */
    object Delete : KeyAction()

    /** Пробел */
    object Space : KeyAction()

    /** Enter / подтвердить */
    object Enter : KeyAction()

    // ─── Модификаторы ───────────────────────────────────────────

    /** Shift / Caps Lock */
    object Shift : KeyAction()

    // ─── Языки ──────────────────────────────────────────────────

    /** Переключить язык раскладки: EN ↔ RU */
    object CycleInputLanguage : KeyAction()

    /** Переключить язык перевода (долгий тап на ⟳) */
    object CycleTargetLanguage : KeyAction()

    // ─── Основные действия ──────────────────────────────────────

    /** Вставить composing-буфер как есть (▶ Вставить) */
    object Insert : KeyAction()

    /** Перевести и вставить (⟳) */
    object Translate : KeyAction()
}
