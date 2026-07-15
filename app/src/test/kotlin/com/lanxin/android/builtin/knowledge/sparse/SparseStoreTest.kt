package com.lanxin.android.builtin.knowledge.sparse

import com.lanxin.android.builtin.knowledge.domain.sparse.Bm25Index
import com.lanxin.android.builtin.knowledge.domain.sparse.SparseIndexItem
import com.lanxin.android.builtin.knowledge.domain.sparse.Tokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SparseStore 核心逻辑的纯 JVM 测试。
 *
 * FTS Room DAO 依赖 Android，此处验证：
 * - match query 构造
 * - 内存 BM25 路径 CRUD 语义（index/search/clear）
 * - 与 documentId 关联的排序稳定性
 */
class SparseStoreTest {

    @Test
    fun buildMatchQuery_joins_tokens_with_or() {
        // 复刻 SparseStore.buildMatchQuery 逻辑（internal，直接测 Tokenizer + 规则）
        val tokens = Tokenizer.tokenizeUnique("猫 和 狗 play")
            .map { it.replace(Regex("[\"'*:(){}\\[\\]^~]"), "") }
            .filter { it.isNotBlank() }
        val match = tokens.joinToString(" OR ") { "\"$it\"" }
        assertTrue(match.contains("\"猫\"") || match.contains("\"狗\""))
        assertTrue(match.contains("\"play\""))
        // 停用词「和」应被去掉
        assertTrue(!match.contains("\"和\""))
    }

    @Test
    fun buildMatchQuery_blank_tokens_empty() {
        val tokens = Tokenizer.tokenizeUnique("的 了 在")
        // 全是停用词
        assertTrue(tokens.isEmpty())
    }

    @Test
    fun inMemory_index_search_delete_semantics() {
        val index = Bm25Index()
        val items = listOf(
            SparseIndexItem(1, "knowledge", "Android 向量数据库 ObjectBox"),
            SparseIndexItem(2, "knowledge", "Room FTS 全文检索"),
            SparseIndexItem(3, "memory", "用户喜欢猫咪")
        )
        index.index(items)

        val kbHits = index.search("全文检索 FTS", topK = 5)
        assertTrue(kbHits.isNotEmpty())
        assertEquals(2L, kbHits[0].documentId)

        // 模拟 delete：重建去掉 id=2
        index.index(items.filter { it.documentId != 2L })
        val afterDelete = index.search("全文检索 FTS", topK = 5)
        assertTrue(afterDelete.none { it.documentId == 2L })
    }

    @Test
    fun upsert_overwrite_same_documentId() {
        val index = Bm25Index()
        index.index(
            listOf(SparseIndexItem(1, "knowledge", "旧内容 苹果"))
        )
        assertEquals(1L, index.search("苹果", 1).single().documentId)

        // upsert 覆盖
        index.index(
            listOf(SparseIndexItem(1, "knowledge", "新内容 香蕉"))
        )
        assertTrue(index.search("苹果", 5).isEmpty())
        assertEquals(1L, index.search("香蕉", 1).single().documentId)
    }

    @Test
    fun source_filter_via_post_filter() {
        val index = Bm25Index()
        index.index(
            listOf(
                SparseIndexItem(1, "memory", "用户生日是三月"),
                SparseIndexItem(2, "knowledge", "三月份销售报告")
            )
        )
        val all = index.search("三月", topK = 10)
        assertEquals(2, all.size)
        val memoryOnly = all.filter { it.source == "memory" }
        assertEquals(1, memoryOnly.size)
        assertEquals(1L, memoryOnly[0].documentId)
    }

    @Test
    fun clear_empties_index() {
        val index = Bm25Index()
        index.index(listOf(SparseIndexItem(1, "k", "hello")))
        index.clear()
        assertTrue(index.isEmpty)
        assertTrue(index.search("hello").isEmpty())
    }

    @Test
    fun payload_roundtrip_on_search_result() {
        val index = Bm25Index()
        index.index(
            listOf(
                SparseIndexItem(
                    documentId = 9,
                    source = "memory",
                    text = "偏好辣味食物",
                    payload = "preference"
                )
            )
        )
        val hit = index.search("辣味", topK = 1).single()
        assertEquals("preference", hit.payload)
        assertEquals(9L, hit.documentId)
    }
}
