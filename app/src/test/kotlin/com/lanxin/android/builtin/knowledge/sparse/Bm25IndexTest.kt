package com.lanxin.android.builtin.knowledge.sparse

import com.lanxin.android.builtin.knowledge.domain.sparse.Bm25Index
import com.lanxin.android.builtin.knowledge.domain.sparse.SparseIndexItem
import com.lanxin.android.builtin.knowledge.domain.sparse.Tokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BM25 与分词纯逻辑单测（无 Android 依赖）。
 */
class Bm25IndexTest {

    @Test
    fun tokenize_chinese_unigram_and_bigram() {
        val tokens = Tokenizer.tokenize("北京欢迎你")
        assertTrue(tokens.contains("北"))
        assertTrue(tokens.contains("北京"))
        assertTrue(tokens.contains("欢迎"))
    }

    @Test
    fun tokenize_english_lowercase_and_stopwords() {
        val tokens = Tokenizer.tokenize("The Quick Brown Fox jumps over the lazy dog")
        assertTrue(tokens.contains("quick"))
        assertTrue(tokens.contains("brown"))
        assertTrue(tokens.none { it == "the" || it == "over" })
    }

    @Test
    fun tokenize_mixed_cjk_latin() {
        val tokens = Tokenizer.tokenize("我喜欢 Kotlin 语言")
        assertTrue(tokens.contains("kotlin"))
        assertTrue(tokens.contains("喜欢") || tokens.contains("喜"))
    }

    @Test
    fun tokenize_blank_returns_empty() {
        assertTrue(Tokenizer.tokenize("").isEmpty())
        assertTrue(Tokenizer.tokenize("   ").isEmpty())
    }

    @Test
    fun bm25_ranks_relevant_doc_higher() {
        val index = Bm25Index()
        index.index(
            listOf(
                item(1, "猫喜欢吃鱼和睡觉"),
                item(2, "狗喜欢跑步和玩球"),
                item(3, "今天天气很好适合出门")
            )
        )
        val results = index.search("猫 吃鱼", topK = 3)
        assertTrue(results.isNotEmpty())
        assertEquals(1L, results[0].documentId)
    }

    @Test
    fun bm25_chinese_query() {
        val index = Bm25Index()
        index.index(
            listOf(
                item(10, "兰心是一款 Android 聊天应用"),
                item(20, "今天的天气预报有雨"),
                item(30, "向量检索与稀疏检索可以融合")
            )
        )
        val results = index.search("稀疏检索 BM25", topK = 2)
        assertTrue(results.isNotEmpty())
        assertEquals(30L, results[0].documentId)
    }

    @Test
    fun bm25_empty_docs_returns_empty() {
        val index = Bm25Index()
        index.index(emptyList())
        assertTrue(index.search("anything").isEmpty())
        assertTrue(index.isEmpty)
    }

    @Test
    fun bm25_empty_query_returns_empty() {
        val index = Bm25Index()
        index.index(listOf(item(1, "hello world")))
        assertTrue(index.search("").isEmpty())
        assertTrue(index.search("   ").isEmpty())
    }

    @Test
    fun bm25_topK_respected() {
        val index = Bm25Index()
        index.index(
            (1..10).map { item(it.toLong(), "文档编号 $it 关于检索 检索 检索") }
        )
        val results = index.search("检索", topK = 3)
        assertEquals(3, results.size)
    }

    @Test
    fun bm25_fingerprint_reuse_skips_rebuild() {
        val items = listOf(item(1, "alpha"), item(2, "beta"))
        val index = Bm25Index()
        index.index(items)
        assertEquals(2, index.size)
        // same fingerprint → no-op rebuild
        index.index(items)
        assertEquals(2, index.size)
        val hit = index.search("alpha", topK = 1)
        assertEquals(1L, hit.single().documentId)
    }

    @Test
    fun bm25_scores_are_positive_for_hits() {
        val index = Bm25Index()
        index.index(listOf(item(1, "machine learning models")))
        val results = index.search("machine learning", topK = 1)
        assertTrue(results.single().score > 0.0)
    }

    private fun item(id: Long, text: String) = SparseIndexItem(
        documentId = id,
        source = "test",
        text = text
    )
}
