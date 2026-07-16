package com.lanxin.android.plugin.market

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 插件市场索引条目（Phase 5.5）。
 *
 * 对应远程 JSON 索引中的单条插件元数据。
 */
@Serializable
data class MarketPluginEntry(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val author: String = "",
    @SerialName("download_url")
    val downloadUrl: String,
    @SerialName("min_app_version")
    val minAppVersion: String = "",
    /** 可选 SHA-256（小写 hex）；为空则跳过哈希校验。 */
    val checksum: String = "",
    /** 期望字节数；小于等于 0 表示不校验 size。 */
    val size: Long = 0L
)

/**
 * 远程市场索引根对象。
 *
 * 示例：
 * ```
 * {
 *   "schema_version": 1,
 *   "updated_at": "2026-07-16T00:00:00Z",
 *   "plugins": [ ... ]
 * }
 * ```
 */
@Serializable
data class MarketCatalog(
    @SerialName("schema_version")
    val schemaVersion: Int = 1,
    @SerialName("updated_at")
    val updatedAt: String = "",
    val plugins: List<MarketPluginEntry> = emptyList()
)

/** 安装进度阶段。 */
enum class InstallPhase {
    IDLE,
    DOWNLOADING,
    VERIFYING,
    INSTALLING,
    LOADING,
    DONE,
    FAILED
}

/** 市场列表项相对本地的安装状态。 */
enum class MarketInstallStatus {
    /** 本地未注册该 id。 */
    NOT_INSTALLED,

    /** 本地已有同 id，版本相同或无法比较。 */
    INSTALLED,

    /** 本地已有同 id，市场版本更新。 */
    UPDATE_AVAILABLE
}

/** 安装管线结果。 */
sealed class PluginInstallResult {
    data class Success(
        val pluginId: String,
        val apkPath: String,
        val loaded: Boolean,
        val message: String = ""
    ) : PluginInstallResult()

    data class Failure(
        val pluginId: String?,
        val reason: String
    ) : PluginInstallResult()
}

/** 市场配置（索引 URL 等）。 */
data class MarketConfig(
    val catalogUrl: String = MarketDefaults.DEFAULT_CATALOG_URL,
    /** 为 true 时远程失败回退内置 sample。 */
    val fallbackToSample: Boolean = true
)

/**
 * 默认常量与覆盖点。
 *
 * 默认索引为仓库内 sample JSON（可离线演示）；
 * 生产环境可通过 [MarketPreferences] 覆盖 URL。
 */
object MarketDefaults {
    /**
     * 默认市场索引 URL。
     * 指向本仓库 docs 下的 sample 索引，便于演示；可在设置中覆盖。
     */
    const val DEFAULT_CATALOG_URL =
        "https://raw.githubusercontent.com/Cuering/LanXin-Android/main/docs/plugin-market-index.sample.json"

    /** DataStore 键：市场索引 URL。 */
    const val PREF_CATALOG_URL = "plugin_market_catalog_url"
}
