package com.lanxin.android.plugins.memory

import android.content.Context
import com.lanxin.android.plugin.LanXinPlugin
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryPlugin @Inject constructor() : LanXinPlugin {
    override val id = "lanxin.memory"
    override val name = "记忆系统"
    override val version = "1.0.0"
    override val description = "本地记忆仓库，自动保存与注入聊天上下文"

    override fun onInitialize(context: Context) {
        // Room 自动初始化
    }

    override fun onMessagePreprocess(message: String): String? = null
}
