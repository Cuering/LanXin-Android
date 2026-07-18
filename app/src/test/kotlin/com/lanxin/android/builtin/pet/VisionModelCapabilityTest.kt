/*
 * Copyright 2025 LanXin Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lanxin.android.builtin.pet

import com.lanxin.android.builtin.pet.domain.OpenAiVisionExplainClient
import com.lanxin.android.builtin.pet.domain.VisionModelCapability
import com.lanxin.android.data.model.ClientType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 视觉能力启发式 + OpenAI vision body 构造：无 vision 不瞎编。
 */
class VisionModelCapabilityTest {

    @Test
    fun `looksLikeVisionModel accepts known multimodal ids`() {
        assertTrue(VisionModelCapability.looksLikeVisionModel("gpt-4o"))
        assertTrue(VisionModelCapability.looksLikeVisionModel("gpt-4o-mini"))
        assertTrue(VisionModelCapability.looksLikeVisionModel("gemini-2.0-flash"))
        assertTrue(VisionModelCapability.looksLikeVisionModel("qwen2.5-vl-72b"))
        assertTrue(VisionModelCapability.looksLikeVisionModel("claude-3-5-sonnet"))
        assertTrue(VisionModelCapability.looksLikeVisionModel("llava-v1.6"))
    }

    @Test
    fun `looksLikeVisionModel rejects plain text models`() {
        assertFalse(VisionModelCapability.looksLikeVisionModel(""))
        assertFalse(VisionModelCapability.looksLikeVisionModel("gpt-4"))
        assertFalse(VisionModelCapability.looksLikeVisionModel("gpt-3.5-turbo"))
        assertFalse(VisionModelCapability.looksLikeVisionModel("deepseek-chat"))
        assertFalse(VisionModelCapability.looksLikeVisionModel("llama-3.1-8b"))
    }

    @Test
    fun `supportsOpenAiVisionPath only for openai-compatible clients`() {
        assertTrue(VisionModelCapability.supportsOpenAiVisionPath(ClientType.OPENAI))
        assertTrue(VisionModelCapability.supportsOpenAiVisionPath(ClientType.OPENROUTER))
        assertTrue(VisionModelCapability.supportsOpenAiVisionPath(ClientType.CUSTOM))
        assertTrue(VisionModelCapability.supportsOpenAiVisionPath(ClientType.GROQ))
        assertTrue(VisionModelCapability.supportsOpenAiVisionPath(ClientType.OLLAMA))
        assertFalse(VisionModelCapability.supportsOpenAiVisionPath(ClientType.ANTHROPIC))
        assertFalse(VisionModelCapability.supportsOpenAiVisionPath(ClientType.GOOGLE))
        assertFalse(VisionModelCapability.supportsOpenAiVisionPath(ClientType.LANXIN))
    }

    @Test
    fun `denyVisionReason when no platform`() {
        assertEquals(
            VisionModelCapability.MSG_NO_PLATFORM,
            VisionModelCapability.denyVisionReason(
                hasEnabledPlatform = false,
                clientType = ClientType.OPENAI,
                modelId = "gpt-4o"
            )
        )
        assertEquals(
            VisionModelCapability.MSG_NO_PLATFORM,
            VisionModelCapability.denyVisionReason(
                hasEnabledPlatform = true,
                clientType = null,
                modelId = "gpt-4o"
            )
        )
    }

    @Test
    fun `denyVisionReason for unsupported provider path`() {
        assertEquals(
            VisionModelCapability.MSG_PROVIDER_PATH_UNSUPPORTED,
            VisionModelCapability.denyVisionReason(
                hasEnabledPlatform = true,
                clientType = ClientType.ANTHROPIC,
                modelId = "claude-3-5-sonnet"
            )
        )
    }

    @Test
    fun `denyVisionReason for non-vision model name`() {
        val reason = VisionModelCapability.denyVisionReason(
            hasEnabledPlatform = true,
            clientType = ClientType.OPENAI,
            modelId = "gpt-3.5-turbo"
        )
        assertEquals(VisionModelCapability.MSG_NO_VISION, reason)
        assertTrue(reason!!.contains("不会假装"))
    }

    @Test
    fun `denyVisionReason null when openai path and vision model`() {
        assertNull(
            VisionModelCapability.denyVisionReason(
                hasEnabledPlatform = true,
                clientType = ClientType.OPENAI,
                modelId = "gpt-4o-mini"
            )
        )
        assertNull(
            VisionModelCapability.denyVisionReason(
                hasEnabledPlatform = true,
                clientType = ClientType.CUSTOM,
                modelId = "qwen-vl-max"
            )
        )
    }

    @Test
    fun `buildVisionBody embeds image_url and user question`() {
        val body = OpenAiVisionExplainClient.buildVisionBody(
            modelId = "gpt-4o",
            question = "桌上有什么",
            dataUri = "data:image/jpeg;base64,ZmFrZQ=="
        )
        assertTrue("body should mention model", body.contains("gpt-4o"))
        assertTrue(body.contains("image_url"))
        assertTrue(body.contains("data:image/jpeg;base64,ZmFrZQ=="))
        assertTrue(body.contains("桌上有什么"))
        assertTrue(body.contains("\"stream\""))
        assertTrue(body.contains("false"))
        // 禁止本地颜色启发式字样
        assertFalse(body.contains("LocalSceneClassifier"))
        assertFalse(body.contains("heuristic"))
    }

    @Test
    fun `buildVisionBody blank question uses describe prompt`() {
        val body = OpenAiVisionExplainClient.buildVisionBody(
            modelId = "gpt-4o",
            question = "  ",
            dataUri = "data:image/jpeg;base64,xx"
        )
        assertTrue(body.contains("描述你看到的画面") || body.contains("简要描述"))
    }

    @Test
    fun `extractAssistantContent from standard openai payload`() {
        val json = """
            {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "画面里是一只猫。"
                  }
                }
              ]
            }
        """.trimIndent()
        assertEquals("画面里是一只猫。", OpenAiVisionExplainClient.extractAssistantContent(json))
    }

    @Test
    fun `extractAssistantContent from content array`() {
        val json = """
            {
              "choices": [
                {
                  "message": {
                    "content": [
                      {"type": "text", "text": "左边"},
                      {"type": "text", "text": "有书"}
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        assertEquals("左边有书", OpenAiVisionExplainClient.extractAssistantContent(json))
    }

    @Test
    fun `extractAssistantContent returns null on garbage`() {
        assertNull(OpenAiVisionExplainClient.extractAssistantContent("not-json"))
        assertNull(OpenAiVisionExplainClient.extractAssistantContent("""{"choices":[]}"""))
        assertNotNull(VisionModelCapability.MSG_CAPTURE_FAILED)
    }
}
