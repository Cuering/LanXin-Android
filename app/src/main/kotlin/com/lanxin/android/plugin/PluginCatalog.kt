package com.lanxin.android.plugin

import com.lanxin.android.plugin.dynamic.DynamicDiscoverResult
import com.lanxin.android.plugin.dynamic.PluginLoadResult
import com.lanxin.android.plugin.dynamic.PluginRecord
import java.io.File

/**
 * 插件目录门面（Phase 5.4 UI / 5.5 市场 / 单测可替换）。
 *
 * [PluginManager] 为默认实现；UI 与单测仅依赖本接口，避免 mock 具体类。
 */
interface PluginCatalog {

    /** 编译期 + 动态插件记录列表。 */
    fun getPluginRecords(): List<PluginRecord>

    /** 最近一次动态扫描/加载失败项。 */
    fun getLastDynamicFailures(): List<PluginLoadResult.Failure>

    /** 启用 / 停用插件。 */
    suspend fun setEnabled(pluginId: String, enabled: Boolean): Boolean

    /**
     * 卸载动态插件（从注册表移除）。
     * 编译期插件返回 false。
     */
    suspend fun unloadPlugin(pluginId: String): Boolean

    /** 扫描 plugin-packages 并加载动态插件。 */
    suspend fun discoverAndLoadDynamicPlugins(
        packagesDir: File? = null
    ): DynamicDiscoverResult

    /**
     * 加载单个动态插件包（Phase 5.5 市场安装后调用）。
     */
    suspend fun loadDynamicPlugin(apkFile: File): PluginLoadResult

    /** 动态插件包目录（用于 UI 提示路径）。 */
    fun packagesDirectory(): File
}
