package com.termux.companion.data.ai

import com.termux.companion.domain.model.AutocompleteSuggestion
import com.termux.companion.domain.model.SuggestionIcon
import com.termux.companion.domain.model.SuggestionSource
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AISuggestionService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun getSuggestions(
        partial: String,
        apiEndpoint: String,
        apiKey: String,
        model: String
    ): List<AutocompleteSuggestion> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || partial.length < 3) return@withContext emptyList()

        try {
            val prompt = buildPrompt(partial)
            val requestBody = buildRequestBody(prompt, model)

            val request = Request.Builder()
                .url(apiEndpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val responseBody = response.body?.string() ?: return@withContext emptyList()
            parseResponse(responseBody)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildPrompt(partial: String): String {
        return """Suggest 5 command completions for a Termux terminal user typing: "$partial"
Return ONLY a JSON array of objects with "command" and "description" fields.
Example: [{"command": "ls -la", "description": "List all files with details"}]
Keep suggestions practical and relevant to Linux/Termux commands."""
    }

    private fun buildRequestBody(prompt: String, model: String): okhttp3.RequestBody {
        val json = """
        {
            "model": "$model",
            "messages": [
                {"role": "system", "content": "You are a helpful terminal assistant. Provide concise command suggestions."},
                {"role": "user", "content": "$prompt"}
            ],
            "max_tokens": 200,
            "temperature": 0.3
        }
        """.trimIndent()
        return json.toRequestBody("application/json".toMediaType())
    }

    private fun parseResponse(responseBody: String): List<AutocompleteSuggestion> {
        return try {
            val json = JsonParser.parseString(responseBody).asJsonObject
            val choices = json.getAsJsonArray("choices")
            if (choices == null || choices.size() == 0) return emptyList()

            val message = choices[0].asJsonObject.getAsJsonObject("message")
            val content = message.get("content")?.asString ?: return emptyList()

            val jsonArray = try {
                JsonParser.parseString(content).asJsonArray
            } catch (e: Exception) {
                extractJsonArrayFromText(content)
            } ?: return emptyList()

            jsonArray.mapNotNull { element ->
                try {
                    val obj = element.asJsonObject
                    AutocompleteSuggestion(
                        text = obj.get("command")?.asString ?: return@mapNotNull null,
                        description = obj.get("description")?.asString ?: "",
                        source = SuggestionSource.AI,
                        icon = SuggestionIcon.AI
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractJsonArrayFromText(text: String): JsonArray? {
        val start = text.indexOf("[")
        val end = text.lastIndexOf("]")
        if (start == -1 || end == -1 || start >= end) return null
        return try {
            JsonParser.parseString(text.substring(start, end + 1)).asJsonArray
        } catch (e: Exception) {
            null
        }
    }
}
