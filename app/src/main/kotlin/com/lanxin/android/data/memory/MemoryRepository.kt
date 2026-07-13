package com.lanxin.android.data.memory

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val dao: MemoryDao
) {
    fun getAllMemories(): Flow<List<MemoryEntity>> = dao.getAllMemories()

    fun getMemoriesByType(type: String): Flow<List<MemoryEntity>> = dao.getMemoriesByType(type)

    suspend fun searchMemories(keyword: String): List<MemoryEntity> {
        if (keyword.isBlank()) return emptyList()
        return dao.searchMemories(keyword.trim())
    }

    suspend fun getMemoryById(id: Long): MemoryEntity? = dao.getMemoryById(id)

    suspend fun addMemory(
        content: String,
        type: String = MemoryType.CHAT,
        importance: Float = 5.0f,
        lifecycle: String = "permanent",
        metadata: String? = null
    ): Long {
        val memory = MemoryEntity(
            content = content.trim(),
            type = type,
            importance = importance.coerceIn(1f, 10f),
            status = "active",
            lifecycle = lifecycle,
            createdAt = System.currentTimeMillis(),
            metadata = metadata
        )
        return dao.insertMemory(memory)
    }

    suspend fun updateMemory(memory: MemoryEntity) {
        dao.updateMemory(memory)
    }

    suspend fun deleteMemory(id: Long) {
        dao.deleteMemoryById(id)
    }

    suspend fun getStats(): Map<String, Int> {
        val total = dao.getTotalCountOnce()
        val active = dao.getActiveCountOnce()
        val typeCounts = dao.getTypeCounts().associate { it.type to it.cnt }
        return buildMap {
            put("total", total)
            put("active", active)
            MemoryType.ALL.forEach { type ->
                put(type, typeCounts[type] ?: 0)
            }
        }
    }

    /**
     * 聊天注入专用检索（关键词 LIKE，Phase 2.1 再换向量检索）。
     */
    suspend fun searchForInject(keyword: String, limit: Int = 5): List<MemoryEntity> {
        if (keyword.isBlank()) return emptyList()
        val results = dao.searchMemoriesForInject(keyword.trim(), limit)
        val now = System.currentTimeMillis()
        results.forEach { dao.touchMemory(it.id, now) }
        return results
    }
}
