package com.termux.companion.data.ai

import com.google.gson.JsonParser
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AISuggestionServiceTest {

    private val service = AISuggestionService()

    @Test
    fun buildRequestBody_producesValidJsonWithHostileInput() {
        val hostilePrompt = "list files with \"quotes\" and\nnewlines\\backslash"
        val body = service.buildRequestBody(prompt = hostilePrompt, model = "gpt-3.5-turbo\"x")

        val json = JsonParser.parseString(Buffer().apply { body.writeTo(this) }.readUtf8()).asJsonObject

        assertEquals("gpt-3.5-turbo\"x", json.get("model").asString)
        val messages = json.getAsJsonArray("messages")
        assertEquals(2, messages.size())
        assertEquals(hostilePrompt, messages[1].asJsonObject.get("content").asString)
        assertEquals(200, json.get("max_tokens").asInt)
        assertEquals(0.3, json.get("temperature").asDouble, 0.0001)
    }

    @Test
    fun parseResponse_parsesStandardChatCompletion() {
        val payload = """
            {"choices":[{"message":{"content":"[{\"command\":\"ls -la\",\"description\":\"List files\"},{\"command\":\"pkg install vim\",\"description\":\"Install editor\"}]"}}]}
        """.trimIndent()

        val suggestions = service.parseResponse(payload)

        assertEquals(2, suggestions.size)
        assertEquals("ls -la", suggestions[0].text)
        assertEquals("List files", suggestions[0].description)
        assertEquals("pkg install vim", suggestions[1].text)
    }

    @Test
    fun parseResponse_extractsJsonFromProse() {
        val payload = """
            {"choices":[{"message":{"content":"Here you go:\n[{\"command\":\"df -h\",\"description\":\"Disk usage\"}]\nHope that helps."}}]}
        """.trimIndent()

        val suggestions = service.parseResponse(payload)

        assertEquals(1, suggestions.size)
        assertEquals("df -h", suggestions[0].text)
    }

    @Test
    fun parseResponse_returnsEmptyOnMalformedJson() {
        assertTrue(service.parseResponse("not json at all").isEmpty())
        assertTrue(service.parseResponse("""{"choices":[]}""").isEmpty())
        assertTrue(
            service.parseResponse(
                """{"choices":[{"message":{"content":"no arrays here"}}]}"""
            ).isEmpty()
        )
    }

    @Test
    fun extractJsonArrayFromText_findsSingleArraySurroundedByProse() {
        val text = "Here you go:\n[\"a\", \"b\"]\nHope that helps."
        val array = service.extractJsonArrayFromText(text)
        assertNotNull(array)
        assertEquals(2, array!!.size())
        assertEquals("a", array[0].asString)
        assertEquals("b", array[1].asString)
    }

    @Test
    fun extractJsonArrayFromText_returnsNullForMultipleArrays() {
        // First '[' to last ']' span is not valid JSON when prose separates two arrays.
        assertNull(service.extractJsonArrayFromText("[\"a\", \"b\"] middle [\"c\"]"))
    }

    @Test
    fun extractJsonArrayFromText_nullWhenNoArray() {
        assertNull(service.extractJsonArrayFromText("nothing here"))
        assertNull(service.extractJsonArrayFromText("[unterminated"))
    }

    @Test
    fun parseResponse_ignoresMalformedElements() {
        val payload = """
            {"choices":[{"message":{"content":"[{\"command\":\"ok\",\"description\":\"fine\"}, {\"description\":\"missing command\"}, \"just-a-string\"]}"}}]}
        """.trimIndent()
        val body = payload.toResponseBody()

        assertEquals("ok", service.parseResponse(payload).single().text)
        // Body helper above only asserts conversion works; silence unused warning.
        assertNotNull(body)
    }
}
