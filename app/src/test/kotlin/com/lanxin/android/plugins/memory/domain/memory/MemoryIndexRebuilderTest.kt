package com.lanxin.android.plugins.memory.domain.memory

import com.lanxin.android.builtin.knowledge.domain.VectorSource
import com.lanxin.android.plugins.memory.data.memory.MemoryEntity
import com.lanxin.android.plugins.memory.data.memory.MemoryType
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryIndexRebuilderTest {

    @Test
    fun `resolveSource uses AUTO_KNOWLEDGE when metadata marks auto_knowledge`() {
        val entity = MemoryEntity(
            id = 1,
            content = "用户在上海",
            type = "fact",
            metadata = """{"source":"auto_knowledge","tags":["location"]}"""
        )
        assertEquals(
            VectorSource.AUTO_KNOWLEDGE,
            VectorPipelineMemoryIndexRebuilder.resolveSource(entity)
        )
    }

    @Test
    fun `resolveSource defaults to MEMORY`() {
        val entity = MemoryEntity(
            id = 2,
            content = "喜欢草莓",
            type = MemoryType.PREFERENCE,
            metadata = null
        )
        assertEquals(
            VectorSource.MEMORY,
            VectorPipelineMemoryIndexRebuilder.resolveSource(entity)
        )
    }

    @Test
    fun `NoOp rebuilder returns zero`() = kotlinx.coroutines.runBlocking {
        assertEquals(0, NoOpMemoryIndexRebuilder.reindex(emptyList()))
        assertEquals(
            0,
            NoOpMemoryIndexRebuilder.reindex(
                listOf(MemoryEntity(id = 1, content = "x"))
            )
        )
        NoOpMemoryIndexRebuilder.clearMemorySources()
    }
}
