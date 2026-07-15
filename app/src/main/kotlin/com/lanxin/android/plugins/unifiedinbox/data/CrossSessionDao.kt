package com.lanxin.android.plugins.unifiedinbox.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CrossSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<CrossSessionEntity>): List<Long>

    @Query("DELETE FROM cross_session_messages")
    suspend fun clearAll()

    @Query("DELETE FROM cross_session_messages WHERE source_chat_id = :chatId")
    suspend fun deleteByChatId(chatId: Int)

    @Query("SELECT COUNT(*) FROM cross_session_messages")
    suspend fun count(): Int

    @Query(
        """
        SELECT * FROM cross_session_messages
        WHERE (:query = '' OR content LIKE '%' || :query || '%' OR session_title LIKE '%' || :query || '%')
          AND (:platform = '' OR platform = :platform)
          AND (:sessionId = '' OR session_id = :sessionId)
          AND (:fromTime = 0 OR time >= :fromTime)
          AND (:toTime = 0 OR time <= :toTime)
        ORDER BY time DESC
        LIMIT :limit
        """
    )
    suspend fun search(
        query: String = "",
        platform: String = "",
        sessionId: String = "",
        fromTime: Long = 0L,
        toTime: Long = 0L,
        limit: Int = 200
    ): List<CrossSessionEntity>

    @Query(
        """
        SELECT * FROM cross_session_messages
        ORDER BY time DESC
        LIMIT :limit
        """
    )
    suspend fun recent(limit: Int = 100): List<CrossSessionEntity>

    @Query(
        """
        SELECT DISTINCT platform FROM cross_session_messages
        ORDER BY platform ASC
        """
    )
    suspend fun listPlatforms(): List<String>

    @Query(
        """
        SELECT session_id, session_title, platform, MAX(time) AS last_time, COUNT(*) AS msg_count
        FROM cross_session_messages
        GROUP BY session_id, platform
        ORDER BY last_time DESC
        """
    )
    suspend fun listSessions(): List<CrossSessionSummaryRow>

    @Query(
        """
        SELECT * FROM cross_session_messages
        WHERE content LIKE '%' || :query || '%'
        ORDER BY time DESC
        LIMIT :limit
        """
    )
    suspend fun searchForInject(query: String, limit: Int = 10): List<CrossSessionEntity>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM cross_session_messages
            WHERE source_message_id = :messageId AND source_chat_id = :chatId
        )
        """
    )
    suspend fun existsSource(messageId: Int, chatId: Int): Boolean
}

/** Room 投影：会话汇总 */
data class CrossSessionSummaryRow(
    @androidx.room.ColumnInfo(name = "session_id") val sessionId: String,
    @androidx.room.ColumnInfo(name = "session_title") val sessionTitle: String,
    val platform: String,
    @androidx.room.ColumnInfo(name = "last_time") val lastTime: Long,
    @androidx.room.ColumnInfo(name = "msg_count") val msgCount: Int
)
