package com.lanxin.android.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiModelProbeSupportTest {

    @Test
    fun `probe prompt is neutral and contains expected token`() {
        assertTrue(OpenAiModelProbeSupport.PROBE_USER_PROMPT.contains(OpenAiModelProbeSupport.PROBE_EXPECTED_TOKEN))
        assertEquals("ping-lx-1", OpenAiModelProbeSupport.PROBE_EXPECTED_TOKEN)
    }

    @Test
    fun `probe prompt must not contain greetings`() {
        val promptLower = OpenAiModelProbeSupport.PROBE_USER_PROMPT.lowercase()
        OpenAiModelProbeSupport.FORBIDDEN_GREETING_SUBSTRINGS.forEach { forbidden ->
            // Word-boundary-ish: reject standalone greeting tokens, not accidental substrings in "which"
            when (forbidden.lowercase()) {
                "hi" -> {
                    // Explicit hard ban list from product constraint — ensure no bare hi/hello/hey
                    assertFalse(
                        "prompt must not contain greeting '$forbidden'",
                        Regex("""\bhi\b""", RegexOption.IGNORE_CASE)
                            .containsMatchIn(OpenAiModelProbeSupport.PROBE_USER_PROMPT)
                    )
                }
                "hey" -> assertFalse(
                    Regex("""\bhey\b""", RegexOption.IGNORE_CASE)
                        .containsMatchIn(OpenAiModelProbeSupport.PROBE_USER_PROMPT)
                )
                "hello" -> assertFalse(promptLower.contains("hello"))
                "你好" -> assertFalse(OpenAiModelProbeSupport.PROBE_USER_PROMPT.contains("你好"))
                else -> assertFalse(
                    "prompt must not contain greeting '$forbidden'",
                    promptLower.contains(forbidden.lowercase())
                )
            }
        }
    }

    @Test
    fun `chat completions endpoint normalizes slash`() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            OpenAiModelProbeSupport.chatCompletionsEndpoint("https://api.example.com/v1/")
        )
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            OpenAiModelProbeSupport.chatCompletionsEndpoint("https://api.example.com/v1")
        )
        assertEquals("", OpenAiModelProbeSupport.chatCompletionsEndpoint("  "))
    }

    @Test
    fun `filterModelIds is case insensitive`() {
        val models = listOf("gpt-4o", "llama3.2", "claude-3")
        assertEquals(listOf("gpt-4o"), OpenAiModelProbeSupport.filterModelIds(models, "GPT"))
        assertEquals(models, OpenAiModelProbeSupport.filterModelIds(models, "  "))
        assertEquals(emptyList<String>(), OpenAiModelProbeSupport.filterModelIds(models, "zzz"))
    }

    @Test
    fun `resolveProbeTargets prefers current then checked and caps`() {
        val available = listOf("a", "b", "c", "d")
        val targets = OpenAiModelProbeSupport.resolveProbeTargets(
            checkedModelIds = listOf("b", "c", "d"),
            currentModel = "a",
            availableModels = available
        )
        assertEquals(OpenAiModelProbeSupport.MAX_PROBE_MODELS, targets.size)
        assertEquals("a", targets.first())
        assertTrue(targets.contains("b"))
    }

    @Test
    fun `resolveProbeTargets falls back to current model alone`() {
        val targets = OpenAiModelProbeSupport.resolveProbeTargets(
            checkedModelIds = emptyList(),
            currentModel = "solo-model",
            availableModels = emptyList()
        )
        assertEquals(listOf("solo-model"), targets)
    }

    @Test
    fun `responseContainsExpectedToken`() {
        assertTrue(OpenAiModelProbeSupport.responseContainsExpectedToken("ping-lx-1"))
        assertTrue(OpenAiModelProbeSupport.responseContainsExpectedToken("prefix ping-lx-1 suffix"))
        assertFalse(OpenAiModelProbeSupport.responseContainsExpectedToken("ping-lx-2"))
        assertFalse(OpenAiModelProbeSupport.responseContainsExpectedToken(null))
        assertFalse(OpenAiModelProbeSupport.responseContainsExpectedToken("  "))
    }

    @Test
    fun `humanizeListError maps common codes`() {
        assertEquals("http_401", OpenAiModelProbeSupport.humanizeListError("http_401:bad key"))
        assertEquals("empty_api_url", OpenAiModelProbeSupport.humanizeListError("empty_api_url"))
        assertEquals("network_error", OpenAiModelProbeSupport.humanizeListError("connection refused"))
        assertEquals("unsupported_type", OpenAiModelProbeSupport.humanizeListError("unsupported_type"))
    }

    @Test
    fun `limits are conservative`() {
        assertTrue(OpenAiModelProbeSupport.MAX_PROBE_MODELS in 1..5)
        assertTrue(OpenAiModelProbeSupport.MAX_PROBE_CONCURRENCY in 1..OpenAiModelProbeSupport.MAX_PROBE_MODELS)
        assertTrue(OpenAiModelProbeSupport.MAX_BULK_LATENCY_PROBE_MODELS >= OpenAiModelProbeSupport.MAX_PROBE_MODELS)
        assertTrue(OpenAiModelProbeSupport.MAX_BULK_PROBE_CONCURRENCY >= OpenAiModelProbeSupport.MAX_PROBE_CONCURRENCY)
    }

    @Test
    fun `resolveBulkLatencyTargets prefers current and caps`() {
        val ids = (1..30).map { "m$it" }
        val targets = OpenAiModelProbeSupport.resolveBulkLatencyTargets(
            modelIds = ids,
            preferredModel = "m20",
            max = 5
        )
        assertEquals(5, targets.size)
        assertEquals("m20", targets.first())
        assertEquals(listOf("m20", "m1", "m2", "m3", "m4"), targets)
    }

    @Test
    fun `sortModelIdsByLatency ranks success fast to slow then failures then unprobed`() {
        val ids = listOf("slow-ok", "fast-ok", "fail-a", "unprobed", "fail-b")
        val results = listOf(
            OpenAiModelProbeResult("slow-ok", success = true, latencyMs = 300, detail = "ok"),
            OpenAiModelProbeResult("fast-ok", success = true, latencyMs = 50, detail = "ok"),
            OpenAiModelProbeResult("fail-a", success = false, latencyMs = 80, detail = "x"),
            OpenAiModelProbeResult("fail-b", success = false, latencyMs = 200, detail = "x")
        )
        val ranked = OpenAiModelProbeSupport.sortModelIdsByLatency(ids, results)
        assertEquals(
            listOf("fast-ok", "slow-ok", "fail-a", "fail-b", "unprobed"),
            ranked
        )
    }

    @Test
    fun `sortProbeResultsByLatency success first then latency`() {
        val results = listOf(
            OpenAiModelProbeResult("b", success = false, latencyMs = 10, detail = "x"),
            OpenAiModelProbeResult("a", success = true, latencyMs = 100, detail = "ok"),
            OpenAiModelProbeResult("c", success = true, latencyMs = 20, detail = "ok")
        )
        val sorted = OpenAiModelProbeSupport.sortProbeResultsByLatency(results)
        assertEquals(listOf("c", "a", "b"), sorted.map { it.modelId })
    }
}
