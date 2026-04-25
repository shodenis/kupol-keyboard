package com.kupol.keyboard

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.kupol.keyboard.translation.TranslationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * IME: [KeyboardView] → [KeyAction] → буфер / [InputConnection];
 * перевод: [TranslationManager] + [KeyStorage].
 */
class MyKeyboardService : InputMethodService() {

    private val sessionState = ImeSessionState()
    private var keyboardView: KeyboardView? = null
    private lateinit var translationManager: TranslationManager

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var translationJob: Job? = null

    private var currentEditorInfo: EditorInfo? = null

    override fun onCreate() {
        super.onCreate()
        translationManager = TranslationManager(this)
    }

    override fun onCreateInputView(): View {
        return KeyboardView(this, sessionState) { action ->
            handleKeyAction(action)
        }.also { keyboardView = it }
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        currentEditorInfo = info
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentEditorInfo = info
        sessionState.startNewSession()
        if (info != null) {
            val inputType = info.inputType and EditorInfo.TYPE_MASK_VARIATION
            val isPasswordField =
                inputType == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                    inputType == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    inputType == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    inputType == EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD
            keyboardView?.setTranslateEnabled(!isPasswordField)
        } else {
            keyboardView?.setTranslateEnabled(true)
        }
        keyboardView?.updateState()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        translationJob?.cancel()
        sessionState.setTranslating(false)
        if (finishingInput) {
            keyboardView = null
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    // ─── Обработка действий клавиатуры ─────────────────────────

    private fun handleKeyAction(action: KeyAction) {
        val ic = currentInputConnection
        if (ic == null) {
            if (action is KeyAction.Translate) {
                keyboardView?.showMessage("Нет соединения с полем ввода")
            }
            return
        }

        when (action) {
            is KeyAction.TypeChar -> handleTypeChar(ic, action.char)
            is KeyAction.Delete -> handleDelete(ic)
            is KeyAction.Space -> handleSpace(ic)
            is KeyAction.Enter -> handleEnter(ic)
            is KeyAction.Shift -> sessionState.toggleShift()
            is KeyAction.CycleInputLanguage -> handleLanguageSwitch(ic)
            is KeyAction.CycleTargetLanguage -> {
                sessionState.cycleTargetLanguage()
            }
            is KeyAction.Insert -> handleInsert(ic)
            is KeyAction.Translate -> {
                if (isPasswordContext()) {
                    keyboardView?.showMessage("Перевод недоступен в поле пароля")
                } else {
                    handleTranslate(ic)
                }
            }
        }
        keyboardView?.updateState()
    }

    private fun isPasswordContext(): Boolean {
        val inputType = (currentEditorInfo?.inputType ?: 0) and EditorInfo.TYPE_MASK_VARIATION
        return inputType == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
            inputType == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            inputType == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            inputType == EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun handleTypeChar(_ic: InputConnection, char: String) {
        val output = if (sessionState.isUppercase) char.uppercase() else char.lowercase()
        sessionState.appendChar(output)
        sessionState.resetShiftAfterKey()
    }

    private fun handleDelete(ic: InputConnection) {
        if (!sessionState.deleteLastChar()) {
            ic.deleteSurroundingText(1, 0)
        }
    }

    private fun handleSpace(ic: InputConnection) {
        if (sessionState.isBufferEmpty) {
            ic.commitText(" ", 1)
        } else {
            ic.commitText(sessionState.composingText + " ", 1)
            sessionState.clearBuffer()
        }
    }

    private fun handleEnter(ic: InputConnection) {
        if (!sessionState.isBufferEmpty) {
            ic.commitText(sessionState.composingText, 1)
            sessionState.clearBuffer()
        }
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    private fun handleLanguageSwitch(ic: InputConnection) {
        if (!sessionState.isBufferEmpty) {
            ic.commitText(sessionState.composingText, 1)
            sessionState.clearBuffer()
        }
        sessionState.cycleInputLanguage()
        sessionState.shiftMode = ImeSessionState.ShiftMode.OFF
    }

    private fun handleInsert(ic: InputConnection) {
        if (sessionState.isBufferEmpty) return
        ic.commitText(sessionState.composingText, 1)
        sessionState.clearBuffer()
    }

    private fun handleTranslate(ic: InputConnection) {
        if (sessionState.isTranslating) return

        val selectedText = ic.getSelectedText(0)?.toString()
        val textToTranslate = when {
            !selectedText.isNullOrEmpty() -> selectedText
            !sessionState.isBufferEmpty -> sessionState.composingText
            else -> {
                val before = ic.getTextBeforeCursor(2000, 0)?.toString().orEmpty()
                val trimmed = before.trimEnd()
                if (trimmed.isNotEmpty()) trimmed
                else {
                    keyboardView?.showMessage("Выделите текст, наберите в буфере или вставьте текст")
                    return
                }
            }
        }

        if (textToTranslate.length > 500) {
            keyboardView?.showMessage("Текст слишком длинный (макс. 500 символов)")
            return
        }

        val capturedSessionId = sessionState.activeSessionId
        val targetLanguage = sessionState.targetLanguage
        val hadSelection = !selectedText.isNullOrEmpty()

        sessionState.setTranslating(true)
        keyboardView?.updateState()

        translationJob = serviceScope.launch {
            val result = withContext(Dispatchers.IO) {
                translationManager.translate(
                    text = textToTranslate,
                    targetLanguage = targetLanguage,
                )
            }

            if (!sessionState.isSessionValid(capturedSessionId) || currentInputConnection == null) {
                sessionState.setTranslating(false)
                keyboardView?.updateState()
                return@launch
            }
            sessionState.setTranslating(false)
            val conn = currentInputConnection ?: return@launch

            result.fold(
                onSuccess = { translatedText ->
                    if (hadSelection) {
                        conn.commitText(translatedText, 1)
                    } else if (!sessionState.isBufferEmpty) {
                        conn.commitText(translatedText, 1)
                        sessionState.clearBuffer()
                    } else {
                        val del = textToTranslate.length
                        if (del > 0) {
                            conn.deleteSurroundingText(del, 0)
                        }
                        conn.commitText(translatedText, 1)
                    }
                },
                onFailure = { e ->
                    keyboardView?.showMessage(e.message ?: "Ошибка перевода")
                },
            )
            keyboardView?.updateState()
        }
    }
}
