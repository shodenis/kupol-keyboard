package com.kupol.keyboard

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.kupol.keyboard.storage.KeyStorage

class SettingsActivity : Activity() {

    private lateinit var inputApiKey: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
        }

        TextView(this).apply {
            text = "API Keys Configuration"
            textSize = 20f
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).also { it.setMargins(0, 0, 0, 24) }
            layout.addView(this)
        }

        inputApiKey = EditText(this).apply {
            hint = "Enter DeepSeek API Key"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            KeyStorage.getDeepSeekKey(this@SettingsActivity)?.let { setText(it) }
            layout.addView(this)
        }

        Button(this).apply {
            text = "Save Key"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).also { it.setMargins(0, 16, 0, 0) }
            setOnClickListener {
                val key = inputApiKey.text.toString().trim()
                if (key.isNotEmpty()) {
                    KeyStorage.saveDeepSeekKey(this@SettingsActivity, key)
                    Toast.makeText(this@SettingsActivity, "Key saved!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SettingsActivity, "Key cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            layout.addView(this)
        }

        setContentView(layout)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }
}
