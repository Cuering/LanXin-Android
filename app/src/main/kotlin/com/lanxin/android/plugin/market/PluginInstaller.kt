package com.lanxin.android.plugin.market

import com.lanxin.android.core.updater.VersionComparator
import com.lanxin.android.plugin.PluginCatalog
import com.lanxin.android.plugin.dynamic.PluginLoadResult
import com.lanxin.android.plugin.dynamic.PluginPackagePaths
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 市场安装进度回调。
 */
data class InstallProgress(
    val phase: InstallPhase,
    val progress: Float = 0f,
    val message: String = ""
)

/**
 * 插件安装管线：下载 → 校验 → 落入 plugin-packages → loadDynamicPlugin。
 *
 * 失败不抛到 UI 层崩溃；返回 [PluginInstallResult.Failure]。
 */
interface PluginInstaller {

    suspend fun install(
        entry: MarketPluginEntry,
        onProgress: (InstallProgress) -> Unit = {}
    ): PluginInstallResult
}

@Singleton
class DefaultPluginInstaller @Inject constructor(
    private val fetcher: MarketHttpFetcher,
    private val catalog: PluginCatalog
) : PluginInstaller {

    override suspend fun install(
        entry: MarketPluginEntry,
        onProgress: (InstallProgress) -> Unit
    ): PluginInstallResult = withContext(Dispatchers.IO) {
        val id = entry.id.trim()
        if (id.isEmpty()) {
            return@withContext PluginInstallResult.Failure(null, "插件 id 为空")
        }
        if (entry.downloadUrl.isBlank()) {
            return@withContext PluginInstallResult.Failure(id, "downloadUrl 为空")
        }

        val packagesDir = catalog.packagesDirectory()
        PluginPackagePaths.ensurePackagesDir(packagesDir.parentFile ?: packagesDir)
        val safeName = sanitizeFileName(id) + ".apk"
        val destFile = File(packagesDir, safeName)
        val tmpFile = File(packagesDir, "$safeName.downloading")

        try {
            onProgress(InstallProgress(InstallPhase.DOWNLOADING, 0f, "下载中…"))
            if (tmpFile.exists()) {
                runCatching { tmpFile.delete() }
            }
            val download = fetcher.downloadToFile(entry.downloadUrl, tmpFile) { p ->
                onProgress(InstallProgress(InstallPhase.DOWNLOADING, p, "下载中…"))
            }
            download.getOrElse { e ->
                runCatching { tmpFile.delete() }
                return@withContext PluginInstallResult.Failure(
                    id,
                    "下载失败：${e.message ?: e.javaClass.simpleName}"
                )
            }

            onProgress(InstallProgress(InstallPhase.VERIFYING, 0f, "校验中…"))
            val verify = PluginPackageVerifier.verify(
                file = tmpFile,
                expectedSize = entry.size,
                expectedSha256 = entry.checksum
            )
            verify.getOrElse { e ->
                runCatching { tmpFile.delete() }
                return@withContext PluginInstallResult.Failure(
                    id,
                    "校验失败：${e.message ?: e.javaClass.simpleName}"
                )
            }

            onProgress(InstallProgress(InstallPhase.INSTALLING, 0f, "安装到本地…"))
            if (destFile.exists()) {
                runCatching { destFile.delete() }
            }
            if (!tmpFile.renameTo(destFile)) {
                tmpFile.copyTo(destFile, overwrite = true)
                runCatching { tmpFile.delete() }
            }

            onProgress(InstallProgress(InstallPhase.LOADING, 0f, "加载插件…"))
            val loadResult = catalog.loadDynamicPlugin(destFile)
            when (loadResult) {
                is PluginLoadResult.Success -> {
                    onProgress(
                        InstallProgress(InstallPhase.DONE, 1f, "安装成功")
                    )
                    PluginInstallResult.Success(
                        pluginId = loadResult.record.id,
                        apkPath = destFile.absolutePath,
                        loaded = loadResult.loaded,
                        message = if (loadResult.loaded) {
                            "已安装并加载 ${loadResult.record.id}"
                        } else {
                            "已安装 ${loadResult.record.id}（当前为停用状态，未 onLoad）"
                        }
                    )
                }
                is PluginLoadResult.Failure -> {
                    // 包已落入目录，加载失败仍返回可读错误（可在管理页重扫）
                    onProgress(
                        InstallProgress(
                            InstallPhase.FAILED,
                            0f,
                            loadResult.reason
                        )
                    )
                    PluginInstallResult.Failure(
                        pluginId = loadResult.pluginId ?: id,
                        reason = "包已下载但加载失败：${loadResult.reason}"
                    )
                }
            }
        } catch (e: Exception) {
            runCatching { tmpFile.delete() }
            onProgress(
                InstallProgress(
                    InstallPhase.FAILED,
                    0f,
                    e.message ?: "未知错误"
                )
            )
            PluginInstallResult.Failure(
                id,
                "安装异常：${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    companion object {
        fun sanitizeFileName(id: String): String {
            val cleaned = id.map { ch ->
                if (ch.isLetterOrDigit() || ch == '.' || ch == '-' || ch == '_') ch else '_'
            }.joinToString("")
            return cleaned.ifBlank { "plugin" }
        }

        /**
         * 比较本地已安装版本与市场版本，判断安装状态。
         *
         * @param localVersion 本地版本；null 表示未安装
         */
        fun resolveInstallStatus(
            localVersion: String?,
            marketVersion: String
        ): MarketInstallStatus {
            if (localVersion.isNullOrBlank()) return MarketInstallStatus.NOT_INSTALLED
            return if (VersionComparator.isNewer(marketVersion, localVersion)) {
                MarketInstallStatus.UPDATE_AVAILABLE
            } else {
                MarketInstallStatus.INSTALLED
            }
        }
    }
}
