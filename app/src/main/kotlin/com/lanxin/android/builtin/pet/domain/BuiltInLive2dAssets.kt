/*
 * Copyright 2025 LanXin Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lanxin.android.builtin.pet.domain

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.FileOutputStream

/**
 * 仓内官方 Live2D Sample **Niziiro Mao**（CubismWebSamples）。
 *
 * - APK assets：`pet/live2d/Mao/` 下全部文件
 * - 运行时：首次拷到 [Context.getFilesDir]/`builtin-live2d/Mao/`，供 WebView `file://` 读
 * - 许可：https://www.live2d.com/en/learn/sample/model-terms
 *
 * **禁止**用妹居 / 商业模型覆盖此目录。
 */
object BuiltInLive2dAssets {

    /** assets 下模型根（相对 AssetManager）。 */
    const val ASSET_ROOT = "pet/live2d/Mao"

    const val MODEL3_NAME = "Mao.model3.json"

    /** assets 内 model3 路径。 */
    const val MODEL3_ASSET = "$ASSET_ROOT/$MODEL3_NAME"

    /**
     * 逻辑路径（未落盘前的就绪标记；[PetPathReadiness] 视为已就绪）。
     * 运行时优先 [ensureInstalled] 后的绝对路径。
     */
    const val LOGICAL_PATH = "asset://$MODEL3_ASSET"

    /** 相对 filesDir 的安装根。 */
    const val INSTALLED_ROOT_REL = "builtin-live2d/Mao"

    /** 相对 filesDir 的 model3。 */
    const val INSTALLED_MODEL3_REL = "$INSTALLED_ROOT_REL/$MODEL3_NAME"

    /** 设置页短说明。 */
    const val LICENSE_HINT =
        "内置官方 Sample：Niziiro Mao（CubismWebSamples）。" +
            "许可 Sample Data Terms：https://www.live2d.com/en/learn/sample/model-terms 。" +
            "仅用于 SDK 集成/打磨，非妹居商业资源。"

    fun installedRoot(filesDir: File): File = File(filesDir, INSTALLED_ROOT_REL)

    fun installedModelFile(filesDir: File): File = File(filesDir, INSTALLED_MODEL3_REL)

    fun isInstalled(filesDir: File): Boolean {
        val f = installedModelFile(filesDir)
        return f.isFile && f.length() > 0L
    }

    fun pathLooksBuiltin(resolved: String): Boolean {
        if (resolved.isBlank()) return false
        return resolved == LOGICAL_PATH ||
            resolved.startsWith("asset://$ASSET_ROOT") ||
            resolved.contains("/$INSTALLED_ROOT_REL/") ||
            resolved.contains("\\$INSTALLED_ROOT_REL\\") ||
            resolved.contains("builtin-live2d/Mao") ||
            resolved.contains("pet/live2d/Mao")
    }

    /**
     * 若已安装返回绝对路径；否则返回 [LOGICAL_PATH]（表示仓内有 Sample，待 ensure）。
     */
    fun resolveIfPresent(filesDir: File, assumePackaged: Boolean = true): String {
        if (isInstalled(filesDir)) return installedModelFile(filesDir).absolutePath
        return if (assumePackaged) LOGICAL_PATH else ""
    }

    /**
     * 将 assets 中 Mao 递归拷到 filesDir（已存在且 model3 非空则跳过）。
     * @return 安装后的 model3 绝对路径；assets 缺失时 null
     */
    fun ensureInstalled(context: Context): String? {
        val filesDir = context.filesDir
        val destModel = installedModelFile(filesDir)
        if (destModel.isFile && destModel.length() > 0L) {
            return destModel.absolutePath
        }
        return runCatching {
            val am = context.assets
            if (!assetFileExists(am, MODEL3_ASSET)) return@runCatching null
            val destRoot = installedRoot(filesDir)
            if (destRoot.exists()) {
                destRoot.deleteRecursively()
            }
            copyAssetDir(am, ASSET_ROOT, destRoot)
            if (destModel.isFile && destModel.length() > 0L) {
                destModel.absolutePath
            } else {
                null
            }
        }.getOrNull()
    }

    fun packagedModel3AssetPath(): String = MODEL3_ASSET

    internal fun assetFileExists(am: AssetManager, path: String): Boolean {
        return runCatching {
            am.open(path).use { true }
        }.getOrDefault(false)
    }

    private fun copyAssetDir(am: AssetManager, assetPath: String, destDir: File) {
        destDir.mkdirs()
        val children = am.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            copyAssetFile(am, assetPath, destDir)
            return
        }
        for (name in children) {
            val childAsset = if (assetPath.isEmpty()) name else "$assetPath/$name"
            val sub = am.list(childAsset)
            if (sub != null && sub.isNotEmpty()) {
                copyAssetDir(am, childAsset, File(destDir, name))
            } else {
                copyAssetFile(am, childAsset, File(destDir, name))
            }
        }
    }

    private fun copyAssetFile(am: AssetManager, assetPath: String, destFile: File) {
        destFile.parentFile?.mkdirs()
        am.open(assetPath).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }
}
