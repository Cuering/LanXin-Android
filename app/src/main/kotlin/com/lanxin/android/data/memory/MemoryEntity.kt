package com.lanxin.android.data.memory

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_nodes")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "content") val content: String,
    // 类型: preference(偏好), factual(事实), daily(日常),
    //       chat(对话), insight(洞察), instruction(指令)
    @ColumnInfo(name = "type") val type: String = "chat",
    @ColumnInfo(name = "importance") val importance: Float = 5.0f,
    // 状态: active(活跃), archived(归档), expired(过期)
    @ColumnInfo(name = "status") val status: String = "active",
    // 生命周期: permanent(永久), normal(普通), ephemeral(临时)
    @ColumnInfo(name = "lifecycle") val lifecycle: String = "permanent",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_accessed_at") val lastAccessedAt: Long? = null,
    // metadata: JSON 字符串，存额外信息
    @ColumnInfo(name = "metadata") val metadata: String? = null
)

object MemoryType {
    const val PREFERENCE = "preference"
    const val FACTUAL = "factual"
    const val DAILY = "daily"
    const val CHAT = "chat"
    const val INSIGHT = "insight"
    const val INSTRUCTION = "instruction"

    val ALL = listOf(PREFERENCE, FACTUAL, DAILY, CHAT, INSIGHT, INSTRUCTION)

    fun displayName(type: String): String = when (type) {
        PREFERENCE -> "偏好"
        FACTUAL -> "事实"
        DAILY -> "日常"
        CHAT -> "对话"
        INSIGHT -> "洞察"
        INSTRUCTION -> "指令"
        else -> type
    }
}
