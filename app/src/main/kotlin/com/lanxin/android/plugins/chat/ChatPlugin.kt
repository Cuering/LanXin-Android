package com.lanxin.android.plugins.chat

import android.content.Context
import com.lanxin.android.core.engine.BackupContributor
import com.lanxin.android.core.engine.ChatHistoryProvider
import com.lanxin.android.plugin.LanXinPlugin
import com.lanxin.android.plugin.PluginContext
import com.lanxin.android.plugins.chat.data.ChatHistoryProviderImpl
import com.lanxin.android.plugins.chat.data.dao.MessageV2Dao
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatPlugin @Inject constructor(
    private val messageV2Dao: MessageV2Dao,
    private val chatHistoryProvider: ChatHistoryProviderImpl
) : LanXinPlugin, ChatHistoryProvider by chatHistoryProvider, BackupContributor {

    override val id = "lanxin.chat"
    override val name = "对话管理"
    override val version = "1.0.0"
    override val description = "本地聊天历史管理、搜索与导出"

    override suspend fun onLoad(context: PluginContext) {
        // TODO: 注册聊天相关的 MCP 工具
    }

    override fun getBackupFiles(context: Context): List<File> {
        val dbDir = context.getDatabasePath("chat_v2").parentFile ?: return emptyList()
        return listOf("chat", "chat_v2")
            .flatMap { name ->
                listOf(
                    File(dbDir, name),
                    File(dbDir, "$name-shm"),
                    File(dbDir, "$name-wal")
                )
            }
            .filter { it.exists() }
    }
}
