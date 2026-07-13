package com.lanxin.android.data.memory

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_nodes ORDER BY importance DESC, created_at DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memory_nodes WHERE type = :type ORDER BY importance DESC, created_at DESC")
    fun getMemoriesByType(type: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memory_nodes WHERE content LIKE '%' || :keyword || '%' ORDER BY importance DESC")
    suspend fun searchMemories(keyword: String): List<MemoryEntity>

    @Query("SELECT * FROM memory_nodes WHERE id = :id")
    suspend fun getMemoryById(id: Long): MemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    @Query("DELETE FROM memory_nodes WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("SELECT COUNT(*) FROM memory_nodes")
    fun getMemoryCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM memory_nodes WHERE status = 'active'")
    fun getActiveMemoryCount(): Flow<Int>

    @Query(
        "SELECT * FROM memory_nodes WHERE content LIKE '%' || :keyword || '%' " +
            "ORDER BY importance DESC LIMIT :limit"
    )
    suspend fun searchMemoriesForInject(keyword: String, limit: Int = 5): List<MemoryEntity>

    @Query("UPDATE memory_nodes SET last_accessed_at = :timestamp WHERE id = :id")
    suspend fun touchMemory(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT type, COUNT(*) as cnt FROM memory_nodes GROUP BY type")
    suspend fun getTypeCounts(): List<TypeCount>

    @Query("SELECT COUNT(*) FROM memory_nodes WHERE status = 'active'")
    suspend fun getActiveCountOnce(): Int

    @Query("SELECT COUNT(*) FROM memory_nodes")
    suspend fun getTotalCountOnce(): Int
}

data class TypeCount(
    val type: String,
    val cnt: Int
)
