package com.lanxin.android.plugin

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 插件管理器，负责注册、初始化、调度所有插件。
 */
@Singleton
class PluginManager @Inject constructor() {

    private val plugins = mutableListOf<LanXinPlugin>()

    /** 所有已注册的插件（只读） */
    fun getPlugins(): List<LanXinPlugin> = plugins.toList()

    /** 按 ID 查找插件 */
    fun getPlugin(id: String): LanXinPlugin? = plugins.find { it.id == id }

    /**
     * 注册一个插件（由 DI 模块或应用启动时调用）。
     */
    fun register(plugin: LanXinPlugin) {
        if (plugins.any { it.id == plugin.id }) {
            Log.w(TAG, "插件已存在，跳过注册: ${plugin.id}")
            return
        }
        plugins.add(plugin)
        Log.i(TAG, "插件已注册: ${plugin.id} (${plugin.name})")
    }

    /**
     * 初始化所有已注册的插件。
     */
    fun initializeAll(context: Context) {
        plugins.forEach { plugin ->
            try {
                plugin.onInitialize(context)
                Log.i(TAG, "插件已初始化: ${plugin.id}")
            } catch (e: Exception) {
                Log.e(TAG, "插件初始化失败: ${plugin.id}", e)
            }
        }
    }

    /**
     * 对消息依次执行所有插件的预处理钩子。
     * 插件按注册顺序执行，后一个接收前一个的输出。
     */
    fun preprocessMessage(message: String): String {
        var result = message
        for (plugin in plugins) {
            try {
                val modified = plugin.onMessagePreprocess(result)
                if (modified != null) {
                    result = modified
                }
            } catch (e: Exception) {
                Log.e(TAG, "插件预处理异常: ${plugin.id}", e)
            }
        }
        return result
    }

    /**
     * 获取所有插件提供的设置页。
     */
    fun getSettingsScreens(): List<Pair<LanXinPlugin, @Composable (() -> Unit)?>> {
        return plugins.map { it to it.settingsScreen }
    }

    companion object {
        private const val TAG = "PluginManager"
    }
}
