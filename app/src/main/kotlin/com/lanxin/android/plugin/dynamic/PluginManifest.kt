package com.lanxin.android.plugin.dynamic

import com.lanxin.android.core.engine.PluginMetadata
import java.io.File

/**
 * 动态插件包清单（来自 APK 内 assets/lanxin-plugin.json）。
 */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val entryClass: String,
    val author: String = "",
    val minAppVersion: String = "",
    val removable: Boolean = true
) {
    fun toMetadata(): PluginMetadata = PluginMetadata(
        id = id,
        name = name,
        version = version,
        description = description,
        author = author,
        removable = removable,
        minAppVersion = minAppVersion
    )
}

/** 插件来源。 */
enum class PluginSource {
    /** Hilt / 源码内 register 的 builtin 或编译期 plugins */
    COMPILED,

    /** 从 filesDir 动态加载的 .apk */
    DYNAMIC
}

/** 动态插件运行时句柄。 */
data class DynamicPluginHandle(
    val manifest: PluginManifest,
    val apkFile: File,
    val classLoader: ClassLoader?,
    val plugin: com.lanxin.android.plugin.LanXinPlugin
)

/** 对外展示 / 管理用的插件记录（5.4 UI 预留）。 */
data class PluginRecord(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val source: PluginSource,
    val enabled: Boolean,
    val removable: Boolean,
    val apkPath: String? = null,
    val author: String = "",
    val loadError: String? = null
)

/** 单包加载结果。 */
sealed class PluginLoadResult {
    data class Success(
        val record: PluginRecord,
        val loaded: Boolean
    ) : PluginLoadResult()

    data class Failure(
        val apkPath: String?,
        val pluginId: String?,
        val reason: String
    ) : PluginLoadResult()
}

/** 批量发现结果。 */
data class DynamicDiscoverResult(
    val successes: List<PluginLoadResult.Success> = emptyList(),
    val failures: List<PluginLoadResult.Failure> = emptyList()
) {
    val total: Int get() = successes.size + failures.size
}
