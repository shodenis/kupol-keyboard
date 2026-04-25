package com.kupol.keyboard.translation

import com.kupol.keyboard.ImeSessionState
import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Сеть DeepSeek (OpenAI-совместимый chat completions).
 * Ключ передаётся снаружи ([TranslationManager]); JSON собирается через [JSONObject].
 */
class DeepSeekProvider : TranslationProvider {

    override val id: String = "deepseek"
    override val displayName: String = "DeepSeek"

    /** Официальный путь — `/v1/chat/completions` (в ТЗ без `v1` — так не отвечает API). */
    private val apiUrl = "https://api.deepseek.com/v1/chat/completions"
    private val model = "deepseek-chat"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun translate(
        text: String,
        targetLanguage: ImeSessionState.TargetLanguage,
        apiKey: String,
    ): String = suspendCancellableCoroutine { cont ->
        if (apiKey.isBlank()) {
            cont.resumeWithException(IllegalStateException("DeepSeek API-ключ не задан"))
            return@suspendCancellableCoroutine
        }

        val targetLangName = targetLanguage.toEnglishName()

        val jsonBody = JSONObject().apply {
            put("model", model)
            put("stream", false)
            put(
                "messages",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "system")
                            put(
                                "content",
                                "You are a professional translator. Translate the following text to $targetLangName.",
                            )
                        },
                    )
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", text)
                        },
                    )
                },
            )
        }

        val requestBody = jsonBody.toString().toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }

        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        if (!cont.isActive) return@use

                        val bodyString = try {
                            resp.body?.string().orEmpty()
                        } catch (e: Exception) {
                            if (cont.isActive) cont.resumeWithException(e)
                            return@use
                        }

                        if (!resp.isSuccessful) {
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    IOException("DeepSeek HTTP ${resp.code}: ${parseError(bodyString)}"),
                                )
                            }
                            return@use
                        }

                        if (bodyString.isBlank()) {
                            if (cont.isActive) {
                                cont.resumeWithException(IOException("DeepSeek: пустой ответ"))
                            }
                            return@use
                        }

                        try {
                            val json = JSONObject(bodyString)
                            val translatedText = json
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")
                            if (cont.isActive) {
                                cont.resume(sanitizeLlmOutput(translatedText))
                            }
                        } catch (e: Exception) {
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    IOException("DeepSeek: ошибка разбора ответа", e),
                                )
                            }
                        }
                    }
                }
            },
        )
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
                result = result.substring(prefix.length).trimStart()
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
