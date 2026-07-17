package com.lanxin.android.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiModelListClientTest {

    @Test
    fun `parse OpenAI envelope sorts and dedupes ids`() {
        val body = """
            {
              "object": "list",
              "data": [
                {"id": "gpt-4o", "object": "model", "owned_by": "openai"},
                {"id": "gpt-4o-mini", "object": "model"},
                {"id": "GPT-4o", "object": "model"},
                {"id": "claude-3-haiku"}
              ]
            }
        """.trimIndent()

        val result = OpenAiModelListClient.parseModelsBody(body)
        assertTrue(result is OpenAiModelListResult.Success)
        val ids = (result as OpenAiModelListResult.Success).modelIds
        assertEquals(listOf("claude-3-haiku", "gpt-4o", "gpt-4o-mini"), ids)
    }

    @Test
    fun `parse bare string array`() {
        val body = """["llama3.2", "llava", "llama3.2"]"""
        val result = OpenAiModelListClient.parseModelsBody(body)
        assertTrue(result is OpenAiModelListResult.Success)
        assertEquals(
            listOf("llama3.2", "llava"),
            (result as OpenAiModelListResult.Success).modelIds
        )
    }

    @Test
    fun `empty body is error`() {
        val result = OpenAiModelListClient.parseModelsBody("  ")
        assertTrue(result is OpenAiModelListResult.Error)
        assertEquals("empty_body", (result as OpenAiModelListResult.Error).message)
    }

    @Test
    fun `empty data array is no_models`() {
        val result = OpenAiModelListClient.parseModelsBody("""{"data":[]}""")
        assertTrue(result is OpenAiModelListResult.Error)
        assertEquals("no_models", (result as OpenAiModelListResult.Error).message)
    }

    @Test
    fun `garbage body is parse_error`() {
        val result = OpenAiModelListClient.parseModelsBody("not-json")
        assertTrue(result is OpenAiModelListResult.Error)
    }

    @Test
    fun `blank ids filtered`() {
        val body = """{"data":[{"id":"  "},{"id":"ok"},{"id":""}]}"""
        val result = OpenAiModelListClient.parseModelsBody(body)
        assertTrue(result is OpenAiModelListResult.Success)
        assertEquals(listOf("ok"), (result as OpenAiModelListResult.Success).modelIds)
    }
}
