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
 * 无 vision 时不瞎编 + 模型启发式。
 */
class VisionModelCapabilityTest {

    @Test
    fun `default deny without platform`() {
        val reason = VisionModelCapability.denyVisionReason(
            hasEnabledPlatform = false,
            clientType = null,
            modelId = null
        )
        assertEquals(VisionModelCapability.MSG_NO_PLATFORM, reason)
    }

    @Test
    fun `text-only model is denied with no-vision message`() {
        val reason = VisionModelCapability.denyVisionReason(
            hasEnabledPlatform = true,
            clientType = ClientType.OPENAI,
            modelId = "gpt-3.5-turbo"
        )
        assertEquals(VisionModelCapability.MSG_NO_VISION, reason)
        assertFalse(VisionModelCapability.looksLikeVisionModel("gpt-3.5-turbo"))
        assertFalse(VisionModelCapability.looksLikeVisionModel("gpt-4"))
        assertFalse(VisionModelCapability.looksLikeVisionModel("deepseek-chat"))
    }

    @Test
    fun `known vision models allowed on OpenAI path`() {
        assertTrue(VisionModelCapability.looksLikeVisionModel("gpt-4o"))
        assertTrue(VisionModelCapability.looksLikeVisionModel("gpt-4o-mini"))
        assertTrue(VisionModelCapability.looksLikeVisionModel("gpt-4-turbo"))
        assertTrue(VisionModelCapability.looksLikeVisionModel("qwen2.5-vl-7b"))
        assertTrue(VisionModelCapability.looksLikeVisionModel("gemini-2.0-flash"))
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
                clientType = ClientType.OPENROUTER,
                modelId = "google/gemini-2.0-flash"
            )
        )
    }

    @Test
    fun `anthropic path denied in V1 even if model name looks vision`() {
        val reason = VisionModelCapability.denyVisionReason(
            hasEnabledPlatform = true,
            clientType = ClientType.ANTHROPIC,
            modelId = "claude-3-5-sonnet"
        )
        assertEquals(VisionModelCapability.MSG_PROVIDER_PATH_UNSUPPORTED, reason)
    }

    @Test
    fun `no vision message must not claim local VLM`() {
        val msg = VisionModelCapability.MSG_NO_VISION
        assertTrue(msg.contains("看不了") || msg.contains("视觉"))
        assertFalse(msg.contains("本地已识别"))
        assertFalse(msg.contains("我看到了"))
        assertTrue(msg.contains("不会假装") || msg.contains("不会假装本地"))
    }

    @Test
    fun `vision request body includes image_url and question`() {
        val body = OpenAiVisionExplainClient.buildVisionBody(
            modelId = "gpt-4o",
            question = "桌上是什么",
            dataUri = "data:image/jpeg;base64,abc"
        )
        assertTrue(body.contains("\"image_url\""))
        assertTrue(body.contains("data:image/jpeg;base64,abc"))
        assertTrue(body.contains("桌上是什么"))
        assertTrue(body.contains("gpt-4o"))
        assertTrue(body.contains("\"stream\":false") || body.contains("\"stream\": false"))
    }

    @Test
    fun `extractAssistantContent reads simple message`() {
        val json = """
            {"choices":[{"message":{"role":"assistant","content":"画面里是一杯茶"}}]}
        """.trimIndent()
        assertEquals(
            "画面里是一杯茶",
            OpenAiVisionExplainClient.extractAssistantContent(json)
        )
    }

    @Test
    fun `extractAssistantContent blank returns null`() {
        assertNull(OpenAiVisionExplainClient.extractAssistantContent("{}"))
        assertNull(
            OpenAiVisionExplainClient.extractAssistantContent(
                """{"choices":[{"message":{"content":""}}]}"""
            )
        )
    }

    @Test
    fun `capability messages are non-blank`() {
        assertNotNull(VisionModelCapability.MSG_NO_VISION)
        assertTrue(VisionModelCapability.MSG_NO_VISION.isNotBlank())
        assertTrue(VisionModelCapability.MSG_CAPTURE_FAILED.isNotBlank())
    }
}
