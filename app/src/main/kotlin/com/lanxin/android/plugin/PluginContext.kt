package com.lanxin.android.plugin

import java.io.File

/**
 * 插件运行上下文。
 *
 * 插件通过此接口与核心交互。
 */
interface PluginContext {

    /** 注册一个 MCP 工具，供 AI 调用 */
    fun registerTool(tool: ToolDef)

    /** 插件专属存储目录 */
    val filesDir: File

    /** 向 AI 核心发送消息 */
    suspend fun sendMessage(message: String)
}
