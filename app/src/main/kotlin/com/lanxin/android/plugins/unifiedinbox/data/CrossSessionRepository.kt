package com.lanxin.android.plugins.unifiedinbox.data

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CrossSessionRepository @Inject constructor(
    private val dao: CrossSessionDao
) {
    suspend fun insertAll(messages: List<CrossSessionEntity>): Int = withContext(Dispatchers.IO) {
        if (messages.isEmpty()) return@withContext 0
        dao.insertAll(messages).size
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }

    suspend fun deleteByChatId(chatId: Int) = withContext(Dispatchers.IO) {
        dao.deleteByChatId(chatId)
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        dao.count()
    }

    suspend fun search(
        query: String = "",
        platform: String = "",
        sessionId: String = "",
        fromTime: Long = 0L,
        toTime: Long = 0L,
        limit: Int = 200
    ): List<CrossSessionEntity> = withContext(Dispatchers.IO) {
        dao.search(
            query = query.trim(),
            platform = platform.trim(),
            sessionId = sessionId.trim(),
            fromTime = fromTime,
            toTime = toTime,
            limit = limit.coerceIn(1, 1000)
        )
    }

    suspend fun recent(limit: Int = 100): List<CrossSessionEntity> = withContext(Dispatchers.IO) {
        dao.recent(limit.coerceIn(1, 500))
    }

    suspend fun listPlatforms(): List<String> = withContext(Dispatchers.IO) {
        dao.listPlatforms()
    }

    suspend fun listSessions(): List<CrossSessionSummaryRow> = withContext(Dispatchers.IO) {
        dao.listSessions()
    }

    suspend fun searchForInject(query: String, limit: Int = 5): List<CrossSessionEntity> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) emptyList()
            else dao.searchForInject(query.trim(), limit.coerceIn(1, 20))
        }

    companion object {
        private const val TAG = "CrossSessionRepo"

        fun logIndexResult(indexed: Int, total: Int) {
            Log.i(TAG, "indexed=$indexed total=$total")
        }
    }
}
