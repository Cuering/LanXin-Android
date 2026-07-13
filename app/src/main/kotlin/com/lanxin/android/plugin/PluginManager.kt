package com.lanxin.android.plugin

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 插件管理器。
 *
 * 负责插件的注册、加载、生命周期管理，
 * 以及 MCP 工具的注册与调度。
 */
@Singleton
class PluginManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    private val plugins = mutableMapOf<String, LanXinPlugin>()
    private val tools = mutableMapOf<String, ToolDef>()
    private val loadedIds = mutableSetOf<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 注册一个插件（非挂起，可在 DI @Provides 中调用）。
     */
    fun register(plugin: LanXinPlugin): PluginManager {
        plugins[plugin.id] = plugin
        return this
    }

    /**
     * 加载所有已注册的插件（调用 onLoad）。
     * 幂等：已加载过的插件不会重复加载。
     */
    suspend fun loadAll() {
        for ((id, plugin) in plugins) {
            if (id !in loadedIds) {
                plugin.onLoad(createContext(id))
                loadedIds.add(id)
            }
        }
    }

    /**
     * 按 ID 获取插件。
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : LanXinPlugin> get(id: String): T? {
        return plugins[id] as? T
    }

    /**
     * 获取所有已注册的工具。
     */
    fun getTools(): List<ToolDef> = tools.values.toList()

    /**
     * 调用指定工具。
     */
    suspend fun callTool(name: String, args: JsonObject): JsonObject {
        val tool = tools[name]
            ?: return buildJsonObject { put("error", "工具 $name 未找到") }
        return try {
            tool.handler(args)
        } catch (e: Exception) {
            buildJsonObject { put("error", "工具执行异常: ${e.message}") }
        }
    }

    /**
     * 卸载所有插件，释放资源。
     */
    suspend fun destroy() {
        plugins.values.forEach { it.onUnload() }
        plugins.clear()
        tools.clear()
        loadedIds.clear()
        scope.cancel()
    }

    // ── 内部 ──

    private fun createContext(pluginId: String): PluginContext = object : PluginContext {

        override fun registerTool(tool: ToolDef) {
            tools[tool.name] = tool
        }

        override val filesDir: File
            get() = File(appContext.filesDir, "plugins/$pluginId").also { it.mkdirs() }

        override suspend fun sendMessage(message: String) {
            // TODO: 对接 AI 核心消息通道
        }
    }
}
