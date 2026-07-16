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

/** 动态插件签名展示状态（Phase 5.6 UI）。 */
enum class PluginSignatureStatus {
    /** 编译期插件或未校验。 */
    NOT_APPLICABLE,

    /** 通过当前策略。 */
    TRUSTED,

    /** 被当前策略拒绝（仅失败列表；成功记录不会出现）。 */
    REJECTED,

    /** 尚未有结果 / 未知。 */
    UNKNOWN
}

/** 附加在 [PluginRecord] 上的签名摘要。 */
data class PluginSignatureInfo(
    val status: PluginSignatureStatus,
    val policy: String = "",
    val certificateSha256: List<String> = emptyList(),
    val detail: String? = null
) {
    fun displayLabel(): String = when (status) {
        PluginSignatureStatus.NOT_APPLICABLE -> "签名: 不适用"
        PluginSignatureStatus.TRUSTED -> {
            val p = policy.ifBlank { "?" }
            "签名: 已校验 ($p)"
        }
        PluginSignatureStatus.REJECTED -> {
            val p = if (policy.isNotBlank()) " [$policy]" else ""
            "签名: 失败$p${detail?.let { ": $it" } ?: ""}"
        }
        PluginSignatureStatus.UNKNOWN -> "签名: 未知"
    }

    companion object {
        fun notApplicable(): PluginSignatureInfo =
            PluginSignatureInfo(status = PluginSignatureStatus.NOT_APPLICABLE)

        fun unknown(policy: String = ""): PluginSignatureInfo =
            PluginSignatureInfo(status = PluginSignatureStatus.UNKNOWN, policy = policy)
    }
}

/** 动态插件运行时句柄。 */
data class DynamicPluginHandle(
    val manifest: PluginManifest,
    val apkFile: File,
    val classLoader: ClassLoader?,
    val plugin: com.lanxin.android.plugin.LanXinPlugin,
    val signature: PluginSignatureInfo = PluginSignatureInfo.unknown()
)

/** 对外展示 / 管理用的插件记录（5.4 UI + 5.6 签名状态）。 */
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
    val loadError: String? = null,
    val signature: PluginSignatureInfo = PluginSignatureInfo.notApplicable()
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
