package com.lanxin.android.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiModelProbeClientTest {

    @Test
    fun `buildProbeBody uses neutral prompt and non-stream`() {
        val body = OpenAiModelProbeClient.buildProbeBody("gpt-test")
        assertTrue(body.contains("\"stream\":false") || body.contains("\"stream\": false"))
        assertTrue(body.contains(OpenAiModelProbeSupport.PROBE_EXPECTED_TOKEN))
        assertTrue(body.contains(OpenAiModelProbeSupport.PROBE_USER_PROMPT))
        assertTrue(body.contains("gpt-test"))
        // Hard ban greetings in wire body
        assertFalse(body.contains("hello", ignoreCase = true) && !body.contains("ping-lx-1"))
        OpenAiModelProbeSupport.FORBIDDEN_GREETING_SUBSTRINGS
            .filter { it != "hi" } // "which" not present; hi checked via word boundary below
            .forEach { g ->
                if (g == "你好") {
                    assertFalse(body.contains(g))
                }
            }
        assertFalse(Regex("""\bhello\b""", RegexOption.IGNORE_CASE).containsMatchIn(body))
        assertFalse(Regex("""\bhi\b""", RegexOption.IGNORE_CASE).containsMatchIn(body))
        assertFalse(Regex("""\bhey\b""", RegexOption.IGNORE_CASE).containsMatchIn(body))
    }

    @Test
    fun `extractAssistantContent from standard message content`() {
        val body = """
            {
              "id": "chatcmpl-1",
              "choices": [
                {
                  "index": 0,
                  "message": {"role": "assistant", "content": "ping-lx-1"},
                  "finish_reason": "stop"
                }
              ]
            }
        """.trimIndent()
        assertEquals("ping-lx-1", OpenAiModelProbeClient.extractAssistantContent(body))
    }

    @Test
    fun `extractAssistantContent from multipart content array`() {
        val body = """
            {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": [{"type": "text", "text": "ping-lx-1"}]
                  }
                }
              ]
            }
        """.trimIndent()
        assertEquals("ping-lx-1", OpenAiModelProbeClient.extractAssistantContent(body))
    }

    @Test
    fun `extractAssistantContent empty or garbage`() {
        assertNull(OpenAiModelProbeClient.extractAssistantContent(""))
        assertNull(OpenAiModelProbeClient.extractAssistantContent("not-json"))
        assertNull(OpenAiModelProbeClient.extractAssistantContent("""{"choices":[]}"""))
    }

    @Test
    fun `extract content then expected token check end-to-end`() {
        val body = """{"choices":[{"message":{"content":"  ping-lx-1  "}}]}"""
        val content = OpenAiModelProbeClient.extractAssistantContent(body)
        assertNotNull(content)
        assertTrue(OpenAiModelProbeSupport.responseContainsExpectedToken(content))
    }
}
