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
 * 仓内内置 ASR/TTS 模型（跟随 APK 打包，首次启动时提取到 filesDir）。
 *
 * 策略：
 * - **ASR**：`assets/voice/asr/` 下 sherpa-onnx 小模型，首次启动提取到
 *   `filesDir/builtin-voice/asr/`（约 12MB，已量化 int8，够日常离线听写）
 * - **TTS**：体积过大（>30MB），不打包；用户从设置页一键下载或走系统 TTS 回退
 *
 * 打包方式（CI 构建时预下载）：
 * ```
 * bash scripts/ci-bundle-voice-assets.sh
 * ```
 * 或手动放入 `app/src/main/assets/voice/asr/`。
 *
 * 许可：sherpa-onnx 模型采用 Apache 2.0
 * https://github.com/k2-fsa/sherpa-onnx
 */
object BuiltInVoiceAssets {

    /** assets 下 ASR 模型根（相对 AssetManager）。 */
    const val ASR_ASSET_ROOT = "voice/asr/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"

    /** TTS 不打包，留空。 */
    const val TTS_ASSET_ROOT = ""

    /** ASR 提取后相对 filesDir 的根。 */
    const val ASR_INSTALLED_ROOT_REL = "builtin-voice/asr/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"

    /** ASR 标志文件——检验是否提取完整的标记。 */
    const val ASR_MARKER = "tokens.txt"

    /** 设置页提示。 */
    const val ASR_LICENSE_HINT =
        "内置轻量 ASR 模型：sherpa-onnx-streaming-zipformer-zh-14M（量化 int8）。" +
            "许可 Apache 2.0，https://github.com/k2-fsa/sherpa-onnx"

    /** 打包的 ASR 模型在 assets 中是否存在。 */
    fun asrPackaged(am: AssetManager): Boolean {
        return runCatching {
            am.list(ASR_ASSET_ROOT)?.isNotEmpty() == true
        }.getOrDefault(false)
    }

    /** 安装后的 ASR 目录。 */
    fun asrInstalledDir(filesDir: File): File = File(filesDir, ASR_INSTALLED_ROOT_REL)

    /** ASR 是否已提取到 filesDir。 */
    fun isAsrInstalled(filesDir: File): Boolean {
        val marker = File(asrInstalledDir(filesDir), ASR_MARKER)
        return marker.isFile && marker.length() > 0L
    }

    /**
     * 解析 ASR 路径：
     * - 若已安装 → 返回 filesDir 绝对路径
     * - 若 assets 中有 → 返回 assets 逻辑路径（由调用方 ensure）
     * - 否则返回空字符串
     */
    fun resolveAsrIfPackaged(context: Context): String {
        val filesDir = context.filesDir
        if (isAsrInstalled(filesDir)) return asrInstalledDir(filesDir).absolutePath
        val am = context.assets
        if (asrPackaged(am)) return "asset://$ASR_ASSET_ROOT"
        return ""
    }

    /**
     * 将 assets 中 ASR 模型递归提取到 filesDir。
     * @return 提取后的 ASR 目录绝对路径；若 assets 无模型则返回 null
     */
    fun ensureAsrInstalled(context: Context): String? {
        val filesDir = context.filesDir
        val destDir = asrInstalledDir(filesDir)
        val marker = File(destDir, ASR_MARKER)
        if (marker.isFile && marker.length() > 0L) {
            return destDir.absolutePath
        }
        return runCatching {
            val am = context.assets
            if (!asrPackaged(am)) return@runCatching null
            if (destDir.exists()) destDir.deleteRecursively()
            copyAssetDir(am, ASR_ASSET_ROOT, destDir)
            if (marker.isFile && marker.length() > 0L) destDir.absolutePath else null
        }.getOrNull()
    }

    private fun copyAssetDir(am: AssetManager, assetPath: String, destDir: File) {
        destDir.mkdirs()
        val children = am.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            copyAssetFile(am, assetPath, File(destDir, File(assetPath).name))
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
