package com.lanxin.android.plugin.dynamic

import java.io.File

/**
 * 扫描 [PluginPackagePaths.packagesDir] 下的插件包文件。
 */
object PluginPackageScanner {

    /**
     * 返回目录中全部疑似插件 APK（按文件名排序）。
     * 目录不存在时返回空列表，不抛异常。
     */
    fun scan(packagesDir: File): List<File> {
        if (!packagesDir.exists()) return emptyList()
        if (!packagesDir.isDirectory) return emptyList()
        return packagesDir.listFiles()
            ?.filter { PluginPackagePaths.isPluginApk(it) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

    /**
     * 扫描并解析清单；解析失败的文件进入 failures，不中断。
     */
    fun scanWithManifests(packagesDir: File): ScanReport {
        val apks = scan(packagesDir)
        val ok = mutableListOf<Pair<File, PluginManifest>>()
        val bad = mutableListOf<PluginLoadResult.Failure>()
        for (apk in apks) {
            val manifest = PluginManifestParser.parseFromApk(apk)
            if (manifest == null) {
                bad += PluginLoadResult.Failure(
                    apkPath = apk.absolutePath,
                    pluginId = null,
                    reason = "无法解析插件清单（缺少或非法 assets/lanxin-plugin.json）"
                )
            } else {
                ok += apk to manifest
            }
        }
        return ScanReport(packages = ok, failures = bad)
    }

    data class ScanReport(
        val packages: List<Pair<File, PluginManifest>>,
        val failures: List<PluginLoadResult.Failure>
    )
}
