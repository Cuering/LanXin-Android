package com.lanxin.android.plugins.memory.data.memory

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryExportModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Test
    fun `serialize and deserialize export payload`() {
        val payload = MemoryExportPayload(
            version = 1,
            exportedAt = 1_700_000_000_000L,
            memories = listOf(
                MemoryExportItem(
                    id = 1,
                    content = "喜欢吃草莓",
                    type = "preference",
                    importance = 3.0f,
                    createdAt = 1_700_000_000_000L,
                    updatedAt = 1_700_000_000_000L
                )
            )
        )

        val encoded = json.encodeToString(MemoryExportPayload.serializer(), payload)
        assertTrue(encoded.contains("\"version\":1"))
        assertTrue(encoded.contains("喜欢吃草莓"))

        val decoded = json.decodeFromString(MemoryExportPayload.serializer(), encoded)
        assertEquals(1, decoded.version)
        assertEquals(1, decoded.memories.size)
        assertEquals("喜欢吃草莓", decoded.memories[0].content)
        assertEquals("preference", decoded.memories[0].type)
        assertEquals(3.0f, decoded.memories[0].importance, 0.001f)
    }

    @Test
    fun `import result message`() {
        val withSkip = MemoryImportResult(imported = 2, skipped = 1, total = 3)
        assertEquals("导入完成：新增 2 条，跳过 1 条", withSkip.message)

        val noSkip = MemoryImportResult(imported = 5, skipped = 0, total = 5)
        assertEquals("导入完成：共 5 条", noSkip.message)

        val reindexed = MemoryImportResult(imported = 3, skipped = 1, total = 4, reindexed = 3)
        assertEquals("导入完成：新增 3 条，跳过 1 条（已重建索引 3 条）", reindexed.message)
    }

    @Test
    fun `decode minimal json without optional fields`() {
        val raw = """
            {
              "version": 1,
              "exportedAt": 123,
              "memories": [
                { "content": "hello", "type": "chat" }
              ]
            }
        """.trimIndent()

        val decoded = json.decodeFromString(MemoryExportPayload.serializer(), raw)
        assertEquals(1, decoded.memories.size)
        assertEquals("hello", decoded.memories[0].content)
        assertEquals(0L, decoded.memories[0].id)
    }

    @Test
    fun `export format enum values`() {
        assertEquals(2, MemoryExportFormat.entries.size)
        assertEquals(MemoryExportFormat.JSON, MemoryExportFormat.valueOf("JSON"))
        assertEquals(MemoryExportFormat.MARKDOWN, MemoryExportFormat.valueOf("MARKDOWN"))
    }
}
