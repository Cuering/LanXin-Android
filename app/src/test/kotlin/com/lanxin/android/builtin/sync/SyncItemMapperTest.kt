package com.lanxin.android.builtin.sync

import com.lanxin.android.builtin.sync.domain.SyncItem
import com.lanxin.android.builtin.sync.domain.SyncItemMapper
import com.lanxin.android.builtin.sync.domain.SyncItemType
import com.lanxin.android.builtin.sync.domain.SyncSource
import com.lanxin.android.plugins.memory.data.memory.MemoryEntity
import com.lanxin.android.plugins.memory.data.memory.MemoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncItemMapperTest {

    @Test
    fun `memory sync id round trip`() {
        assertEquals("memory:12", SyncItemMapper.memorySyncId(12L))
        assertEquals(12L, SyncItemMapper.parseMemoryLocalId("memory:12"))
        assertNull(SyncItemMapper.parseMemoryLocalId("knowledge:12"))
    }

    @Test
    fun `fromMemory maps fields`() {
        val entity = MemoryEntity(
            id = 7L,
            content = "事实A",
            type = MemoryType.FACTUAL,
            importance = 6f,
            createdAt = 1000L,
            lastAccessedAt = 2000L,
            metadata = """{"source":"manual"}"""
        )
        val item = SyncItemMapper.fromMemory(entity, deviceId = "dev")
        assertEquals("memory:7", item.id)
        assertEquals(SyncItemType.MEMORY, item.type)
        assertEquals("事实A", item.content)
        assertEquals(2000L, item.updatedAt)
        assertEquals(MemoryType.FACTUAL, item.subtype)
        assertEquals(6f, item.importance)
        assertEquals("dev", item.deviceId)
        assertEquals(SyncSource.ANDROID, item.source)
    }

    @Test
    fun `fromMemory deleted clears content flag`() {
        val entity = MemoryEntity(id = 1L, content = "x", createdAt = 1L)
        val item = SyncItemMapper.fromMemory(entity, deviceId = null, deleted = true)
        assertTrue(item.deleted)
        assertEquals("", item.content)
    }

    @Test
    fun `toMemoryDraft parses remote`() {
        val item = SyncItem(
            id = "memory:3",
            type = SyncItemType.MEMORY,
            content = "c",
            updatedAt = 50L,
            subtype = "preference",
            importance = 9f,
            createdAt = 10L
        )
        val draft = SyncItemMapper.toMemoryDraft(item)
        assertEquals(3L, draft.localId)
        assertEquals(MemoryType.PREFERENCE, draft.type)
        assertEquals(9f, draft.importance)
    }

    @Test
    fun `knowledge placeholder id`() {
        assertEquals("knowledge:doc1", SyncItemMapper.knowledgePlaceholderId("doc1"))
    }
}
