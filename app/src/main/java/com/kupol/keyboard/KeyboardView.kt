package com.kupol.keyboard

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout

class KeyboardView(context: Context) : LinearLayout(context) {

    var onKeyActionListener: ((KeyAction) -> Unit)? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.GRAY)
        setPadding(8, 8, 8, 8)

        val row1 = createRow()
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p").forEach { char ->
            row1.addView(
                createKeyButton(char) {
                    onKeyActionListener?.invoke(KeyAction.Character(char))
                },
            )
        }
        addView(row1)

        val actionRow = createRow()

        actionRow.addView(
            createKeyButton("Del", Color.RED) {
                onKeyActionListener?.invoke(KeyAction.Backspace)
            },
        )

        actionRow.addView(
            createKeyButton("Trans", Color.BLUE) {
                onKeyActionListener?.invoke(KeyAction.Translate)
            },
        )

        actionRow.addView(
            createKeyButton("Enter", Color.GREEN) {
                onKeyActionListener?.invoke(KeyAction.Enter(0))
            },
        )

        addView(actionRow)
    }

    private fun createRow(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ).also { it.setMargins(0, 0, 0, 4) }
            gravity = Gravity.CENTER
        }
    }

    private fun createKeyButton(
        text: String,
        bgColor: Int = Color.WHITE,
        onClick: () -> Unit,
    ): Button {
        return Button(context).apply {
            this.text = text
            setBackgroundColor(bgColor)
            setTextColor(Color.BLACK)
            layoutParams = LayoutParams(
                0,
                LayoutParams.WRAP_CONTENT,
                1f,
            ).also { it.setMargins(2, 0, 2, 0) }
            setOnClickListener { onClick() }
        }
    }
}
