package com.kupol.keyboard.translation

import com.kupol.keyboard.ImeSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ClaudeProvider : TranslationProvider {

    override val id: String = "claude"
    override val displayName: String = "Claude"

    private val apiUrl = "https://api.anthropic.com/v1/messages"
    private val model = "claude-sonnet-4-5"
    private val anthropicVersion = "2023-06-01"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun translate(
        text: String,
        targetLanguage: ImeSessionState.TargetLanguage,
        apiKey: String,
    ): String = withContext(Dispatchers.IO) {

        if (apiKey.isBlank()) {
            throw IllegalStateException("Claude API-ключ не задан")
        }

        val targetName = targetLanguage.toEnglishName()
        val systemPrompt = buildSystemPrompt(targetName)

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", text)
            })
        }

        val requestBody = JSONObject().apply {
            put("model", model)
            put("max_tokens", 1024)
            put("temperature", 0.3)
            put("system", systemPrompt)
            put("messages", messages)
        }

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", anthropicVersion)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                throw IOException("Claude HTTP ${response.code}: ${parseError(bodyString)}")
            }

            if (bodyString.isBlank()) {
                throw IOException("Claude: пустой ответ")
            }

            val json = JSONObject(bodyString)
            val contentArray = json.optJSONArray("content")
                ?: throw IOException("Claude: нет поля content")
            if (contentArray.length() == 0) {
                throw IOException("Claude: content пустой")
            }

            // Claude возвращает массив блоков — берём первый text-блок
            var resultText: String? = null
            for (i in 0 until contentArray.length()) {
                val block = contentArray.getJSONObject(i)
                if (block.optString("type") == "text") {
                    resultText = block.optString("text").trim()
                    break
                }
            }

            if (resultText.isNullOrEmpty()) {
                throw IOException("Claude: не найден text-блок")
            }

            sanitizeLlmOutput(resultText)
        }
    }

    private fun buildSystemPrompt(targetName: String): String {
        return """
            You are a professional translator.
            Translate the user's text to $targetName.
            Rules:
            - Output ONLY the translation. No explanations, no quotes, no prefaces.
            - Preserve the original tone, register, and formatting.
            - If the input is already in $targetName, return it unchanged.
            - Do not wrap the result in quotes or code blocks.
        """.trimIndent()
    }

    private fun sanitizeLlmOutput(raw: String): String {
        var result = raw.trim()

        val quotePairs = listOf("\"" to "\"", "«" to "»", "\u201C" to "\u201D", "'" to "'")
        for ((open, close) in quotePairs) {
            if (result.startsWith(open) && result.endsWith(close) && result.length > 1) {
                result = result.substring(open.length, result.length - close.length).trim()
            }
        }

        val prefixes = listOf("Translation:", "Перевод:", "翻译:", "译文:")
        for (prefix in prefixes) {
            if (result.length >= prefix.length &&
                result.substring(0, prefix.length).equals(prefix, ignoreCase = true)
            ) {
                result = result.substring(prefix.length).trim()
            }
        }

        return result
    }

    private fun parseError(body: String): String {
        if (body.isBlank()) return "empty body"
        return try {
            val err = JSONObject(body).optJSONObject("error")
            err?.optString("message").takeUnless { it.isNullOrBlank() } ?: body.take(200)
        } catch (_: Exception) {
            body.take(200)
        }
    }
}
