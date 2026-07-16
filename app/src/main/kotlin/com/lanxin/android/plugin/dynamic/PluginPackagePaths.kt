package com.lanxin.android.plugin.dynamic

import java.io.File

/**
 * 动态插件相关路径约定。
 *
 * - 包目录与 [com.lanxin.android.plugin.PluginContext.filesDir]（`plugins/<id>/`）分离。
 */
object PluginPackagePaths {

    /** 外部 .apk 插件包根目录名（相对 filesDir）。 */
    const val PACKAGES_DIR_NAME = "plugin-packages"

    /** enable/disable 状态文件名（相对 filesDir）。 */
    const val STATE_FILE_NAME = "plugin-state.json"

    /** APK 内清单 entry 路径。 */
    const val MANIFEST_ENTRY = "assets/lanxin-plugin.json"

    /** 备用清单 entry（部分打包工具可能去掉 assets 前缀不一致时的兼容位）。 */
    const val MANIFEST_ENTRY_ALT = "lanxin-plugin.json"

    fun packagesDir(filesDir: File): File =
        File(filesDir, PACKAGES_DIR_NAME)

    fun stateFile(filesDir: File): File =
        File(filesDir, STATE_FILE_NAME)

    fun ensurePackagesDir(filesDir: File): File =
        packagesDir(filesDir).also { it.mkdirs() }

    fun isPluginApk(file: File): Boolean {
        if (!file.isFile) return false
        if (file.name.startsWith(".")) return false
        val name = file.name.lowercase()
        return name.endsWith(".apk")
    }
}
