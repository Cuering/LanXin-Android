package com.lanxin.android.core.engine

import android.content.Context
import com.lanxin.android.plugins.chat.data.entity.MessageV2
import com.lanxin.android.plugins.memory.data.memory.MemoryEntity
import java.io.File

/**
 * 插件元数据 — 统一描述格式。
 */
data class PluginMetadata(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val icon: Int? = null,
    val author: String = "",
    val removable: Boolean = true,
    val minAppVersion: String = ""
)

/**
 * 记忆提供者 — plugins/memory 实现。
 */
interface MemoryProvider {
    suspend fun inject(context: String): String
    suspend fun getMemories(): List<MemoryEntity>
}

/**
 * 聊天历史提供者 — plugins/chat 实现。
 */
interface ChatHistoryProvider {
    suspend fun search(query: String): List<MessageV2>
    suspend fun export(): File
}

/**
 * 备份贡献者 — 各插件可选实现。
 */
interface BackupContributor {
    fun getBackupFiles(context: Context): List<File>
}
