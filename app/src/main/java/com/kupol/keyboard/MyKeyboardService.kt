package com.kupol.keyboard

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import com.kupol.keyboard.translation.TranslationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * IME-сервис: [KeyboardView] → [KeyAction] → [InputConnection];
 * перевод: [TranslationManager] (внутри — [com.kupol.keyboard.storage.KeyStorage] и провайдеры).
 */
class MyKeyboardService : InputMethodService() {

    private var keyboardView: KeyboardView? = null
    private lateinit var translationManager: TranslationManager

    /** Состояние сессии: язык цели перевода, счётчики; клавиатура пока без переключателей. */
    private val sessionState = ImeSessionState()

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var currentEditorInfo: EditorInfo? = null

    override fun onCreate() {
        super.onCreate()
        translationManager = TranslationManager(this)
    }

    override fun onCreateInputView(): View {
        return KeyboardView(this).apply {
            onKeyActionListener = { action ->
                handleAction(action)
            }
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
    }

    override fun onDestroyInputView() {
        super.onDestroyInputView()
        keyboardView = null
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun handleAction(action: KeyAction) {
        when (action) {
            is KeyAction.Character -> {
                currentInputConnection?.commitText(action.char, 1)
            }
            is KeyAction.Backspace -> {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            }
            is KeyAction.Enter -> {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            }
            is KeyAction.Translate -> {
                performTranslation()
            }
        }
    }

    private fun performTranslation() {
        val ic = currentInputConnection ?: return

        val inputType = (currentEditorInfo?.inputType ?: 0) and EditorInfo.TYPE_MASK_VARIATION
        val isPasswordField =
            inputType == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                inputType == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                inputType == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                inputType == EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD
        if (isPasswordField) {
            Toast.makeText(this, "Перевод недоступен в поле пароля", Toast.LENGTH_SHORT).show()
            return
        }

        val originalText = ic.getTextBeforeCursor(100, 0)?.toString()?.trim().orEmpty()

        if (originalText.isEmpty()) {
            Toast.makeText(this, "Nothing to translate", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Translating...", Toast.LENGTH_SHORT).show()

        serviceScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    translationManager.translate(
                        text = originalText,
                        targetLanguage = sessionState.targetLanguage,
                    )
                }

                result.fold(
                    onSuccess = { translatedText ->
                        val conn = currentInputConnection ?: return@fold
                        conn.deleteSurroundingText(originalText.length, 0)
                        conn.commitText(translatedText, 1)
                        Toast.makeText(this@MyKeyboardService, "Translated!", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = {
                        Toast.makeText(
                            this@MyKeyboardService,
                            "Translation failed: ${it.message ?: "API Error"}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MyKeyboardService, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendDownUpKeyEvents(keyCode: Int) {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}
