package com.lanxin.android.plugin

import android.content.Context
import com.lanxin.android.plugin.dynamic.AndroidPathClassLoaderFactory
import com.lanxin.android.plugin.dynamic.DynamicDiscoverResult
import com.lanxin.android.plugin.dynamic.DynamicPluginClassLoaderFactory
import com.lanxin.android.plugin.dynamic.DynamicPluginHandle
import com.lanxin.android.plugin.dynamic.DynamicPluginLoader
import com.lanxin.android.plugin.dynamic.PluginLoadResult
import com.lanxin.android.plugin.dynamic.PluginPackagePaths
import com.lanxin.android.plugin.dynamic.PluginPackageScanner
import com.lanxin.android.plugin.dynamic.PluginRecord
import com.lanxin.android.plugin.dynamic.PluginSignatureConfig
import com.lanxin.android.plugin.dynamic.PluginSignatureConfigStore
import com.lanxin.android.plugin.dynamic.PluginSignatureInfo
import com.lanxin.android.plugin.dynamic.PluginSignatureVerifier
import com.lanxin.android.plugin.dynamic.PluginSource
import com.lanxin.android.plugin.dynamic.PluginStateStore
import com.lanxin.android.plugin.dynamic.SignaturePolicy
import com.lanxin.android.plugin.dynamic.StoreBackedPluginSignatureVerifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
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
 * Phase 5.3：支持从 `filesDir/plugin-packages/` 动态加载 .apk 插件包。
 * Phase 5.5：市场安装后可通过 [loadDynamicPlugin] 单包加载。
 * Phase 5.6：签名策略 AllowAll / DenyAll / Allowlist（见 docs/plugin-signature.md）。
 */
@Singleton
class PluginManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) : PluginCatalog {

    private val plugins = ConcurrentHashMap<String, LanXinPlugin>()
    private val tools = ConcurrentHashMap<String, ToolDef>()

    /** toolName → 所属 pluginId（用于 disable 时清理工具） */
    private val toolOwners = ConcurrentHashMap<String, String>()
    private val loadedIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val dynamicHandles = ConcurrentHashMap<String, DynamicPluginHandle>()
    private val compiledIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val lastFailures = java.util.Collections.synchronizedList(mutableListOf<PluginLoadResult.Failure>())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val stateStore: PluginStateStore by lazy {
        PluginStateStore(PluginPackagePaths.stateFile(appContext.filesDir))
    }

    private val defaultSignaturePolicy: SignaturePolicy by lazy {
        val debuggable = runCatching {
            (appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        }.getOrDefault(true)
        if (debuggable) SignaturePolicy.ALLOW_ALL else SignaturePolicy.ALLOWLIST
    }

    private val signatureConfigStore: PluginSignatureConfigStore by lazy {
        PluginSignatureConfigStore(
            file = PluginPackagePaths.signatureConfigFile(appContext.filesDir),
            defaultPolicy = defaultSignaturePolicy
        )
    }

    @Volatile
    private var classLoaderFactory: DynamicPluginClassLoaderFactory = AndroidPathClassLoaderFactory

    @Volatile
    private var signatureVerifierOverride: PluginSignatureVerifier? = null

    private val storeBackedVerifier: PluginSignatureVerifier by lazy {
        StoreBackedPluginSignatureVerifier(signatureConfigStore)
    }

    private fun activeSignatureVerifier(): PluginSignatureVerifier =
        signatureVerifierOverride ?: storeBackedVerifier

    @Volatile
    private var appVersionName: String = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    /** 单测注入：替换 ClassLoader 工厂 / 签名校验 / App 版本。 */
    fun configureDynamicLoading(
        classLoaderFactory: DynamicPluginClassLoaderFactory = this.classLoaderFactory,
        signatureVerifier: PluginSignatureVerifier? = this.signatureVerifierOverride,
        appVersionName: String = this.appVersionName
    ) {
        this.classLoaderFactory = classLoaderFactory
        this.signatureVerifierOverride = signatureVerifier
        this.appVersionName = appVersionName
    }

    /** 当前签名策略配置（UI / 单测）。 */
    fun getSignatureConfig(): PluginSignatureConfig = signatureConfigStore.load()

    /** 更新签名策略（立即影响后续 load）。 */
    fun setSignatureConfig(config: PluginSignatureConfig) {
        signatureConfigStore.save(config)
    }

    override fun currentSignaturePolicy(): String = getSignatureConfig().policy.wireName

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
     *
     * @param defaultEnabled 首次落盘默认是否启用；false 时 plugin-state 写 false，loadAll 跳过 onLoad
     */
    fun register(plugin: LanXinPlugin, defaultEnabled: Boolean = true): PluginManager {
        plugins[plugin.id] = plugin
        compiledIds.add(plugin.id)
        stateStore.ensureDefault(plugin.id, defaultEnabled)
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

    fun getPlugins(): List<LanXinPlugin> = plugins.values.toList()

    @Suppress("UNCHECKED_CAST")
    fun <T : LanXinPlugin> get(id: String): T? {
        return plugins[id] as? T
    }

    fun getTools(): List<ToolDef> = tools.values.toList()

    suspend fun callTool(name: String, args: JsonObject): JsonObject {
        val tool = tools[name]
            ?: return buildJsonObject { put("error", "工具 $name 未找到") }
        return try {
            tool.handler(args)
        } catch (e: Exception) {
            buildJsonObject { put("error", "工具执行异常: ${e.message}") }
        }
    }

    fun isEnabled(pluginId: String): Boolean = stateStore.isEnabled(pluginId)

    override suspend fun setEnabled(pluginId: String, enabled: Boolean): Boolean {
        // ConcurrentHashMap: 必须用 containsKey，Kotlin `in` 会误走 containsValue（KT-18053）
        if (!plugins.containsKey(pluginId) && !dynamicHandles.containsKey(pluginId)) {
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

    fun <T : LanXinPlugin> getPluginsByType(clazz: Class<T>): List<T> =
        plugins.values.filterIsInstance(clazz)

    inline fun <reified T : LanXinPlugin> getPluginsByType(): List<T> {
        val snapshot = getPlugins()
        return snapshot.filterIsInstance<T>()
    }

    override suspend fun discoverAndLoadDynamicPlugins(
        packagesDir: File?
    ): DynamicDiscoverResult {
        val dir = packagesDir ?: PluginPackagePaths.ensurePackagesDir(appContext.filesDir)
        lastFailures.clear()
        val scan = PluginPackageScanner.scanWithManifests(dir)
        val successes = mutableListOf<PluginLoadResult.Success>()
        val failures = scan.failures.toMutableList()

        val loader = DynamicPluginLoader(
            classLoaderFactory = classLoaderFactory,
            signatureVerifier = activeSignatureVerifier(),
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

    override suspend fun loadDynamicPlugin(apkFile: File): PluginLoadResult {
        val loader = DynamicPluginLoader(
            classLoaderFactory = classLoaderFactory,
            signatureVerifier = activeSignatureVerifier(),
            appVersionName = appVersionName,
            pluginFactory = testPluginFactory
        )
        val result = loadDynamicPluginInternal(apkFile, loader)
        if (result is PluginLoadResult.Failure) {
            lastFailures.add(result)
        }
        return result
    }

    override suspend fun unloadPlugin(pluginId: String): Boolean {
        if (pluginId in compiledIds && !dynamicHandles.containsKey(pluginId)) {
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

    override fun getPluginRecords(): List<PluginRecord> {
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
                author = handle?.manifest?.author.orEmpty(),
                signature = if (isDynamic) {
                    handle?.signature ?: PluginSignatureInfo.unknown()
                } else {
                    PluginSignatureInfo.notApplicable()
                }
            )
        }
        return records.sortedBy { it.id }
    }

    override fun getLastDynamicFailures(): List<PluginLoadResult.Failure> = lastFailures.toList()

    override fun packagesDirectory(): File =
        PluginPackagePaths.ensurePackagesDir(appContext.filesDir)

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

    private suspend fun loadOne(pluginId: String, plugin: LanXinPlugin) {
        try {
            plugin.onLoad(createContext(pluginId))
            loadedIds.add(pluginId)
        } catch (_: Exception) {
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

                if (plugins.containsKey(id) && !dynamicHandles.containsKey(id)) {
                    return PluginLoadResult.Failure(
                        apkPath = apkFile.absolutePath,
                        pluginId = id,
                        reason = "插件 id 与内置/编译期插件冲突: $id"
                    )
                }

                if (dynamicHandles.containsKey(id)) {
                    unloadPlugin(id)
                }

                plugins[id] = pkg.plugin
                dynamicHandles[id] = DynamicPluginHandle(
                    manifest = pkg.manifest,
                    apkFile = pkg.apkFile,
                    classLoader = pkg.classLoader,
                    plugin = pkg.plugin,
                    signature = pkg.signature
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
                        author = pkg.manifest.author,
                        signature = pkg.signature
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
