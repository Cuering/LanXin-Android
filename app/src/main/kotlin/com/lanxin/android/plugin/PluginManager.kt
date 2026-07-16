package com.lanxin.android.plugin

import android.content.Context
import com.lanxin.android.plugin.dynamic.AllowAllPluginSignatureVerifier
import com.lanxin.android.plugin.dynamic.AndroidPathClassLoaderFactory
import com.lanxin.android.plugin.dynamic.DynamicDiscoverResult
import com.lanxin.android.plugin.dynamic.DynamicPluginClassLoaderFactory
import com.lanxin.android.plugin.dynamic.DynamicPluginHandle
import com.lanxin.android.plugin.dynamic.DynamicPluginLoader
import com.lanxin.android.plugin.dynamic.PluginLoadResult
import com.lanxin.android.plugin.dynamic.PluginPackagePaths
import com.lanxin.android.plugin.dynamic.PluginPackageScanner
import com.lanxin.android.plugin.dynamic.PluginRecord
import com.lanxin.android.plugin.dynamic.PluginSignatureVerifier
import com.lanxin.android.plugin.dynamic.PluginSource
import com.lanxin.android.plugin.dynamic.PluginStateStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 插件管理器。
 *
 * 负责插件的注册、加载、生命周期管理，
 * 以及 MCP 工具的注册与调度。
 *
 * Phase 5.3：支持从 `filesDir/plugin-packages/` 动态加载 .apk 插件包，
 * 并提供 enable / disable / unload 状态机（见 docs/dynamic-plugins.md）。
 */
@Singleton
class PluginManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    private val plugins = mutableMapOf<String, LanXinPlugin>()
    private val tools = mutableMapOf<String, ToolDef>()
    /** toolName → 所属 pluginId（用于 disable 时清理工具） */
    private val toolOwners = mutableMapOf<String, String>()
    private val loadedIds = mutableSetOf<String>()
    private val dynamicHandles = mutableMapOf<String, DynamicPluginHandle>()
    private val compiledIds = mutableSetOf<String>()
    private val lastFailures = mutableListOf<PluginLoadResult.Failure>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val stateStore: PluginStateStore by lazy {
        PluginStateStore(PluginPackagePaths.stateFile(appContext.filesDir))
    }

    @Volatile
    private var classLoaderFactory: DynamicPluginClassLoaderFactory = AndroidPathClassLoaderFactory

    @Volatile
    private var signatureVerifier: PluginSignatureVerifier = AllowAllPluginSignatureVerifier

    @Volatile
    private var appVersionName: String = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    /** 单测注入：替换 ClassLoader 工厂 / 签名校验 / App 版本。 */
    fun configureDynamicLoading(
        classLoaderFactory: DynamicPluginClassLoaderFactory = this.classLoaderFactory,
        signatureVerifier: PluginSignatureVerifier = this.signatureVerifier,
        appVersionName: String = this.appVersionName
    ) {
        this.classLoaderFactory = classLoaderFactory
        this.signatureVerifier = signatureVerifier
        this.appVersionName = appVersionName
    }

    /**
     * 可选：单测用的插件工厂（跳过 dex 实例化）。
     * 由 [discoverAndLoadDynamicPlugins] 读取。
     */
    @Volatile
    var testPluginFactory: ((com.lanxin.android.plugin.dynamic.PluginManifest, File) -> LanXinPlugin?)? =
        null

    /**
     * 注册一个插件（非挂起，可在 DI @Provides 中调用）。
     * 默认视为编译期插件（builtin / 源码 plugins）。
     */
    fun register(plugin: LanXinPlugin): PluginManager {
        plugins[plugin.id] = plugin
        compiledIds.add(plugin.id)
        return this
    }

    /**
     * 加载所有已注册且 **enabled** 的插件（调用 onLoad）。
     * 幂等：已加载过的插件不会重复加载。
     */
    suspend fun loadAll() {
        for ((id, plugin) in plugins.toMap()) {
            if (id !in loadedIds && isEnabled(id)) {
                loadOne(id, plugin)
            }
        }
    }

    /**
     * 获取所有已注册的插件列表。
     */
    fun getPlugins(): List<LanXinPlugin> = plugins.values.toList()

    /**
     * 按 ID 获取插件。
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : LanXinPlugin> get(id: String): T? {
        return plugins[id] as? T
    }

    /**
     * 获取所有已注册的工具（仅已 load 的插件贡献）。
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

    /** 插件是否启用；未写入状态时默认 true。 */
    fun isEnabled(pluginId: String): Boolean = stateStore.isEnabled(pluginId)

    /**
     * 启用 / 停用插件。
     *
     * - disable：若已 load 则 onUnload 并移除其工具
     * - enable：若已注册且未 load 则 onLoad
     */
    suspend fun setEnabled(pluginId: String, enabled: Boolean): Boolean {
        if (pluginId !in plugins && pluginId !in dynamicHandles) {
            // 仍持久化，便于尚未扫描到的包
            stateStore.setEnabled(pluginId, enabled)
            return false
        }
        stateStore.setEnabled(pluginId, enabled)
        val plugin = plugins[pluginId] ?: return true
        if (!enabled) {
            if (pluginId in loadedIds) {
                runCatching { plugin.onUnload() }
                removeToolsFor(pluginId)
                loadedIds.remove(pluginId)
            }
        } else {
            if (pluginId !in loadedIds) {
                loadOne(pluginId, plugin)
            }
        }
        return true
    }

    /**
     * 按类型查找已注册插件（Phase 2 API）。
     */
    inline fun <reified T : LanXinPlugin> getPluginsByType(): List<T> =
        plugins.values.filterIsInstance<T>()

    /**
     * 扫描 `filesDir/plugin-packages/` 并加载动态插件。
     * 失败项记入结果，不抛异常、不中断宿主。
     */
    suspend fun discoverAndLoadDynamicPlugins(
        packagesDir: File = PluginPackagePaths.ensurePackagesDir(appContext.filesDir)
    ): DynamicDiscoverResult {
        lastFailures.clear()
        val scan = PluginPackageScanner.scanWithManifests(packagesDir)
        val successes = mutableListOf<PluginLoadResult.Success>()
        val failures = scan.failures.toMutableList()

        val loader = DynamicPluginLoader(
            classLoaderFactory = classLoaderFactory,
            signatureVerifier = signatureVerifier,
            appVersionName = appVersionName,
            pluginFactory = testPluginFactory
        )

        for ((apk, _) in scan.packages) {
            when (val result = loadDynamicPluginInternal(apk, loader)) {
                is PluginLoadResult.Success -> successes += result
                is PluginLoadResult.Failure -> failures += result
            }
        }

        lastFailures += failures
        return DynamicDiscoverResult(successes = successes, failures = failures)
    }

    /**
     * 加载单个动态插件包。
     */
    suspend fun loadDynamicPlugin(apkFile: File): PluginLoadResult {
        val loader = DynamicPluginLoader(
            classLoaderFactory = classLoaderFactory,
            signatureVerifier = signatureVerifier,
            appVersionName = appVersionName,
            pluginFactory = testPluginFactory
        )
        return loadDynamicPluginInternal(apkFile, loader)
    }

    /**
     * 卸载动态插件（从注册表移除并 onUnload）。
     * 编译期插件返回 false。
     */
    suspend fun unloadPlugin(pluginId: String): Boolean {
        if (pluginId in compiledIds && pluginId !in dynamicHandles) {
            return false
        }
        val plugin = plugins[pluginId] ?: return false
        if (pluginId in loadedIds) {
            runCatching { plugin.onUnload() }
            removeToolsFor(pluginId)
            loadedIds.remove(pluginId)
        }
        plugins.remove(pluginId)
        dynamicHandles.remove(pluginId)
        return true
    }

    /**
     * 插件记录列表（编译期 + 动态），供 5.4 管理 UI。
     */
    fun getPluginRecords(): List<PluginRecord> {
        val records = mutableListOf<PluginRecord>()
        for ((id, plugin) in plugins) {
            val handle = dynamicHandles[id]
            val isDynamic = handle != null
            records += PluginRecord(
                id = id,
                name = plugin.name,
                version = plugin.version,
                description = plugin.description,
                source = if (isDynamic) PluginSource.DYNAMIC else PluginSource.COMPILED,
                enabled = isEnabled(id),
                removable = isDynamic && (handle?.manifest?.removable ?: true),
                apkPath = handle?.apkFile?.absolutePath,
                author = handle?.manifest?.author.orEmpty()
            )
        }
        return records.sortedBy { it.id }
    }

    fun getLastDynamicFailures(): List<PluginLoadResult.Failure> = lastFailures.toList()

    /**
     * 卸载所有插件，释放资源。
     */
    suspend fun destroy() {
        plugins.values.forEach {
            runCatching { it.onUnload() }
        }
        plugins.clear()
        tools.clear()
        toolOwners.clear()
        loadedIds.clear()
        dynamicHandles.clear()
        compiledIds.clear()
        lastFailures.clear()
        scope.cancel()
    }

    // ── 内部 ──

    private suspend fun loadOne(pluginId: String, plugin: LanXinPlugin) {
        try {
            plugin.onLoad(createContext(pluginId))
            loadedIds.add(pluginId)
        } catch (_: Exception) {
            // 单插件 onLoad 失败不拖垮宿主
            removeToolsFor(pluginId)
            loadedIds.remove(pluginId)
        }
    }

    private suspend fun loadDynamicPluginInternal(
        apkFile: File,
        loader: DynamicPluginLoader
    ): PluginLoadResult {
        return when (val loaded = loader.loadPackage(apkFile)) {
            is DynamicPluginLoader.LoadPackageResult.Error ->
                PluginLoadResult.Failure(
                    apkPath = loaded.apkPath,
                    pluginId = loaded.pluginId,
                    reason = loaded.reason
                )

            is DynamicPluginLoader.LoadPackageResult.Ok -> {
                val pkg = loaded.pkg
                val id = pkg.manifest.id

                if (id in plugins && id !in dynamicHandles) {
                    return PluginLoadResult.Failure(
                        apkPath = apkFile.absolutePath,
                        pluginId = id,
                        reason = "插件 id 与内置/编译期插件冲突: $id"
                    )
                }

                // 已加载过同一动态 id：先卸载再换包
                if (id in dynamicHandles) {
                    unloadPlugin(id)
                }

                plugins[id] = pkg.plugin
                dynamicHandles[id] = DynamicPluginHandle(
                    manifest = pkg.manifest,
                    apkFile = pkg.apkFile,
                    classLoader = pkg.classLoader,
                    plugin = pkg.plugin
                )

                val enabled = isEnabled(id)
                var didLoad = false
                if (enabled) {
                    loadOne(id, pkg.plugin)
                    didLoad = id in loadedIds
                }

                PluginLoadResult.Success(
                    record = PluginRecord(
                        id = id,
                        name = pkg.plugin.name,
                        version = pkg.plugin.version,
                        description = pkg.plugin.description,
                        source = PluginSource.DYNAMIC,
                        enabled = enabled,
                        removable = true,
                        apkPath = apkFile.absolutePath,
                        author = pkg.manifest.author
                    ),
                    loaded = didLoad
                )
            }
        }
    }

    private fun removeToolsFor(pluginId: String) {
        val names = toolOwners.filter { it.value == pluginId }.keys.toList()
        for (name in names) {
            tools.remove(name)
            toolOwners.remove(name)
        }
    }

    private fun createContext(pluginId: String): PluginContext = object : PluginContext {

        override fun registerTool(tool: ToolDef) {
            tools[tool.name] = tool
            toolOwners[tool.name] = pluginId
        }

        override val filesDir: File
            get() = File(appContext.filesDir, "plugins/$pluginId").also { it.mkdirs() }

        override suspend fun sendMessage(message: String) {
            // TODO: 对接 AI 核心消息通道
        }
    }
}
