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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

@SuppressLint("ViewConstructor")
class KeyboardView(
    context: Context,
    private val state: ImeSessionState,
    private val onAction: (KeyAction) -> Unit,
) : LinearLayout(context) {

    // ─── UI-элементы ────────────────────────────────────────────
    private val previewText: TextView
    private val previewProgress: ProgressBar
    private val inputLangButton: Button
    private val insertButton: Button
    private val translateButton: Button
    private val shiftButton: Button
    private val keysContainer: LinearLayout

    private var translateEnabled: Boolean = true

    // Двойной тап по Shift → CAPS_LOCK
    private var lastShiftTapTime: Long = 0
    private val doubleTapThreshold = 300L

    // ─── Цвета ──────────────────────────────────────────────────
    private val colorBg = Color.parseColor("#E8EAED")
    private val colorKey = Color.parseColor("#FFFFFF")
    private val colorKeyActive = Color.parseColor("#C4C7CA")
    private val colorActionPrimary = Color.parseColor("#1A73E8")
    private val colorActionSecondary = Color.parseColor("#F28B82")
    private val colorText = Color.parseColor("#202124")
    private val colorTextOnAction = Color.parseColor("#FFFFFF")

    // Должны быть объявлены до init: updateState() → rebuildKeys() читает раскладки
    private val layoutEn = arrayOf(
        arrayOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        arrayOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        arrayOf("z", "x", "c", "v", "b", "n", "m"),
    )

    private val layoutRu = arrayOf(
        arrayOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х"),
        arrayOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"),
        arrayOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю"),
    )

    private val numberRow = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    init {
        orientation = VERTICAL
        setBackgroundColor(colorBg)
        setPadding(dp(4), dp(4), dp(4), dp(8))

        // Preview strip
        val previewContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        previewText = TextView(context).apply {
            textSize = 16f
            setTextColor(colorText)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        previewProgress = ProgressBar(context).apply {
            visibility = View.GONE
            layoutParams = LayoutParams(dp(20), dp(20))
        }

        previewContainer.addView(previewText)
        previewContainer.addView(previewProgress)
        addView(previewContainer, LayoutParams(LayoutParams.MATCH_PARENT, dp(36)))

        // Action bar: [input lang] [▶ insert] [⟳ translate]
        val actionBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(2), dp(2), dp(2), dp(4))
        }

        inputLangButton = makeActionButton("EN", colorActionSecondary).apply {
            setOnClickListener { onAction(KeyAction.CycleInputLanguage) }
        }

        insertButton = makeActionButton("▶ Вставить", colorActionPrimary).apply {
            setOnClickListener { onAction(KeyAction.Insert) }
        }

        translateButton = makeActionButton("⟳ →ZH", colorActionPrimary).apply {
            setOnClickListener { onAction(KeyAction.Translate) }
            setOnLongClickListener {
                onAction(KeyAction.CycleTargetLanguage)
                true
            }
        }

        actionBar.addView(inputLangButton, actionBarParams(1f))
        actionBar.addView(insertButton, actionBarParams(1.5f))
        actionBar.addView(translateButton, actionBarParams(1.5f))
        addView(actionBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))

        // Keys container (динамически перерисовывается)
        keysContainer = LinearLayout(context).apply {
            orientation = VERTICAL
        }
        addView(keysContainer)

        // Shift button используется в bottom row — создаём заранее
        shiftButton = makeKeyButton("⇧").apply {
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

    // ─── Публичный API ──────────────────────────────────────────

    fun updateState() {
        // Preview
        previewText.text = state.composingText
        previewProgress.visibility = if (state.isTranslating) View.VISIBLE else View.GONE

        // Input lang
        inputLangButton.text = when (state.inputLanguage) {
            ImeSessionState.InputLanguage.EN -> "EN"
            ImeSessionState.InputLanguage.RU -> "RU"
        }

        // Target lang
        translateButton.text = when (state.targetLanguage) {
            ImeSessionState.TargetLanguage.RU -> "⟳ →RU"
            ImeSessionState.TargetLanguage.EN -> "⟳ →EN"
            ImeSessionState.TargetLanguage.ZH -> "⟳ →ZH"
        }
        translateButton.isEnabled = translateEnabled && !state.isTranslating
        translateButton.alpha = if (translateButton.isEnabled) 1f else 0.5f

        insertButton.isEnabled = !state.isTranslating
        insertButton.alpha = if (insertButton.isEnabled) 1f else 0.5f

        // Shift
        shiftButton.setBackgroundColor(
            when (state.shiftMode) {
                ImeSessionState.ShiftMode.OFF -> colorKey
                ImeSessionState.ShiftMode.ON -> colorKeyActive
                ImeSessionState.ShiftMode.CAPS_LOCK -> colorActionPrimary
            },
        )

        rebuildKeys()
    }

    fun setTranslateEnabled(enabled: Boolean) {
        translateEnabled = enabled
        updateState()
    }

    fun showMessage(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun rebuildKeys() {
        keysContainer.removeAllViews()

        keysContainer.addView(buildRow(numberRow, weightPerKey = 1f))

        val letters = when (state.inputLanguage) {
            ImeSessionState.InputLanguage.EN -> layoutEn
            ImeSessionState.InputLanguage.RU -> layoutRu
        }
        for (row in letters) {
            val displayRow = if (state.isUppercase) {
                row.map { it.uppercase() }.toTypedArray()
            } else {
                row
            }
            keysContainer.addView(buildRow(displayRow, weightPerKey = 1f))
        }

        val bottomRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
        }

        (shiftButton.parent as? LinearLayout)?.removeView(shiftButton)
        bottomRow.addView(shiftButton, rowKeyParams(1.5f))

        val spaceButton = makeKeyButton("").apply {
            setOnClickListener { onAction(KeyAction.Space) }
        }
        bottomRow.addView(spaceButton, rowKeyParams(4f))

        val deleteButton = makeKeyButton("⌫").apply {
            setOnClickListener { onAction(KeyAction.Delete) }
        }
        bottomRow.addView(deleteButton, rowKeyParams(1.5f))

        val enterButton = makeActionButton("↵", colorActionPrimary).apply {
            setOnClickListener { onAction(KeyAction.Enter) }
        }
        bottomRow.addView(enterButton, rowKeyParams(1.5f))

        keysContainer.addView(bottomRow)
    }

    private fun buildRow(keys: Array<String>, weightPerKey: Float): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
        }
        if (keys.isEmpty()) return row
        for (key in keys) {
            val button = makeKeyButton(key).apply {
                setOnClickListener { onAction(KeyAction.TypeChar(key)) }
            }
            row.addView(button, rowKeyParams(weightPerKey))
        }
        return row
    }

    // ─── Фабрики кнопок ─────────────────────────────────────────

    private fun makeKeyButton(label: String): Button {
        return Button(context).apply {
            text = label
            textSize = 16f
            setTextColor(colorText)
            background = roundedBackground(colorKey)
            isAllCaps = false
            minWidth = 0
            minHeight = 0
            setPadding(0, 0, 0, 0)
        }
    }

    private fun makeActionButton(label: String, bgColor: Int): Button {
        return Button(context).apply {
            text = label
            textSize = 14f
            setTextColor(colorTextOnAction)
            background = roundedBackground(bgColor)
            isAllCaps = false
            minWidth = 0
            minHeight = 0
            setPadding(dp(8), 0, dp(8), 0)
        }
    }

    private fun roundedBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(6).toFloat()
        }
    }

    // ─── LayoutParams хелперы ───────────────────────────────────

    private fun rowKeyParams(weight: Float): LayoutParams {
        return LayoutParams(0, dp(44), weight).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
    }

    private fun actionBarParams(weight: Float): LayoutParams {
        return LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
            setMargins(dp(2), 0, dp(2), 0)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
