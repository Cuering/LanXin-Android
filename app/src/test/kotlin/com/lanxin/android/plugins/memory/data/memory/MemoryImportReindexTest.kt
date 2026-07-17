package com.lanxin.android.plugins.memory.data.memory

import com.lanxin.android.plugins.memory.domain.memory.ImportStrategy
import com.lanxin.android.plugins.memory.domain.memory.MemoryIndexRebuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证 import 后调用 reindex，且 REPLACE 会 clear 索引源。
 * 使用内存 FakeDao，不依赖 Room / Android 运行时。
 */
class MemoryImportReindexTest {

    @Test
    fun `import MERGE_DEDUP reindexes inserted rows`() = runBlocking {
        val dao = FakeMemoryDao()
        val rebuilder = RecordingRebuilder()
        val repo = MemoryRepository(dao, rebuilder)

        val payload = """
            {
              "version": 1,
              "exportedAt": 1,
              "memories": [
                {"content":"喜欢草莓","type":"preference","importance":8},
                {"content":"住在上海","type":"factual","importance":7}
              ]
            }
        """.trimIndent()

        val result = repo.importFromJsonTextPublic(payload, ImportStrategy.MERGE_DEDUP)
        assertEquals(2, result.imported)
        assertEquals(0, result.skipped)
        assertEquals(2, result.reindexed)
        assertEquals(0, rebuilder.clearCount)
        assertEquals(2, rebuilder.reindexedEntities.size)
        assertTrue(result.message.contains("重建索引"))
    }

    @Test
    fun `import REPLACE clears index sources then reindexes`() = runBlocking {
        val dao = FakeMemoryDao(
            initial = listOf(
                MemoryEntity(id = 9, content = "旧记忆", type = "chat")
            )
        )
        val rebuilder = RecordingRebuilder()
        val repo = MemoryRepository(dao, rebuilder)

        val payload = """
            {
              "version": 1,
              "exportedAt": 1,
              "memories": [
                {"content":"新记忆A","type":"chat"},
                {"content":"新记忆B","type":"chat"}
              ]
            }
        """.trimIndent()

        val result = repo.importFromJsonTextPublic(payload, ImportStrategy.REPLACE)
        assertEquals(2, result.imported)
        assertEquals(1, rebuilder.clearCount)
        assertEquals(2, result.reindexed)
        assertEquals(2, dao.store.size)
        assertTrue(dao.store.none { it.content == "旧记忆" })
    }

    @Test
    fun `import empty payload does not reindex`() = runBlocking {
        val rebuilder = RecordingRebuilder()
        val repo = MemoryRepository(FakeMemoryDao(), rebuilder)
        val payload = """{"version":1,"exportedAt":1,"memories":[]}"""
        val result = repo.importFromJsonTextPublic(payload, ImportStrategy.MERGE_DEDUP)
        assertEquals(0, result.imported)
        assertEquals(0, result.reindexed)
        assertEquals(0, rebuilder.reindexedEntities.size)
    }

    @Test
    fun `invalid json throws`() = runBlocking {
        val repo = MemoryRepository(FakeMemoryDao(), RecordingRebuilder())
        try {
            repo.importFromJsonTextPublic("{not-json", ImportStrategy.MERGE_DEDUP)
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("JSON"))
        }
    }

    @Test
    fun `exportToJsonText roundtrip`() = runBlocking {
        val dao = FakeMemoryDao(
            initial = listOf(
                MemoryEntity(id = 1, content = "hello", type = "chat", importance = 3f)
            )
        )
        val repo = MemoryRepository(dao, Noop)
        val text = repo.exportToJsonText()
        assertTrue(text.contains("hello"))
        assertTrue(text.contains("\"version\": 1") || text.contains("\"version\":1"))
    }

    private object Noop : MemoryIndexRebuilder {
        override suspend fun clearMemorySources() = Unit
        override suspend fun reindex(entities: List<MemoryEntity>): Int = 0
    }

    private class RecordingRebuilder : MemoryIndexRebuilder {
        var clearCount = 0
        val reindexedEntities = mutableListOf<MemoryEntity>()

        override suspend fun clearMemorySources() {
            clearCount++
        }

        override suspend fun reindex(entities: List<MemoryEntity>): Int {
            reindexedEntities += entities
            return entities.size
        }
    }

    /**
     * 最小 Fake DAO：仅覆盖 import/export 用到的方法。
     */
    private class FakeMemoryDao(
        initial: List<MemoryEntity> = emptyList()
    ) : MemoryDao {
        val store = initial.toMutableList()
        private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

        override fun getAllMemories(): Flow<List<MemoryEntity>> = flowOf(store.toList())
        override fun getMemoriesByType(type: String): Flow<List<MemoryEntity>> =
            flowOf(store.filter { it.type == type })
        override suspend fun searchMemories(keyword: String): List<MemoryEntity> =
            store.filter { it.content.contains(keyword) }
        override suspend fun getMemoryById(id: Long): MemoryEntity? = store.find { it.id == id }
        override suspend fun insertMemory(memory: MemoryEntity): Long {
            val id = if (memory.id > 0) memory.id else nextId++
            val entity = memory.copy(id = id)
            store.removeAll { it.id == id }
            store += entity
            return id
        }
        override suspend fun updateMemory(memory: MemoryEntity) {
            store.removeAll { it.id == memory.id }
            store += memory
        }
        override suspend fun deleteMemory(memory: MemoryEntity) {
            store.removeAll { it.id == memory.id }
        }
        override suspend fun deleteMemoryById(id: Long) {
            store.removeAll { it.id == id }
        }
        override fun getMemoryCount(): Flow<Int> = flowOf(store.size)
        override fun getActiveMemoryCount(): Flow<Int> =
            flowOf(store.count { it.status == "active" })
        override suspend fun searchMemoriesForInject(keyword: String, limit: Int): List<MemoryEntity> =
            store.filter { it.content.contains(keyword) }.take(limit)
        override suspend fun touchMemory(id: Long, timestamp: Long) = Unit
        override suspend fun getTypeCounts(): List<TypeCount> =
            store.groupBy { it.type }.map { TypeCount(it.key, it.value.size) }
        override suspend fun getActiveCountOnce(): Int = store.count { it.status == "active" }
        override suspend fun getTotalCountOnce(): Int = store.size
        override suspend fun getAllMemoriesOnce(): List<MemoryEntity> = store.toList()
        override suspend fun getAllIds(): List<Long> = store.map { it.id }
        override suspend fun existsByContentAndType(content: String, type: String): Boolean =
            store.any { it.content == content && it.type == type }
        override suspend fun insertMemoryIgnore(memory: MemoryEntity): Long = insertMemory(memory)
        override suspend fun insertMemories(memories: List<MemoryEntity>) {
            memories.forEach { insertMemory(it) }
        }
        override suspend fun deleteAll() {
            store.clear()
        }
        override suspend fun getJudgmentMemories(): List<MemoryEntity> =
            store.filter { it.type == "judgment" }
        override suspend fun getExpiredMemories(cutoff: Long): List<MemoryEntity> = emptyList()
        override suspend fun touchMemoryById(id: Long, timestamp: Long) = Unit
        override suspend fun markExpired(id: Long) = Unit
        override suspend fun deleteExpiredMemories() = Unit
        override suspend fun getUserProfile(): UserEntity? = null
        override suspend fun upsertUserProfile(entity: UserEntity) = Unit
        override suspend fun getEvolutionEntries(limit: Int): List<EvolutionEntry> = emptyList()
        override suspend fun insertEvolutionEntry(entry: EvolutionEntry) = Unit
        override suspend fun getPendingTaskResume(): TaskResumeEntity? = null
        override suspend fun markTaskResumeResolved(id: Long) = Unit
        override suspend fun insertTaskResume(resume: TaskResumeEntity) = Unit
        override suspend fun getUnarchivedDialogs(limit: Int): List<DialogEntity> = emptyList()
        override suspend fun markDialogArchived(id: Long) = Unit
    }
}
