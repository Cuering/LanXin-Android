package com.lanxin.android.plugins.memory.data.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryMarkdownExporterTest {

    private val sampleItems = listOf(
        MemoryExportItem(
            id = 1,
            content = "永久偏好：喜欢草莓",
            type = MemoryType.PREFERENCE,
            importance = 8.0f,
            createdAt = 1_700_000_000_000L,
            lifecycle = "permanent",
            metadata = """{"tags":["food","fruit"]}"""
        ),
        MemoryExportItem(
            id = 2,
            content = "普通对话记忆",
            type = MemoryType.CHAT,
            importance = 4.0f,
            createdAt = 1_700_000_100_000L,
            lifecycle = "normal",
            metadata = null
        ),
        MemoryExportItem(
            id = 3,
            content = "自动抽取：用户在上海",
            type = "fact",
            importance = 6.5f,
            createdAt = 1_700_000_200_000L,
            lifecycle = "permanent",
            metadata = """{"source":"auto_knowledge","tags":["location","city"],"knowledgeType":"fact"}"""
        ),
        MemoryExportItem(
            id = 4,
            content = "与同事的关系备注",
            type = "relationship",
            importance = 7.0f,
            createdAt = 1_700_000_300_000L,
            lifecycle = "permanent",
            metadata = """{"tags":["work"]}"""
        )
    )

    @Test
    fun `markdown groups by permanent memory auto_knowledge relationship`() {
        val md = MemoryMarkdownExporter.build(
            memories = sampleItems,
            exportedAt = 1_700_100_000_000L,
            typeFilter = null
        )

        assertTrue(md.contains("# LanXin Memory Export"))
        assertTrue(md.contains("- total: 4"))
        assertTrue(md.contains("- filter: all"))
        assertTrue(md.contains("## permanent"))
        assertTrue(md.contains("## memory"))
        assertTrue(md.contains("## auto_knowledge"))
        assertTrue(md.contains("## relationship"))

        // 内容加粗
        assertTrue(md.contains("**永久偏好：喜欢草莓**"))
        assertTrue(md.contains("**自动抽取：用户在上海**"))
        assertTrue(md.contains("**与同事的关系备注**"))

        // tags / score
        assertTrue(md.contains("tags: food, fruit"))
        assertTrue(md.contains("tags: location, city"))
        assertTrue(md.contains("score: 8.0"))
        assertTrue(md.contains("score: 6.5"))
    }

    @Test
    fun `filter by export group auto_knowledge`() {
        val md = MemoryMarkdownExporter.build(
            memories = sampleItems,
            exportedAt = 1_700_100_000_000L,
            typeFilter = "auto_knowledge"
        )

        assertTrue(md.contains("- total: 1"))
        assertTrue(md.contains("- filter: auto_knowledge"))
        assertTrue(md.contains("## auto_knowledge"))
        assertTrue(md.contains("**自动抽取：用户在上海**"))
        assertFalse(md.contains("**永久偏好：喜欢草莓**"))
        assertFalse(md.contains("**普通对话记忆**"))
    }

    @Test
    fun `filter by raw type preference`() {
        val md = MemoryMarkdownExporter.build(
            memories = sampleItems,
            exportedAt = 1_700_100_000_000L,
            typeFilter = MemoryType.PREFERENCE
        )

        assertTrue(md.contains("- total: 1"))
        assertTrue(md.contains("**永久偏好：喜欢草莓**"))
        assertFalse(md.contains("**普通对话记忆**"))
        assertFalse(md.contains("**自动抽取：用户在上海**"))
    }

    @Test
    fun `filter by relationship type`() {
        val md = MemoryMarkdownExporter.build(
            memories = sampleItems,
            exportedAt = 1_700_100_000_000L,
            typeFilter = "relationship"
        )

        assertTrue(md.contains("- total: 1"))
        assertTrue(md.contains("## relationship"))
        assertTrue(md.contains("**与同事的关系备注**"))
        assertFalse(md.contains("**普通对话记忆**"))
    }

    @Test
    fun `filter by lifecycle permanent excludes normal chat`() {
        // permanent 既匹配 lifecycle，也匹配分组名；chat(normal) 不应命中
        val md = MemoryMarkdownExporter.build(
            memories = sampleItems,
            exportedAt = 1_700_100_000_000L,
            typeFilter = "permanent"
        )

        // sample 中 lifecycle=permanent 的有 id1,3,4；但 resolveExportGroup 把 3→auto_knowledge、4→relationship、1→permanent
        // matchesTypeFilter：lifecycle.equals("permanent") 也会命中 1,3,4
        assertTrue(md.contains("**永久偏好：喜欢草莓**"))
        assertTrue(md.contains("**自动抽取：用户在上海**"))
        assertTrue(md.contains("**与同事的关系备注**"))
        assertFalse(md.contains("**普通对话记忆**"))
        assertTrue(md.contains("- total: 3"))
    }

    @Test
    fun `empty export still has header`() {
        val md = MemoryMarkdownExporter.build(
            memories = emptyList(),
            exportedAt = 1_700_100_000_000L
        )
        assertTrue(md.contains("# LanXin Memory Export"))
        assertTrue(md.contains("- total: 0"))
        assertTrue(md.contains("无记忆条目"))
    }

    @Test
    fun `matchesTypeFilter accepts group and type keys`() {
        val auto = sampleItems[2]
        assertTrue(MemoryMarkdownExporter.matchesTypeFilter(auto, "auto_knowledge"))
        assertTrue(MemoryMarkdownExporter.matchesTypeFilter(auto, "fact"))
        assertFalse(MemoryMarkdownExporter.matchesTypeFilter(auto, "chat"))

        val chat = sampleItems[1]
        assertTrue(MemoryMarkdownExporter.matchesTypeFilter(chat, "memory"))
        assertTrue(MemoryMarkdownExporter.matchesTypeFilter(chat, MemoryType.CHAT))
        assertFalse(MemoryMarkdownExporter.matchesTypeFilter(chat, "permanent"))
    }

    @Test
    fun `resolveExportGroup priority auto_knowledge over permanent`() {
        val item = sampleItems[2]
        assertEquals("auto_knowledge", MemoryMarkdownExporter.resolveExportGroup(item))
    }

    @Test
    fun `extractTags from json array and missing`() {
        assertEquals(
            listOf("food", "fruit"),
            MemoryMarkdownExporter.extractTags("""{"tags":["food","fruit"]}""")
        )
        assertEquals(emptyList<String>(), MemoryMarkdownExporter.extractTags(null))
        assertEquals(emptyList<String>(), MemoryMarkdownExporter.extractTags("not-json"))
        assertEquals(
            listOf("a", "b"),
            MemoryMarkdownExporter.extractTags("""{"tags":"a,b"}""")
        )
    }

    @Test
    fun `item fields include createdAt and score lines`() {
        val md = MemoryMarkdownExporter.build(
            memories = listOf(sampleItems[0]),
            exportedAt = 1_700_100_000_000L
        )
        assertTrue(md.contains("createdAt:"))
        assertTrue(md.contains("score: 8.0"))
        assertTrue(md.contains("type: preference"))
        assertTrue(md.contains("lifecycle: permanent"))
    }
}
