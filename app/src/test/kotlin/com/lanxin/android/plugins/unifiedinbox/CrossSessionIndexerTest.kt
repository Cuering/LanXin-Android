package com.lanxin.android.plugins.unifiedinbox

import com.lanxin.android.plugins.chat.data.entity.MessageV2
import com.lanxin.android.plugins.unifiedinbox.data.CrossSessionPlatform
import com.lanxin.android.plugins.unifiedinbox.data.CrossSessionRole
import com.lanxin.android.plugins.unifiedinbox.domain.CrossSessionIndexer
import com.lanxin.android.plugins.unifiedinbox.domain.CrossSessionHistoryInjector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossSessionIndexerTest {

    @Test
    fun mapMessage_userWhenPlatformNull() {
        val msg = MessageV2(
            id = 1,
            chatId = 10,
            content = "hello",
            platformType = null,
            createdAt = 1_700_000_000L
        )
        val entity = CrossSessionIndexer.mapMessage(msg, "10", "Test Room")
        assertEquals(CrossSessionRole.USER, entity.role)
        assertEquals(CrossSessionPlatform.LOCAL, entity.platform)
        assertEquals("hello", entity.content)
        assertEquals("10", entity.sessionId)
        assertEquals("Test Room", entity.sessionTitle)
        assertEquals(1_700_000_000_000L, entity.time)
        assertEquals(1, entity.sourceMessageId)
        assertEquals(10, entity.sourceChatId)
    }

    @Test
    fun mapMessage_assistantWhenPlatformPresent() {
        val msg = MessageV2(
            id = 2,
            chatId = 11,
            content = "hi there",
            platformType = "OpenAI",
            createdAt = 1_700_000_001L
        )
        val entity = CrossSessionIndexer.mapMessage(msg, "11", "AI")
        assertEquals(CrossSessionRole.ASSISTANT, entity.role)
        assertEquals("openai", entity.platform)
        assertEquals(1_700_000_001_000L, entity.time)
    }

    @Test
    fun normalizeTimeMs_keepsMilliseconds() {
        val ms = 1_700_000_000_123L
        assertEquals(ms, CrossSessionIndexer.normalizeTimeMs(ms))
    }

    @Test
    fun normalizePlatform_trimsAndLowercases() {
        assertEquals("anthropic", CrossSessionIndexer.normalizePlatform(" Anthropic "))
        assertEquals(CrossSessionPlatform.LOCAL, CrossSessionIndexer.normalizePlatform("   "))
    }

    @Test
    fun historyInjector_formatHit() {
        val entity = CrossSessionIndexer.mapMessage(
            MessageV2(
                id = 3,
                chatId = 1,
                content = "line1\nline2",
                platformType = null,
                createdAt = 100L
            ),
            "1",
            "会话A"
        )
        val line = CrossSessionHistoryInjector.formatHit(entity)
        assertTrue(line.contains("local"))
        assertTrue(line.contains("会话A"))
        assertTrue(line.contains("user"))
        assertTrue(line.contains("line1 line2"))
    }

    @Test
    fun historyInjector_extractKeyword_truncates() {
        val long = "a".repeat(30)
        assertEquals(20, CrossSessionHistoryInjector.extractKeyword(long).length)
        assertEquals("short", CrossSessionHistoryInjector.extractKeyword(" short "))
    }
}
