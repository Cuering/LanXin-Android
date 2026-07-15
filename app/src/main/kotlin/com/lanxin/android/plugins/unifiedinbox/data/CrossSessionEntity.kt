package com.lanxin.android.plugins.unifiedinbox.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 跨 session 统一对话归档。
 *
 * 索引：`(platform, session_id, time)`，便于按平台/会话/时间过滤。
 */
@Entity(
    tableName = "cross_session_messages",
    indices = [
        Index(value = ["platform", "session_id", "time"]),
        Index(value = ["time"]),
        Index(value = ["session_id"])
    ]
)
data class CrossSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 来源平台：local / openai / anthropic / wechat 等 */
    @ColumnInfo(name = "platform")
    val platform: String,

    /** 会话 ID（本地 chat_id 或外部 session 标识） */
    @ColumnInfo(name = "session_id")
    val sessionId: String,

    /** 会话标题（便于 UI 标注来源） */
    @ColumnInfo(name = "session_title")
    val sessionTitle: String = "",

    /** 消息时间戳（毫秒） */
    @ColumnInfo(name = "time")
    val time: Long,

    /** 角色：user / assistant */
    @ColumnInfo(name = "role")
    val role: String,

    @ColumnInfo(name = "content")
    val content: String,

    /** 源 messages_v2.message_id，用于去重 */
    @ColumnInfo(name = "source_message_id")
    val sourceMessageId: Int? = null,

    /** 源 chat_id */
    @ColumnInfo(name = "source_chat_id")
    val sourceChatId: Int? = null
)

object CrossSessionRole {
    const val USER = "user"
    const val ASSISTANT = "assistant"
}

object CrossSessionPlatform {
    const val LOCAL = "local"
}
