package com.lanxin.android.plugin

import android.content.Context
import androidx.compose.runtime.Composable

/**
 * LanXin 插件接口。
 * 所有插件（内置和第三方）都需要实现此接口。
 */
interface LanXinPlugin {
    /** 唯一标识，如 "lanxin.memory" */
    val id: String

    /** 插件展示名称，如 "记忆系统" */
    val name: String

    /** 插件版本号 */
    val version: String

    /** 插件简要描述 */
    val description: String

    /** 插件初始化，应用启动时调用 */
    fun onInitialize(context: Context)

    /**
     * 消息预处理钩子。
     * 在用户消息发送给 LLM 前调用，插件可以修改消息内容或注入上下文。
     * 返回 null 表示不处理（跳过）。
     * @param message 原始用户消息
     * @return 修改后的消息，或 null
     */
    fun onMessagePreprocess(message: String): String? = null

    /**
     * 插件设置页（可选的）。
     * 返回 Composable 会在设置界面中展示。
     */
    val settingsScreen: @Composable (() -> Unit)? get() = null
}
