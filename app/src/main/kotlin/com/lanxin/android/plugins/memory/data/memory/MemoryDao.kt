package com.lanxin.android.plugins.memory.data.memory

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
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

    @Query("SELECT * FROM memory_nodes ORDER BY importance DESC, created_at DESC")
    suspend fun getAllMemoriesOnce(): List<MemoryEntity>

    @Query("SELECT id FROM memory_nodes")
    suspend fun getAllIds(): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM memory_nodes WHERE content = :content AND type = :type LIMIT 1)")
    suspend fun existsByContentAndType(content: String, type: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemoryIgnore(memory: MemoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemories(memories: List<MemoryEntity>)

    @Query("DELETE FROM memory_nodes")
    suspend fun deleteAll()

    // === Phase 5.7 新增查询 ===

    /** 获取所有 judgment 类型记忆 */
    @Query("SELECT * FROM memory_nodes WHERE type = 'judgment' ORDER BY importance DESC")
    suspend fun getJudgmentMemories(): List<MemoryEntity>

    /** 获取即将过期的记忆 */
    @Query("SELECT * FROM memory_nodes WHERE status = 'active' AND lifecycle != 'permanent' AND last_accessed_at < :cutoff")
    suspend fun getExpiredMemories(cutoff: Long): List<MemoryEntity>

    /** 更新所有记忆的最后访问时间（衰减用） */
    @Query("UPDATE memory_nodes SET last_accessed_at = :timestamp WHERE id = :id")
    suspend fun touchMemoryById(id: Long, timestamp: Long = System.currentTimeMillis())

    /** 标记过期记忆 */
    @Query("UPDATE memory_nodes SET status = 'expired' WHERE id = :id")
    suspend fun markExpired(id: Long)

    /** 删除过期记忆 */
    @Query("DELETE FROM memory_nodes WHERE status = 'expired'")
    suspend fun deleteExpiredMemories()

    /** 获取用户画像记录 */
    @Query("SELECT * FROM user_profiles ORDER BY id DESC LIMIT 1")
    suspend fun getUserProfile(): UserEntity?

    /** 保存/更新用户画像 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserProfile(entity: UserEntity)

    /** 获取进化条目 */
    @Query("SELECT * FROM evolution_entries ORDER BY created_at DESC LIMIT :limit")
    suspend fun getEvolutionEntries(limit: Int = 5): List<EvolutionEntry>

    /** 插入进化条目 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvolutionEntry(entry: EvolutionEntry)

    /** 获取待续接任务 */
    @Query("SELECT * FROM task_resumes WHERE status = 'pending' ORDER BY created_at DESC LIMIT 1")
    suspend fun getPendingTaskResume(): TaskResumeEntity?

    /** 标记任务续接为已处理 */
    @Query("UPDATE task_resumes SET status = 'resolved' WHERE id = :id")
    suspend fun markTaskResumeResolved(id: Long)

    /** 插入任务续接记录 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskResume(resume: TaskResumeEntity)

    /** 获取未归档的最近对话 */
    @Query("SELECT * FROM dialog_archive WHERE archived = 0 ORDER BY created_at DESC LIMIT :limit")
    suspend fun getUnarchivedDialogs(limit: Int = 100): List<DialogEntity>

    /** 标记对话已归档 */
    @Query("UPDATE dialog_archive SET archived = 1 WHERE id = :id")
    suspend fun markDialogArchived(id: Long)
}

data class TypeCount(
    val type: String,
    val cnt: Int
)

/** 用户画像实体 */
@Entity(tableName = "user_profiles")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "summary") val summary: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

/** 进化条目实体 */
@Entity(tableName = "evolution_entries")
data class EvolutionEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/** 任务续接实体 */
@Entity(tableName = "task_resumes")
data class TaskResumeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "status") val status: String = "pending", // pending / resolved
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/** 对话归档实体 */
@Entity(tableName = "dialog_archive")
data class DialogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "sender") val sender: String,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "archived") val archived: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
