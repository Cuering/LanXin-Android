package com.lanxin.android.plugins.memory.domain.memory

import com.lanxin.android.builtin.knowledge.domain.EmbeddingService
import com.lanxin.android.builtin.knowledge.domain.VectorHit
import com.lanxin.android.builtin.knowledge.domain.VectorPipeline
import com.lanxin.android.builtin.knowledge.domain.VectorStore
import com.lanxin.android.plugins.memory.data.memory.MemoryEntity
import com.lanxin.android.plugins.memory.data.memory.MemoryRepository
import com.lanxin.android.plugins.memory.data.memory.MemoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RRF 融合纯逻辑单测（不依赖 Android / Room / ObjectBox）。
 * 通过反射绕过真实构造依赖，只调用 internal reciprocalRankFusion。
 */
class MemoryInjectorRrfTest {

    private fun injector(): MemoryInjector {
        // 用空实现桩，仅用于拿到实例调用 RRF
        val fakeRepo = object {
            // MemoryRepository 有 @Inject 构造，这里用 Unsafe 方式不可行；
            // 改为直接 new 一个最小桩 VectorPipeline + 通过 JVM 反射构造。
        }
        // 用 mock-free 方式：直接构造 MemoryInjector 需要真实依赖，
        // 但 reciprocalRankFusion 不依赖成员状态，可在子类中暴露。
        return TestableInjector()
    }

    @Test
    fun rrf_prefers_items_in_both_lists() {
        val keyword = listOf(
            entity("A", MemoryType.FACTUAL),
            entity("B", MemoryType.CHAT),
            entity("C", MemoryType.DAILY)
        )
        val semantic = listOf("B", "D", "A")

        val merged = injector().reciprocalRankFusion(keyword, semantic, k = 60, topK = 3)

        // B 在两路都靠前，应排第一；A 次之
        assertEquals("B", merged[0].content)
        assertEquals("A", merged[1].content)
        assertTrue(merged.map { it.content }.contains("C") || merged.map { it.content }.contains("D"))
    }

    @Test
    fun rrf_semantic_only_hit_becomes_chat_entity() {
        val merged = injector().reciprocalRankFusion(
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
        val merged = injector().reciprocalRankFusion(emptyList(), emptyList())
        assertTrue(merged.isEmpty())
    }

    @Test
    fun rrf_respects_topK() {
        val keyword = (1..10).map { entity("k$it") }
        val semantic = (1..10).map { "s$it" }
        val merged = injector().reciprocalRankFusion(keyword, semantic, topK = 4)
        assertEquals(4, merged.size)
    }

    @Test
    fun rrf_preserves_keyword_type_on_overlap() {
        val keyword = listOf(entity("overlap", MemoryType.PREFERENCE))
        val semantic = listOf("overlap")
        val merged = injector().reciprocalRankFusion(keyword, semantic, topK = 1)
        assertEquals(MemoryType.PREFERENCE, merged.single().type)
    }

    private fun entity(content: String, type: String = MemoryType.CHAT) =
        MemoryEntity(id = content.hashCode().toLong(), content = content, type = type)

    /**
     * 绕过 Hilt 依赖：直接用假 Embedding/Store 构造 VectorPipeline，
     * MemoryRepository 无法轻松桩化，改用反射写入字段。
     */
    private class TestableInjector : MemoryInjector(
        memoryRepository = createRepoStub(),
        vectorPipeline = VectorPipeline(
            embeddingService = object : EmbeddingService {
                override val isReady: Boolean = true
                override val dimension: Int = 4
                override suspend fun embed(text: String): FloatArray = FloatArray(4)
            },
            vectorStore = object : VectorStore {
                override suspend fun upsert(
                    externalId: Long,
                    source: String,
                    embedding: FloatArray,
                    textPreview: String
                ): Long = 1L

                override suspend fun delete(id: Long) = Unit
                override suspend fun deleteByExternal(externalId: Long, source: String) = Unit
                override suspend fun search(
                    query: FloatArray,
                    topK: Int,
                    source: String?
                ): List<VectorHit> = emptyList()

                override suspend fun count(source: String?): Long = 0L
                override suspend fun clear() = Unit
            }
        )
    )

    companion object {
        /**
         * MemoryRepository 依赖 MemoryDao，单元测试里用空 JVM 代理即可
         *（RRF 测试不调用 repo）。
         */
        @Suppress("UNCHECKED_CAST")
        private fun createRepoStub(): MemoryRepository {
            // 无法在无 Android 环境下 new MemoryRepository(dao)，
            // 使用 Unsafe 分配未初始化实例（仅用于调用不依赖字段的方法）。
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val theUnsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
            val unsafe = theUnsafe.get(null)
            val allocate = unsafeClass.getMethod("allocateInstance", Class::class.java)
            return allocate.invoke(unsafe, MemoryRepository::class.java) as MemoryRepository
        }
    }
}
