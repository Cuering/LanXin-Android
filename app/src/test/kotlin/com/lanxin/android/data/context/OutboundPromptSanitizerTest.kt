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

package com.lanxin.android.data.context

import com.lanxin.android.data.model.ChatAttachment
import com.lanxin.android.plugins.chat.data.entity.MessageV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboundPromptSanitizerTest {

    @Test
    fun `sanitizeText replaces known sensitive words`() {
        val input = "请评价一下习近平的政策"
        val out = OutboundPromptSanitizer.sanitizeText(input)
        assertFalse(out.contains("习近平"))
        assertTrue(out.contains("某领导"))
    }

    @Test
    fun `sanitizeText prefers longer match first`() {
        val input = "讨论六四事件的影响"
        val out = OutboundPromptSanitizer.sanitizeText(input)
        assertFalse(out.contains("六四事件"))
        assertFalse(out.contains("六四"))
        assertTrue(out.contains("某历史事件"))
    }

    @Test
    fun `sanitizeText leaves normal chat untouched`() {
        val input = "今天天气真好，一起吃饭吧"
        assertEquals(input, OutboundPromptSanitizer.sanitizeText(input))
    }

    @Test
    fun `normalizeSystemPrompt trims and collapses blank lines`() {
        val raw = "  你是助手\r\n\r\n\r\n请简短回答  \n"
        val out = OutboundPromptSanitizer.normalizeSystemPrompt(raw)
        assertEquals("你是助手\n\n请简短回答", out)
    }

    @Test
    fun `normalizeSystemPrompt returns null for blank`() {
        assertNull(OutboundPromptSanitizer.normalizeSystemPrompt("   \n  "))
        assertNull(OutboundPromptSanitizer.normalizeSystemPrompt(null))
    }

    @Test
    fun `normalizeSystemPrompt is stable across equivalent inputs`() {
        val a = OutboundPromptSanitizer.normalizeSystemPrompt("角色设定\r\n\r\n规则一")
        val b = OutboundPromptSanitizer.normalizeSystemPrompt("角色设定\n\n\n规则一\n")
        assertEquals(a, b)
    }

    @Test
    fun `sanitizeMessage only changes content for outbound`() {
        val msg = MessageV2(
            content = "关于台独的讨论",
            platformType = "p1"
        )
        val cleaned = OutboundPromptSanitizer.sanitizeMessage(msg)
        assertFalse(cleaned.content.contains("台独"))
        assertTrue(cleaned.content.contains("某政治议题"))
    }

    @Test
    fun `stabilizeAttachments sorts by path`() {
        val msg = MessageV2(
            content = "看图",
            platformType = "p1",
            attachments = listOf(
                ChatAttachment(
                    localFilePath = "/b.png",
                    preparedFilePath = "",
                    displayName = "b.png",
                    mimeType = "image/png",
                    sizeBytes = 1
                ),
                ChatAttachment(
                    localFilePath = "/a.png",
                    preparedFilePath = "",
                    displayName = "a.png",
                    mimeType = "image/png",
                    sizeBytes = 1
                )
            )
        )
        val stable = OutboundPromptSanitizer.stabilizeAttachments(msg)
        assertEquals("/a.png", stable.attachments[0].localFilePath)
        assertEquals("/b.png", stable.attachments[1].localFilePath)
    }

    @Test
    fun `prepareTurnsForOutbound sanitizes both sides`() {
        val turn = ConversationTurn(
            userMessage = MessageV2(content = "讲讲法轮功", platformType = null),
            assistantMessage = MessageV2(content = "这是关于色情的回复", platformType = "p1"),
            isCurrentTurn = true
        )
        val out = OutboundPromptSanitizer.prepareTurnsForOutbound(listOf(turn)).single()
        assertFalse(out.userMessage.content.contains("法轮功"))
        assertFalse(out.assistantMessage!!.content.contains("色情"))
    }

    @Test
    fun `extraReplacements are applied`() {
        val prev = OutboundPromptSanitizer.extraReplacements
        try {
            OutboundPromptSanitizer.extraReplacements = listOf("蓝心专属词" to "替换词")
            val out = OutboundPromptSanitizer.sanitizeText("测试蓝心专属词一下")
            assertTrue(out.contains("替换词"))
            assertFalse(out.contains("蓝心专属词"))
        } finally {
            OutboundPromptSanitizer.extraReplacements = prev
        }
    }

    @Test
    fun `findHits reports matched terms`() {
        val hits = OutboundPromptSanitizer.findHits("涉及台独与港独")
        assertTrue(hits.contains("台独"))
        assertTrue(hits.contains("港独"))
    }
}
