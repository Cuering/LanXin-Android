package com.lanxin.android.presentation.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGenerationStatusLogicTest {

    @Test
    fun `label covers all active phases`() {
        assertEquals("准备中…", ChatGenerationStatusLogic.label(ChatGenerationPhase.PREPARING))
        assertEquals("检索记忆…", ChatGenerationStatusLogic.label(ChatGenerationPhase.SEARCHING_MEMORY))
        assertEquals("检索知识库…", ChatGenerationStatusLogic.label(ChatGenerationPhase.SEARCHING_KNOWLEDGE))
        assertEquals("生成中…", ChatGenerationStatusLogic.label(ChatGenerationPhase.GENERATING))
        assertEquals("本地离线生成中…", ChatGenerationStatusLogic.label(ChatGenerationPhase.GENERATING_LOCAL))
        assertEquals("调用工具…", ChatGenerationStatusLogic.label(ChatGenerationPhase.CALLING_TOOLS))
        assertEquals("已完成", ChatGenerationStatusLogic.label(ChatGenerationPhase.DONE))
        assertEquals("", ChatGenerationStatusLogic.label(ChatGenerationPhase.IDLE))
    }

    @Test
    fun `isActive true only for in-flight phases`() {
        assertTrue(ChatGenerationPhase.PREPARING.isActive)
        assertTrue(ChatGenerationPhase.SEARCHING_MEMORY.isActive)
        assertTrue(ChatGenerationPhase.GENERATING.isActive)
        assertTrue(ChatGenerationPhase.GENERATING_LOCAL.isActive)
        assertFalse(ChatGenerationPhase.IDLE.isActive)
        assertFalse(ChatGenerationPhase.DONE.isActive)
    }

    @Test
    fun `onStreamContentStarted collapses active phase to Done`() {
        assertEquals(
            ChatGenerationPhase.DONE,
            ChatGenerationStatusLogic.onStreamContentStarted(ChatGenerationPhase.GENERATING)
        )
        assertEquals(
            ChatGenerationPhase.DONE,
            ChatGenerationStatusLogic.onStreamContentStarted(ChatGenerationPhase.SEARCHING_MEMORY)
        )
        assertEquals(
            ChatGenerationPhase.IDLE,
            ChatGenerationStatusLogic.onStreamContentStarted(ChatGenerationPhase.IDLE)
        )
        assertEquals(
            ChatGenerationPhase.DONE,
            ChatGenerationStatusLogic.onStreamContentStarted(ChatGenerationPhase.DONE)
        )
    }

    @Test
    fun `onGenerationFinished maps activity to Done`() {
        assertEquals(
            ChatGenerationPhase.DONE,
            ChatGenerationStatusLogic.onGenerationFinished(ChatGenerationPhase.CALLING_TOOLS)
        )
        assertEquals(
            ChatGenerationPhase.IDLE,
            ChatGenerationStatusLogic.onGenerationFinished(ChatGenerationPhase.IDLE)
        )
    }

    @Test
    fun `refsFromUnifiedKeys parses memory and knowledge only`() {
        val refs = ChatGenerationStatusLogic.refsFromUnifiedKeys(
            keys = listOf(
                "memory:42",
                "knowledge:7:99",
                "chat:1:2",
                "memory:",
                "knowledge:abc:1"
            ),
            texts = listOf(
                "喜欢猫",
                "向量片段",
                "旧聊天",
                "空id",
                "知识B"
            ),
            subtitles = listOf(
                "偏好",
                "score=0.9",
                "昨天",
                "",
                ""
            )
        )
        assertEquals(3, refs.size)
        assertEquals(ChatRefType.MEMORY, refs[0].type)
        assertEquals("42", refs[0].id)
        assertEquals("偏好", refs[0].title)
        assertEquals(ChatRefType.KNOWLEDGE, refs[1].type)
        assertEquals("7", refs[1].id)
        assertEquals(ChatRefType.KNOWLEDGE, refs[2].type)
        assertEquals("abc", refs[2].id)
    }

    @Test
    fun `memoryRefs distinct by id`() {
        val refs = ChatGenerationStatusLogic.memoryRefs(
            ids = listOf(1L, 1L, 2L),
            titles = listOf("事实", "事实", "日常"),
            snippets = listOf("a", "a2", "b")
        )
        assertEquals(2, refs.size)
        assertEquals("1", refs[0].id)
        assertEquals("2", refs[1].id)
    }
}
