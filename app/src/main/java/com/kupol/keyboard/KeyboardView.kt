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
    private val listeningBanner: TextView
    private val keysContainer: LinearLayout
    private val languageButton: Button
    private val micButton: Button
    private val translateButton: Button
    private val shiftButton: Button
    private val letterButtons: MutableList<Pair<Button, String>> = mutableListOf()

    private var allowTranslateAndMic = true
    private var renderedLanguage: ImeSessionState.InputLanguage? = null
    private var lastShiftTapTime: Long = 0
    private val doubleTapThreshold = 300L

    private val colorBoard = Color.parseColor("#D3D3D3")
    private val colorKey = Color.parseColor("#FFFFFF")
    private val colorKeyActive = Color.parseColor("#C4C7CA")
    private val colorShiftActive = Color.parseColor("#B0B0B0")
    private val colorText = Color.parseColor("#202124")
    private val colorListening = Color.parseColor("#111111")

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
    private val layoutZh get() = layoutEn
    private val numberRow = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    init {
        orientation = VERTICAL
        setBackgroundColor(colorBoard)
        setPadding(dp(4), dp(4), dp(4), dp(4))

        listeningBanner = TextView(context).apply {
            text = "Слушаю... Нажмите для завершения"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = keyBackground(colorListening)
            visibility = GONE
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(dp(4), dp(4), dp(4), dp(6)) }
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                onAction(KeyAction.MicToggle)
            }
        }
        addView(listeningBanner)

        keysContainer = LinearLayout(context).apply { orientation = VERTICAL }
        addView(keysContainer)

        languageButton = makeKeyButton(state.inputLanguage.name)
        micButton = makeKeyButton("🎤")
        translateButton = makeKeyButton("⟳")
        shiftButton = makeKeyButton("⇧")

        bindTap(languageButton) { onAction(KeyAction.CycleInputLanguage) }
        bindTap(micButton) { onAction(KeyAction.MicToggle) }
        bindTap(translateButton) { onAction(KeyAction.TranslateWholeField) }
        bindTap(shiftButton) {
            val now = System.currentTimeMillis()
            if (now - lastShiftTapTime < doubleTapThreshold) {
                onAction(KeyAction.Shift)
                onAction(KeyAction.Shift)
            } else {
                onAction(KeyAction.Shift)
            }
            lastShiftTapTime = now
        }

        rebuildForLanguage()
        updateState(listening = false)
    }

    fun setTranslateEnabled(enabled: Boolean) {
        allowTranslateAndMic = enabled
        updateState()
    }

    fun updateState(listening: Boolean? = null) {
        if (renderedLanguage != state.inputLanguage) {
            rebuildForLanguage()
        }
        languageButton.text = state.inputLanguage.name
        updateShiftVisual()
        updateLettersCase()
        val interactive = allowTranslateAndMic && !state.isTranslating
        micButton.isEnabled = interactive
        translateButton.isEnabled = interactive
        micButton.alpha = if (interactive) 1f else 0.45f
        translateButton.alpha = if (interactive) 1f else 0.45f
        listening?.let { listeningBanner.visibility = if (it) VISIBLE else GONE }
    }

    fun showMessage(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun rebuildForLanguage() {
        renderedLanguage = state.inputLanguage
        keysContainer.removeAllViews()
        letterButtons.clear()

        keysContainer.addView(buildRow(numberRow, 1f))

        val letters = when (state.inputLanguage) {
            ImeSessionState.InputLanguage.EN -> layoutEn
            ImeSessionState.InputLanguage.RU -> layoutRu
            ImeSessionState.InputLanguage.ZH -> layoutZh
        }
        for (row in letters) {
            keysContainer.addView(buildRow(row, 1f))
        }

        val toolRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        (shiftButton.parent as? LinearLayout)?.removeView(shiftButton)
        toolRow.addView(shiftButton, rowKeyParams(1.4f))
        toolRow.addView(View(context), rowKeyParams(2.2f))
        val del = makeKeyButton("⌫")
        bindTap(del) { onAction(KeyAction.Delete) }
        toolRow.addView(del, rowKeyParams(1.4f))
        keysContainer.addView(toolRow)

        val bottom = LinearLayout(context).apply { orientation = HORIZONTAL }
        val space = makeKeyButton("")
        bindTap(space) { onAction(KeyAction.Space) }
        val enter = makeKeyButton("↵")
        bindTap(enter) { onAction(KeyAction.Enter) }

        (languageButton.parent as? LinearLayout)?.removeView(languageButton)
        (micButton.parent as? LinearLayout)?.removeView(micButton)
        (translateButton.parent as? LinearLayout)?.removeView(translateButton)
        bottom.addView(languageButton, rowKeyParams(1f))
        bottom.addView(space, rowKeyParams(3.2f))
        bottom.addView(micButton, rowKeyParams(1f))
        bottom.addView(translateButton, rowKeyParams(1f))
        bottom.addView(enter, rowKeyParams(1.1f))
        keysContainer.addView(bottom)
    }

    private fun buildRow(keys: Array<String>, weightPerKey: Float): LinearLayout {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        for (base in keys) {
            val btn = makeKeyButton(base)
            bindTap(btn) { onAction(KeyAction.TypeChar(base)) }
            letterButtons += btn to base
            row.addView(btn, rowKeyParams(weightPerKey))
        }
        return row
    }

    private fun bindTap(button: Button, onClick: () -> Unit) {
        button.setOnClickListener {
            button.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        }
    }

    private fun updateLettersCase() {
        val upper = state.isUppercase
        for ((btn, base) in letterButtons) {
            btn.text = if (upper) base.uppercase() else base.lowercase()
        }
    }

    private fun updateShiftVisual() {
        shiftButton.background = keyBackground(
            when (state.shiftMode) {
                ImeSessionState.ShiftMode.OFF -> colorKey
                ImeSessionState.ShiftMode.ON -> colorKeyActive
                ImeSessionState.ShiftMode.CAPS_LOCK -> colorShiftActive
            },
        )
    }

    private fun makeKeyButton(label: String): Button {
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

    private fun keyBackground(fill: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(8).toFloat()
        }
    }

    private fun rowKeyParams(weight: Float): LayoutParams {
        return LayoutParams(0, dp(48), weight).apply {
            setMargins(dp(4), dp(4), dp(4), dp(4))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
