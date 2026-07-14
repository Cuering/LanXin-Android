package com.lanxin.android.plugins.memory.domain.memory

import com.lanxin.android.plugins.memory.data.memory.MemoryEntity
import com.lanxin.android.plugins.memory.data.memory.MemoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RRF 融合纯逻辑单测（无 Android / Room / Hilt 依赖）。
 */
class MemoryInjectorRrfTest {

    @Test
    fun rrf_prefers_items_in_both_lists() {
        val keyword = listOf(
            entity("A", MemoryType.FACTUAL),
            entity("B", MemoryType.CHAT),
            entity("C", MemoryType.DAILY)
        )
        val semantic = listOf("B", "D", "A")

        val merged = MemoryInjector.reciprocalRankFusion(
            keywordResults = keyword,
            semanticTexts = semantic,
            k = 60,
            topK = 3
        )

        // B 在两路都靠前，应排第一；A 次之
        assertEquals("B", merged[0].content)
        assertEquals("A", merged[1].content)
        assertTrue(
            merged.map { it.content }.contains("C") ||
                merged.map { it.content }.contains("D")
        )
    }

    @Test
    fun rrf_semantic_only_hit_becomes_chat_entity() {
        val merged = MemoryInjector.reciprocalRankFusion(
            keywordResults = emptyList(),
            semanticTexts = listOf("纯语义命中"),
            k = 60,
            topK = 5
        )
        assertEquals(1, merged.size)
        assertEquals("纯语义命中", merged[0].content)
        assertEquals(MemoryType.CHAT, merged[0].type)
    }

    @Test
    fun rrf_empty_both_returns_empty() {
        val merged = MemoryInjector.reciprocalRankFusion(
            keywordResults = emptyList(),
            semanticTexts = emptyList()
        )
        assertTrue(merged.isEmpty())
    }

    @Test
    fun rrf_respects_topK() {
        val keyword = (1..10).map { entity("k$it") }
        val semantic = (1..10).map { "s$it" }
        val merged = MemoryInjector.reciprocalRankFusion(
            keywordResults = keyword,
            semanticTexts = semantic,
            topK = 4
        )
        assertEquals(4, merged.size)
    }

    @Test
    fun rrf_preserves_keyword_type_on_overlap() {
        val keyword = listOf(entity("overlap", MemoryType.PREFERENCE))
        val semantic = listOf("overlap")
        val merged = MemoryInjector.reciprocalRankFusion(
            keywordResults = keyword,
            semanticTexts = semantic,
            topK = 1
        )
        assertEquals(MemoryType.PREFERENCE, merged.single().type)
    }

    @Test
    fun rrf_skips_blank_semantic_texts() {
        val merged = MemoryInjector.reciprocalRankFusion(
            keywordResults = listOf(entity("keep")),
            semanticTexts = listOf("", "  ", "keep"),
            topK = 5
        )
        assertEquals(1, merged.size)
        assertEquals("keep", merged[0].content)
    }

    private fun entity(content: String, type: String = MemoryType.CHAT) =
        MemoryEntity(id = content.hashCode().toLong(), content = content, type = type)
}
