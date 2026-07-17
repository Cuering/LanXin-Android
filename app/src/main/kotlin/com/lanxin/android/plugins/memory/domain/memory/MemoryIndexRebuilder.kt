package com.lanxin.android.plugins.memory.domain.memory

import android.util.Log
import com.lanxin.android.builtin.knowledge.domain.VectorPipeline
import com.lanxin.android.builtin.knowledge.domain.VectorSource
import com.lanxin.android.plugins.memory.data.memory.MemoryEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P4：导入记忆后重建 dense/sparse 向量索引的钩子。
 *
 * 导出 JSON 不含 embedding，导入后必须 re-index，否则语义检索失效。
 */
interface MemoryIndexRebuilder {
    /**
     * REPLACE 策略：清空 memory / auto_knowledge 源的旧向量后再写。
     */
    suspend fun clearMemorySources()

    /**
     * 为已入库记忆建立向量 + 稀疏索引。
     * @return 成功索引条数（embed 失败不计入）
     */
    suspend fun reindex(entities: List<MemoryEntity>): Int
}

/** Worker 等无 VectorPipeline 场景使用。 */
object NoOpMemoryIndexRebuilder : MemoryIndexRebuilder {
    override suspend fun clearMemorySources() = Unit

    override suspend fun reindex(entities: List<MemoryEntity>): Int = 0
}

@Singleton
class VectorPipelineMemoryIndexRebuilder @Inject constructor(
    private val vectorPipeline: VectorPipeline
) : MemoryIndexRebuilder {

    override suspend fun clearMemorySources() {
        runCatching { vectorPipeline.deleteBySource(VectorSource.MEMORY) }
            .onFailure { Log.w(TAG, "clear MEMORY index failed: ${it.message}") }
        runCatching { vectorPipeline.deleteBySource(VectorSource.AUTO_KNOWLEDGE) }
            .onFailure { Log.w(TAG, "clear AUTO_KNOWLEDGE index failed: ${it.message}") }
    }

    override suspend fun reindex(entities: List<MemoryEntity>): Int {
        if (entities.isEmpty()) return 0
        var ok = 0
        for (entity in entities) {
            if (entity.id <= 0L || entity.content.isBlank()) continue
            val source = resolveSource(entity)
            val vid = runCatching {
                vectorPipeline.index(
                    externalId = entity.id,
                    source = source,
                    text = entity.content
                )
            }.getOrElse {
                Log.w(TAG, "reindex id=${entity.id} failed: ${it.message}")
                -1L
            }
            if (vid > 0L) ok++
        }
        return ok
    }

    companion object {
        private const val TAG = "MemoryIndexRebuilder"

        fun resolveSource(entity: MemoryEntity): String {
            val meta = entity.metadata.orEmpty()
            return if (meta.contains("auto_knowledge")) {
                VectorSource.AUTO_KNOWLEDGE
            } else {
                VectorSource.MEMORY
            }
        }
    }
}
