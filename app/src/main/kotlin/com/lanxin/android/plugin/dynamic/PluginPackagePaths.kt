package com.lanxin.android.plugin.dynamic

import java.io.File

/**
 * 动态插件相关路径约定（与 PluginContext.filesDir 的 plugins/ 分离）。
 */
object PluginPackagePaths {

    const val PACKAGES_DIR_NAME = "plugin-packages"
    const val STATE_FILE_NAME = "plugin-state.json"
    const val SIGNATURE_CONFIG_FILE_NAME = "plugin-signature.json"
    const val MANIFEST_ENTRY = "assets/lanxin-plugin.json"

    fun packagesDir(filesDir: File): File = File(filesDir, PACKAGES_DIR_NAME)

    fun ensurePackagesDir(filesDir: File): File =
        packagesDir(filesDir).also { it.mkdirs() }

    fun stateFile(filesDir: File): File = File(filesDir, STATE_FILE_NAME)

    fun signatureConfigFile(filesDir: File): File =
        File(filesDir, SIGNATURE_CONFIG_FILE_NAME)
}
