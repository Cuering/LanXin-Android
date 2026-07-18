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
        assertTrue(md.contains("- filter: type=auto_knowledge"))
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
    fun `empty list renders placeholder`() {
        val md = MemoryMarkdownExporter.build(
            memories = emptyList(),
            exportedAt = 1_700_100_000_000L,
            typeFilter = null
        )
        assertTrue(md.contains("- total: 0"))
        assertTrue(md.contains("_（无记忆条目）_"))
    }

    @Test
    fun `matchesTypeFilter supports lifecycle alias`() {
        val item = sampleItems[0]
        assertTrue(MemoryMarkdownExporter.matchesTypeFilter(item, "permanent"))
        assertTrue(MemoryMarkdownExporter.matchesTypeFilter(item, "preference"))
        assertFalse(MemoryMarkdownExporter.matchesTypeFilter(item, "chat"))
    }

    @Test
    fun `status filter keeps only matching status`() {
        val items = listOf(
            sampleItems[0].copy(status = "active"),
            sampleItems[1].copy(status = "archived"),
            sampleItems[2].copy(status = "expired"),
            sampleItems[3].copy(status = "active")
        )
        val filter = MemoryExportFilter(statusFilter = "active")
        val md = MemoryMarkdownExporter.build(
            memories = items,
            exportedAt = 1_700_100_000_000L,
            filter = filter
        )
        assertTrue(md.contains("- filter: status=active"))
        assertTrue(md.contains("- total: 2"))
        assertTrue(md.contains("**永久偏好：喜欢草莓**"))
        assertTrue(md.contains("**与同事的关系备注**"))
        assertFalse(md.contains("**普通对话记忆**"))
        assertFalse(md.contains("**自动抽取：用户在上海**"))
    }

    @Test
    fun `status filter supports multi value OR`() {
        val items = listOf(
            sampleItems[0].copy(status = "active"),
            sampleItems[1].copy(status = "archived"),
            sampleItems[2].copy(status = "expired")
        )
        val kept = MemoryMarkdownExporter.applyFilter(
            items,
            MemoryExportFilter(statusFilter = "active,expired")
        )
        assertEquals(2, kept.size)
        assertTrue(kept.any { it.content.contains("草莓") })
        assertTrue(kept.any { it.content.contains("上海") })
    }

    @Test
    fun `date range filter is inclusive on both ends`() {
        val day = "2023-11-14"
        val after = MemoryExportFilter.parseDayStart(day)!!
        val before = MemoryExportFilter.parseDayEnd(day)!!
        // 2023-11-14 12:00 UTC-ish local: use midpoint of the day range
        val mid = after + 12L * 60L * 60L * 1000L
        val items = listOf(
            sampleItems[0].copy(content = "before", createdAt = after - 1),
            sampleItems[1].copy(content = "start", createdAt = after),
            sampleItems[2].copy(content = "mid", createdAt = mid),
            sampleItems[3].copy(content = "end", createdAt = before),
            sampleItems[0].copy(id = 99, content = "after", createdAt = before + 1)
        )
        val kept = MemoryMarkdownExporter.applyFilter(
            items,
            MemoryExportFilter(createdAfterMs = after, createdBeforeMs = before)
        )
        assertEquals(listOf("start", "mid", "end"), kept.map { it.content })
    }

    @Test
    fun `combined type status and date filters`() {
        val after = 1_700_000_050_000L
        val items = listOf(
            sampleItems[0].copy(status = "active", createdAt = after + 1), // preference active in range
            sampleItems[1].copy(status = "active", createdAt = after + 1), // chat active in range
            sampleItems[0].copy(id = 10, status = "archived", createdAt = after + 1),
            sampleItems[0].copy(id = 11, status = "active", createdAt = after - 10)
        )
        val kept = MemoryMarkdownExporter.applyFilter(
            items,
            MemoryExportFilter(
                typeFilter = MemoryType.PREFERENCE,
                statusFilter = "active",
                createdAfterMs = after
            )
        )
        assertEquals(1, kept.size)
        assertEquals(1L, kept[0].id)
    }

    @Test
    fun `parseDayStart and parseDayEnd reject garbage`() {
        assertEquals(null, MemoryExportFilter.parseDayStart(null))
        assertEquals(null, MemoryExportFilter.parseDayStart(""))
        assertEquals(null, MemoryExportFilter.parseDayStart("not-a-date"))
        assertEquals(null, MemoryExportFilter.parseDayEnd("2023-13-40"))
        val start = MemoryExportFilter.parseDayStart("2024-01-02")!!
        val end = MemoryExportFilter.parseDayEnd("2024-01-02")!!
        assertTrue(end > start)
        assertEquals(24L * 60L * 60L * 1000L - 1L, end - start)
    }

    @Test
    fun `filter describe lists non empty parts`() {
        assertEquals("all", MemoryExportFilter().describe())
        val d = MemoryExportFilter(
            typeFilter = "chat",
            statusFilter = "active",
            createdAfterMs = MemoryExportFilter.parseDayStart("2024-05-01"),
            createdBeforeMs = MemoryExportFilter.parseDayEnd("2024-05-31")
        ).describe()
        assertTrue(d.contains("type=chat"))
        assertTrue(d.contains("status=active"))
        assertTrue(d.contains("after=2024-05-01"))
        assertTrue(d.contains("before=2024-05-31"))
    }
}
