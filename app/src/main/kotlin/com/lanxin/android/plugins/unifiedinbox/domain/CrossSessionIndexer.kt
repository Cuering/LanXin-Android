package com.lanxin.android.plugins.unifiedinbox.domain

import android.util.Log
import com.lanxin.android.plugins.chat.data.dao.ChatRoomV2Dao
import com.lanxin.android.plugins.chat.data.dao.MessageV2Dao
import com.lanxin.android.plugins.chat.data.entity.MessageV2
import com.lanxin.android.plugins.chat.data.entity.effectiveContent
import com.lanxin.android.plugins.unifiedinbox.data.CrossSessionEntity
import com.lanxin.android.plugins.unifiedinbox.data.CrossSessionPlatform
import com.lanxin.android.plugins.unifiedinbox.data.CrossSessionRepository
import com.lanxin.android.plugins.unifiedinbox.data.CrossSessionRole
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 聚合 `plugins/chat` 所有本地 session 的对话，写入统一 Room 库。
 *
 * 纯 Android 端实现：从 chat_v2 读取 → 映射 → 全量重建索引。
 */
@Singleton
class CrossSessionIndexer @Inject constructor(
    private val chatRoomV2Dao: ChatRoomV2Dao,
    private val messageV2Dao: MessageV2Dao,
    private val repository: CrossSessionRepository
) {
    data class IndexResult(
        val sessions: Int,
        val messages: Int,
        val durationMs: Long
    )

    /**
     * 全量重建索引（先清空再写入，保证与 chat_v2 一致）。
     */
    suspend fun reindexAll(): IndexResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val rooms = chatRoomV2Dao.getChatRooms()
        val entities = mutableListOf<CrossSessionEntity>()

        for (room in rooms) {
            val messages = messageV2Dao.loadMessages(room.id)
            for (msg in messages) {
                val content = msg.effectiveContent().trim()
                if (content.isBlank()) continue
                entities += mapMessage(
                    message = msg,
                    sessionId = room.id.toString(),
                    sessionTitle = room.title.ifBlank { "会话 #${room.id}" }
                )
            }
        }

        repository.clearAll()
        val inserted = repository.insertAll(entities)
        val duration = System.currentTimeMillis() - start
        Log.i(TAG, "reindex sessions=${rooms.size} messages=$inserted durationMs=$duration")
        IndexResult(sessions = rooms.size, messages = inserted, durationMs = duration)
    }

    /**
     * 仅重建单个 chat room 的索引。
     */
    suspend fun reindexChat(chatId: Int, title: String = ""): Int = withContext(Dispatchers.IO) {
        val messages = messageV2Dao.loadMessages(chatId)
        val sessionTitle = title.ifBlank { "会话 #$chatId" }
        val entities = messages.mapNotNull { msg ->
            val content = msg.effectiveContent().trim()
            if (content.isBlank()) null
            else mapMessage(msg, chatId.toString(), sessionTitle)
        }
        repository.deleteByChatId(chatId)
        repository.insertAll(entities)
    }

    companion object {
        private const val TAG = "CrossSessionIndexer"

        /**
         * MessageV2 → CrossSessionEntity。
         * - platformType == null → user
         * - platformType != null → assistant，platform 取平台标识
         * - createdAt 在 chat 中为秒，统一转为毫秒
         */
        fun mapMessage(
            message: MessageV2,
            sessionId: String,
            sessionTitle: String
        ): CrossSessionEntity {
            val isAssistant = !message.platformType.isNullOrBlank()
            val role = if (isAssistant) CrossSessionRole.ASSISTANT else CrossSessionRole.USER
            val platform = when {
                isAssistant -> normalizePlatform(message.platformType!!)
                else -> CrossSessionPlatform.LOCAL
            }
            val timeMs = normalizeTimeMs(message.createdAt)
            return CrossSessionEntity(
                platform = platform,
                sessionId = sessionId,
                sessionTitle = sessionTitle,
                time = timeMs,
                role = role,
                content = message.effectiveContent().trim(),
                sourceMessageId = message.id,
                sourceChatId = message.chatId.takeIf { it > 0 } ?: sessionId.toIntOrNull()
            )
        }

        fun normalizePlatform(raw: String): String {
            val t = raw.trim().lowercase()
            return t.ifBlank { CrossSessionPlatform.LOCAL }
        }

        /** chat 存秒；若已是毫秒（> 1e12）则原样。 */
        fun normalizeTimeMs(raw: Long): Long {
            return if (raw > 1_000_000_000_000L) raw else raw * 1000L
        }
    }
}
