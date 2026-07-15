package com.lanxin.android.builtin.knowledge.sparse

import com.lanxin.android.builtin.knowledge.domain.sparse.SparseRrf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 稀疏/稠密 RRF 融合不退化单测。
 */
class SparseRrfTest {

    @Test
    fun fuse_prefers_items_in_both_lists() {
        val dense = listOf("A", "B", "C")
        val sparse = listOf("B", "D", "A")
        val fused = SparseRrf.fuse(listOf(dense, sparse), k = 60, topK = 3)
        assertEquals("B", fused[0])
        assertEquals("A", fused[1])
    }

    @Test
    fun fuse_single_list_preserves_order() {
        val only = listOf("x", "y", "z")
        val fused = SparseRrf.fuse(listOf(only), topK = 2)
        assertEquals(listOf("x", "y"), fused)
    }

    @Test
    fun fuse_empty_returns_empty() {
        assertTrue(SparseRrf.fuse(emptyList()).isEmpty())
        assertTrue(SparseRrf.fuse(listOf(emptyList(), emptyList())).isEmpty())
    }

    @Test
    fun fuse_skips_blank_keys() {
        val fused = SparseRrf.fuse(
            listOf(listOf("", "keep"), listOf("  ", "keep")),
            topK = 5
        )
        assertEquals(listOf("keep"), fused)
    }

    @Test
    fun fuse_respects_topK() {
        val a = (1..10).map { "a$it" }
        val b = (1..10).map { "b$it" }
        val fused = SparseRrf.fuse(listOf(a, b), topK = 4)
        assertEquals(4, fused.size)
    }

    @Test
    fun fuse_does_not_degrade_when_one_side_empty() {
        val dense = listOf("d1", "d2", "d3")
        val fused = SparseRrf.fuse(listOf(dense, emptyList()), topK = 3)
        assertEquals(dense, fused)
    }

    @Test
    fun fuseWithPayload_keeps_first_seen_text() {
        val primary = listOf("k1" to "from-primary", "k2" to "p2")
        val secondary = listOf("k1" to "from-secondary", "k3" to "s3")
        val fused = SparseRrf.fuseWithPayload(primary, secondary, topK = 3)
        val map = fused.toMap()
        assertEquals("from-primary", map["k1"])
        assertTrue(map.containsKey("k2") || map.containsKey("k3"))
    }

    @Test
    fun rrf_k60_classic_both_rank1_beats_single_rank1() {
        // B rank1 both sides; A rank1 only dense
        val dense = listOf("A", "B")
        val sparse = listOf("B", "C")
        val fused = SparseRrf.fuse(listOf(dense, sparse), k = 60, topK = 3)
        assertEquals("B", fused[0])
    }
}
