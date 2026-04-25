package com.kupol.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

@SuppressLint("ViewConstructor")
class KeyboardView(
    context: Context,
    private val state: ImeSessionState,
    private val onAction: (KeyAction) -> Unit,
) : LinearLayout(context) {

    private val inputLangPill: Button
    private val targetLangPill: Button
    private val shiftButton: Button
    private val keysContainer: LinearLayout

    private var translateAndSpeechEnabled: Boolean = true

    private var lastShiftTapTime: Long = 0
    private val doubleTapThreshold = 300L

    private val colorBoard = Color.parseColor("#D3D3D3")
    private val colorKey = Color.parseColor("#FFFFFF")
    private val colorKeyActive = Color.parseColor("#C4C7CA")
    private val colorShiftActive = Color.parseColor("#B0B0B0")
    private val colorText = Color.parseColor("#202124")
    private val colorPillStroke = Color.parseColor("#C8C8C8")

    private val layoutEn = arrayOf(
        arrayOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        arrayOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        arrayOf("z", "x", "c", "v", "b", "n", "m"),
    )

    /** Для ZH — латиница под пиньинь (как на многих клавиатурах). */
    private val layoutZh get() = layoutEn

    private val layoutRu = arrayOf(
        arrayOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х"),
        arrayOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"),
        arrayOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю"),
    )

    private val numberRow = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    init {
        orientation = VERTICAL
        setBackgroundColor(colorBoard)
        setPadding(dp(4), dp(4), dp(4), dp(4))

        // ─── Speakly-полоса: [ввод] → [перевод] ─────────────────
        val topBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(6))
        }

        inputLangPill = makeTopPill().apply {
            setOnClickListener { onAction(KeyAction.CycleInputLanguage) }
        }

        val arrow = TextView(context).apply {
            text = "→"
            textSize = 18f
            setTextColor(colorText)
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(8), 0, dp(8), 0)
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        targetLangPill = makeTopPill().apply {
            setOnClickListener { onAction(KeyAction.CycleTargetLanguage) }
        }

        topBar.addView(inputLangPill, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        topBar.addView(arrow)
        topBar.addView(targetLangPill, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(topBar)

        keysContainer = LinearLayout(context).apply { orientation = VERTICAL }
        addView(keysContainer)

        shiftButton = makeLetterKey("⇧").apply {
            setOnClickListener {
                val now = System.currentTimeMillis()
                if (now - lastShiftTapTime < doubleTapThreshold) {
                    onAction(KeyAction.Shift)
                    onAction(KeyAction.Shift)
                } else {
                    onAction(KeyAction.Shift)
                }
                lastShiftTapTime = now
            }
        }

        updateState()
    }

    fun updateState() {
        inputLangPill.text = state.inputLanguage.name
        targetLangPill.text = state.targetLanguage.name

        val busy = state.isTranslating
        inputLangPill.isEnabled = !busy
        targetLangPill.isEnabled = !busy
        inputLangPill.alpha = if (busy) 0.5f else 1f
        targetLangPill.alpha = if (busy) 0.5f else 1f

        shiftButton.background = keyBackground(
            when (state.shiftMode) {
                ImeSessionState.ShiftMode.OFF -> colorKey
                ImeSessionState.ShiftMode.ON -> colorKeyActive
                ImeSessionState.ShiftMode.CAPS_LOCK -> colorShiftActive
            },
        )

        rebuildKeys()
    }

    fun setTranslateEnabled(enabled: Boolean) {
        translateAndSpeechEnabled = enabled
        updateState()
    }

    fun showMessage(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun makeTopPill(): Button {
        return Button(context).apply {
            textSize = 15f
            setTextColor(colorText)
            background = pillBackground(colorKey)
            isAllCaps = false
            minHeight = 0
            minWidth = 0
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
    }

    private fun pillBackground(fill: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), colorPillStroke)
        }
    }

    private fun keyBackground(fill: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(8).toFloat()
        }
    }

    private fun rebuildKeys() {
        keysContainer.removeAllViews()

        keysContainer.addView(buildRow(numberRow, weightPerKey = 1f))

        val letters = when (state.inputLanguage) {
            ImeSessionState.InputLanguage.EN -> layoutEn
            ImeSessionState.InputLanguage.RU -> layoutRu
            ImeSessionState.InputLanguage.ZH -> layoutZh
        }
        for (row in letters) {
            val displayRow =
                if (state.isUppercase) row.map { it.uppercase() }.toTypedArray() else row
            keysContainer.addView(buildRow(displayRow, weightPerKey = 1f))
        }

        // Ряд Shift … ⌫
        val toolRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        (shiftButton.parent as? LinearLayout)?.removeView(shiftButton)
        toolRow.addView(shiftButton, rowKeyParams(1.4f))
        val spacer = View(context).apply { layoutParams = rowKeyParams(2.2f) }
        toolRow.addView(spacer)
        val del = makeLetterKey("⌫").apply {
            setOnClickListener { onAction(KeyAction.Delete) }
        }
        toolRow.addView(del, rowKeyParams(1.4f))
        keysContainer.addView(toolRow)

        // Нижний ряд: язык | пробел | 🎤 | 🎤→ | ⟳ | Enter
        val bottom = LinearLayout(context).apply { orientation = HORIZONTAL }

        val langKey = makeLetterKey(state.inputLanguage.name).apply {
            setOnClickListener { onAction(KeyAction.CycleInputLanguage) }
        }
        bottom.addView(langKey, rowKeyParams(1f))

        val space = makeLetterKey("").apply {
            setOnClickListener { onAction(KeyAction.Space) }
        }
        bottom.addView(space, rowKeyParams(2.8f))

        val mic = makeLetterKey("🎤").apply {
            setOnClickListener { onAction(KeyAction.MicPlain) }
            isEnabled = translateAndSpeechEnabled && !state.isTranslating
            alpha = if (isEnabled) 1f else 0.45f
        }
        bottom.addView(mic, rowKeyParams(1f))

        val micTr = makeLetterKey("🎤→").apply {
            textSize = 14f
            setOnClickListener { onAction(KeyAction.MicTranslate) }
            isEnabled = translateAndSpeechEnabled && !state.isTranslating
            alpha = if (isEnabled) 1f else 0.45f
        }
        bottom.addView(micTr, rowKeyParams(1.1f))

        val retr = makeLetterKey("⟳").apply {
            setOnClickListener { onAction(KeyAction.TranslateWholeField) }
            isEnabled = translateAndSpeechEnabled && !state.isTranslating
            alpha = if (isEnabled) 1f else 0.45f
        }
        bottom.addView(retr, rowKeyParams(1f))

        val enter = makeLetterKey("↵").apply {
            setOnClickListener { onAction(KeyAction.Enter) }
        }
        bottom.addView(enter, rowKeyParams(1.1f))

        keysContainer.addView(bottom)
    }

    private fun buildRow(keys: Array<String>, weightPerKey: Float): LinearLayout {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        if (keys.isEmpty()) return row
        for (key in keys) {
            val button = makeLetterKey(key).apply {
                setOnClickListener { onAction(KeyAction.TypeChar(key)) }
            }
            row.addView(button, rowKeyParams(weightPerKey))
        }
        return row
    }

    private fun makeLetterKey(label: String): Button {
        return Button(context).apply {
            text = label
            textSize = 16f
            setTextColor(colorText)
            background = keyBackground(colorKey)
            isAllCaps = false
            minWidth = 0
            minHeight = 0
            minimumHeight = dp(48)
            setPadding(0, 0, 0, 0)
        }
    }

    private fun rowKeyParams(weight: Float): LayoutParams {
        return LayoutParams(0, dp(48), weight).apply {
            setMargins(dp(4), dp(4), dp(4), dp(4))
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
