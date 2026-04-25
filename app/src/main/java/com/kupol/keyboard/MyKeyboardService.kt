package com.kupol.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.os.Build
import androidx.core.content.ContextCompat
import com.kupol.keyboard.translation.TranslationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * IME: полоса языков Speakly, ввод commitText, ⟳ = весь текст поля, речь.
 */
class MyKeyboardService : InputMethodService() {

    private val sessionState = ImeSessionState()
    private var keyboardView: KeyboardView? = null
    private lateinit var translationManager: TranslationManager

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var translationJob: Job? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false

    private var currentEditorInfo: EditorInfo? = null

    override fun onCreate() {
        super.onCreate()
        translationManager = TranslationManager(this)
    }

    override fun onCreateInputView(): View {
        return KeyboardView(this, sessionState) { handleKeyAction(it) }
            .also { keyboardView = it }
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        currentEditorInfo = info
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentEditorInfo = info
        sessionState.startNewSession()
        keyboardView?.setTranslateEnabled(!isPasswordContext())
        keyboardView?.updateState(listening = false)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        translationJob?.cancel()
        stopSpeechInternal()
        sessionState.setTranslating(false)
        isListening = false
        if (finishingInput) {
            keyboardView = null
        }
    }

    override fun onDestroy() {
        stopSpeechInternal()
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun handleKeyAction(action: KeyAction) {
        when (action) {
            is KeyAction.CycleInputLanguage -> {
                sessionState.cycleInputLanguage()
                keyboardView?.updateState()
            }
            is KeyAction.MicToggle -> {
                if (isPasswordContext()) {
                    keyboardView?.showMessage("Голосовой ввод недоступен в поле пароля")
                    return
                }
                if (isListening) {
                    stopSpeechInternal()
                    isListening = false
                    keyboardView?.updateState(listening = false)
                } else {
                    startSpeechRecognition()
                }
            }
            is KeyAction.TypeChar -> {
                val ic = currentInputConnection ?: return
                val out =
                    if (sessionState.isUppercase) action.char.uppercase() else action.char.lowercase()
                ic.commitText(out, 1)
                sessionState.resetShiftAfterKey()
                keyboardView?.updateState()
            }
            is KeyAction.Delete -> {
                val ic = currentInputConnection ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    ic.deleteSurroundingTextInCodePoints(1, 0)
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
            }
            is KeyAction.Space -> {
                val ic = currentInputConnection ?: return
                ic.commitText(" ", 1)
            }
            is KeyAction.Enter -> {
                val ic = currentInputConnection ?: return
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            is KeyAction.Shift -> {
                sessionState.toggleShift()
                keyboardView?.updateState()
            }
            is KeyAction.TranslateWholeField -> {
                if (isPasswordContext()) {
                    keyboardView?.showMessage("Перевод недоступен в поле пароля")
                    return
                }
                val ic = currentInputConnection ?: run {
                    keyboardView?.showMessage("Нет поля ввода")
                    return
                }
                translateWholeField(ic)
            }
        }
    }

    private fun isPasswordContext(): Boolean {
        val inputType = (currentEditorInfo?.inputType ?: 0) and EditorInfo.TYPE_MASK_VARIATION
        return inputType == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
            inputType == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            inputType == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            inputType == EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun textAroundCursor(ic: InputConnection): Pair<String, Int> {
        val before = ic.getTextBeforeCursor(500_000, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(500_000, 0)?.toString().orEmpty()
        return Pair(before + after, before.length)
    }

    private fun replaceAroundCursor(ic: InputConnection, beforeLen: Int, oldLen: Int, newText: String) {
        val afterLen = oldLen - beforeLen
        ic.beginBatchEdit()
        try {
            ic.deleteSurroundingText(beforeLen, afterLen)
            ic.commitText(newText, 1)
        } finally {
            ic.endBatchEdit()
        }
    }

    private fun translateWholeField(ic: InputConnection) {
        if (sessionState.isTranslating) return
        val (full, beforeLen) = textAroundCursor(ic)
        if (full.isEmpty()) {
            keyboardView?.showMessage("Нет текста для перевода")
            return
        }
        if (full.length > 50_000) {
            keyboardView?.showMessage("Слишком длинный текст")
            return
        }

        val capturedSessionId = sessionState.activeSessionId
        val target = sessionState.inputLanguage.toTargetLanguage()

        sessionState.setTranslating(true)
        keyboardView?.updateState()

        translationJob = serviceScope.launch {
            val result = withContext(Dispatchers.IO) {
                translationManager.translate(full, target)
            }
            if (!sessionState.isSessionValid(capturedSessionId) || currentInputConnection == null) {
                sessionState.setTranslating(false)
                keyboardView?.updateState()
                return@launch
            }
            sessionState.setTranslating(false)
            val conn = currentInputConnection ?: return@launch
            result.fold(
                onSuccess = { translated ->
                    replaceAroundCursor(conn, beforeLen, full.length, translated)
                },
                onFailure = { e ->
                    keyboardView?.showMessage(e.message ?: "Ошибка перевода")
                },
            )
            keyboardView?.updateState()
        }
    }

    private fun startSpeechRecognition() {
        if (sessionState.isTranslating) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            keyboardView?.showMessage("Распознавание речи недоступно")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            keyboardView?.showMessage("Разрешите микрофон для Kupol в настройках приложения")
            return
        }

        stopSpeechInternal()
        isListening = true
        keyboardView?.updateState(listening = true)

        val sr = SpeechRecognizer.createSpeechRecognizer(this).also { speechRecognizer = it }
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                isListening = false
                keyboardView?.updateState(listening = false)
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Ошибка микрофона"
                    SpeechRecognizer.ERROR_CLIENT -> ""
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет разрешения микрофона"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Ошибка сети"
                    SpeechRecognizer.ERROR_NO_MATCH -> "Не распознано"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознаватель занят"
                    SpeechRecognizer.ERROR_SERVER -> "Ошибка сервера"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Нет речи"
                    else -> "Ошибка распознавания"
                }
                if (msg.isNotEmpty()) keyboardView?.showMessage(msg)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                keyboardView?.updateState(listening = false)
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = list?.firstOrNull()?.trim().orEmpty()
                if (spoken.isEmpty()) return
                onSpeechText(spoken)
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val locale = sessionState.inputLanguage.speechLocaleTag()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        sr.startListening(intent)
    }

    private fun onSpeechText(spoken: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(spoken, 1)
    }

    private fun stopSpeechInternal() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }
}
