package com.kupol.keyboard.translation

import com.kupol.keyboard.ImeSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Google Cloud Translation API v2.
 * Принципиально отличается от LLM-провайдеров: нет промпта, нет санитайзера —
 * это классический translation API, возвращающий чистый результат.
 *
 * Требует Google Cloud API-ключ с включённым Cloud Translation API.
 */
class GoogleProvider : TranslationProvider {

    override val id: String = "google"
    override val displayName: String = "Google Translate"

    private val apiUrl = "https://translation.googleapis.com/language/translate/v2"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun translate(
        text: String,
        targetLanguage: ImeSessionState.TargetLanguage,
        apiKey: String,
    ): String = withContext(Dispatchers.IO) {

        if (apiKey.isBlank()) {
            throw IllegalStateException("Google API-ключ не задан")
        }

        val targetCode = targetLanguage.toIsoCode()
        val url = "$apiUrl?key=$apiKey"

        val formBody = FormBody.Builder()
            .add("q", text)
            .add("target", targetCode)
            .add("format", "text")
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                throw IOException("Google HTTP ${response.code}: ${parseError(bodyString)}")
            }

            if (bodyString.isBlank()) {
                throw IOException("Google: пустой ответ")
            }

            val json = JSONObject(bodyString)
            val data = json.optJSONObject("data")
                ?: throw IOException("Google: нет поля data")
            val translations = data.optJSONArray("translations")
                ?: throw IOException("Google: нет поля translations")
            if (translations.length() == 0) {
                throw IOException("Google: translations пустой")
            }

            val translatedText = translations
                .getJSONObject(0)
                .optString("translatedText")
                .trim()

            if (translatedText.isEmpty()) {
                throw IOException("Google: пустой translatedText")
            }

            translatedText
        }
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
