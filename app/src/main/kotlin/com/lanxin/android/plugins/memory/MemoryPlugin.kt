package com.lanxin.android.plugins.memory

import com.lanxin.android.plugin.LanXinPlugin
import com.lanxin.android.plugin.PluginContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryPlugin @Inject constructor() : LanXinPlugin {

    override val id = "lanxin.memory"
    override val name = "记忆系统"
    override val version = "1.0.0"
    override val description = "本地记忆仓库，自动保存与注入聊天上下文"

    override suspend fun onLoad(context: PluginContext) {
        // TODO: 注册 MCP 工具 memory_store / memory_recall
    }
}
