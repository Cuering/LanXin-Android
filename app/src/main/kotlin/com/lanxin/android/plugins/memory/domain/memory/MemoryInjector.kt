package com.lanxin.android.plugins.memory.domain.memory

import com.lanxin.android.builtin.knowledge.domain.VectorPipeline
import com.lanxin.android.builtin.knowledge.domain.VectorSource
import com.lanxin.android.plugins.memory.data.memory.MemoryEntity
import com.lanxin.android.plugins.memory.data.memory.MemoryRepository
import com.lanxin.android.plugins.memory.data.memory.MemoryType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在发送聊天消息前检索本地记忆并注入上下文。
 *
 * 双路召回 + RRF 融合：
 * 1. 关键词 LIKE（MemoryRepository）
 * 2. 语义向量（VectorPipeline，source=memory）
 *
 * 语义路失败时自动降级为纯关键词。
 */
@Singleton
class MemoryInjector @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val vectorPipeline: VectorPipeline
) {
    @Volatile
    var enabled: Boolean = true

    /** 语义向量检索开关，默认开启；关闭后仅走关键词路。 */
    @Volatile
    var semanticEnabled: Boolean = true

    /**
     * 将匹配记忆注入到用户消息前面。
     * 若无匹配或注入关闭，原样返回。
     */
    suspend fun inject(question: String, limit: Int = 5): String {
        if (!enabled || question.isBlank()) return question

        val keyword = extractKeyword(question)

        // 路1：关键词搜索
        val keywordResults = memoryRepository.searchForInject(keyword, limit * 2)

        // 路2：语义向量搜索（降级为空）
        val semanticResults = if (semanticEnabled) {
            try {
                vectorPipeline.search(
                    query = keyword,
                    topK = limit * 2,
                    source = VectorSource.MEMORY
                )
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val merged = reciprocalRankFusion(
            keywordResults = keywordResults,
            semanticTexts = semanticResults.map { it.textPreview },
            k = RRF_K,
            topK = limit
        )

        if (merged.isEmpty()) return question

        val lines = merged.joinToString("\n") { item ->
            val typeLabel = MemoryType.displayName(item.type)
            "- [$typeLabel] ${item.content}"
        }

        return buildString {
            appendLine("[我的记忆]")
            appendLine(lines)
            appendLine("[记忆结束]")
            appendLine()
            append(question)
        }
    }

    /**
     * 提取检索关键词：取前 20 个字或整句。
     */
    private fun extractKeyword(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.length <= 20) trimmed else trimmed.take(20)
    }

    companion object {
        /** RRF 平滑常数，经典取值 60。 */
        const val RRF_K = 60

        /**
         * Reciprocal Rank Fusion：两路按排名累加 1/(k+rank)，再取 topK。
         *
         * 语义路只有文本预览，若能与关键词结果 content 对齐则复用 MemoryEntity
         * （保留 type 标签）；纯语义命中则降级为 type=chat 的伪实体。
         */
        fun reciprocalRankFusion(
            keywordResults: List<MemoryEntity>,
            semanticTexts: List<String>,
            k: Int = RRF_K,
            topK: Int = 5
        ): List<MemoryEntity> {
            val scores = mutableMapOf<String, Double>()
            val entityByContent = LinkedHashMap<String, MemoryEntity>()

            keywordResults.forEachIndexed { index, entity ->
                val key = entity.content
                scores[key] = scores.getOrDefault(key, 0.0) + 1.0 / (k + index + 1)
                entityByContent.putIfAbsent(key, entity)
            }

            semanticTexts.forEachIndexed { index, text ->
                if (text.isBlank()) return@forEachIndexed
                scores[text] = scores.getOrDefault(text, 0.0) + 1.0 / (k + index + 1)
                entityByContent.putIfAbsent(
                    text,
                    MemoryEntity(content = text, type = MemoryType.CHAT)
                )
            }

            return scores.entries
                .sortedByDescending { it.value }
                .take(topK)
                .mapNotNull { entityByContent[it.key] }
        }
    }
}
