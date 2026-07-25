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
 * - **ASR**：`assets/voice/asr/` 下 sherpa-onnx 小模型 → `filesDir/builtin-voice/asr/`（约 12MB）
 * - **TTS**：`assets/voice/tts/matcha-icefall-zh-baker/`（含 vocoder，约 60–80MB）
 *   → `filesDir/builtin-voice/tts/matcha-icefall-zh-baker/`
 *   构建期由 `BUNDLE_TTS=1 bash scripts/ci-bundle-voice-assets.sh` 写入 assets（**不进 git**）
 *
 * 许可：sherpa-onnx 模型 Apache 2.0
 * https://github.com/k2-fsa/sherpa-onnx
 */
object BuiltInVoiceAssets {

    /** assets 下 ASR 模型根（相对 AssetManager）。 */
    const val ASR_ASSET_ROOT = "voice/asr/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"

    /** assets 下 TTS Matcha 模型根（含 vocos vocoder）。 */
    const val TTS_ASSET_ROOT = "voice/tts/matcha-icefall-zh-baker"

    /** ASR 提取后相对 filesDir 的根。 */
    const val ASR_INSTALLED_ROOT_REL = "builtin-voice/asr/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"

    /** TTS 提取后相对 filesDir 的根。 */
    const val TTS_INSTALLED_ROOT_REL = "builtin-voice/tts/matcha-icefall-zh-baker"

    /** ASR 标志文件。 */
    const val ASR_MARKER = "tokens.txt"

    /** TTS 完整性标志：Matcha 必须有 vocoder 才算可真合成。 */
    const val TTS_VOCODER_MARKER = "vocos-22khz-univ.onnx"

    const val ASR_LICENSE_HINT =
        "内置轻量 ASR 模型：sherpa-onnx-streaming-zipformer-zh-14M（量化 int8）。" +
            "许可 Apache 2.0，https://github.com/k2-fsa/sherpa-onnx"

    const val TTS_LICENSE_HINT =
        "内置 TTS：matcha-icefall-zh-baker + vocos-22khz-univ（随 APK 打包，首次启动提取）。" +
            "许可 Apache 2.0，https://github.com/k2-fsa/sherpa-onnx"

    fun asrPackaged(am: AssetManager): Boolean {
        return runCatching {
            am.list(ASR_ASSET_ROOT)?.isNotEmpty() == true
        }.getOrDefault(false)
    }

    fun ttsPackaged(am: AssetManager): Boolean {
        return runCatching {
            // 必须能打开 vocoder，避免半套 assets 误判
            am.open("$TTS_ASSET_ROOT/$TTS_VOCODER_MARKER").close()
            true
        }.getOrDefault(false)
    }

    fun asrInstalledDir(filesDir: File): File = File(filesDir, ASR_INSTALLED_ROOT_REL)

    fun ttsInstalledDir(filesDir: File): File = File(filesDir, TTS_INSTALLED_ROOT_REL)

    fun isAsrInstalled(filesDir: File): Boolean {
        val marker = File(asrInstalledDir(filesDir), ASR_MARKER)
        return marker.isFile && marker.length() > 0L
    }

    fun isTtsInstalled(filesDir: File): Boolean {
        val dir = ttsInstalledDir(filesDir)
        return DebugOpenSourcePaths.isTtsModelDirReady(dir)
    }

    fun resolveAsrIfPackaged(context: Context): String {
        val filesDir = context.filesDir
        if (isAsrInstalled(filesDir)) return asrInstalledDir(filesDir).absolutePath
        if (asrPackaged(context.assets)) return "asset://$ASR_ASSET_ROOT"
        return ""
    }

    fun resolveTtsIfPackaged(context: Context): String {
        val filesDir = context.filesDir
        if (isTtsInstalled(filesDir)) return ttsInstalledDir(filesDir).absolutePath
        if (ttsPackaged(context.assets)) return "asset://$TTS_ASSET_ROOT"
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

    /**
     * 将 assets 中 TTS（Matcha+vocoder）提取到 filesDir。
     * 已完整安装则直接返回路径；半套（缺 vocoder）会重新提取。
     */
    fun ensureTtsInstalled(context: Context): String? {
        val filesDir = context.filesDir
        val destDir = ttsInstalledDir(filesDir)
        if (isTtsInstalled(filesDir)) {
            return destDir.absolutePath
        }
        return runCatching {
            val am = context.assets
            if (!ttsPackaged(am)) return@runCatching null
            if (destDir.exists()) destDir.deleteRecursively()
            copyAssetDir(am, TTS_ASSET_ROOT, destDir)
            if (isTtsInstalled(filesDir)) destDir.absolutePath else null
        }.getOrNull()
    }

    /**
     * 一次确保 ASR+TTS 均已从 APK 提取（装完零下载路径）。
     * @return Pair(asrPath?, ttsPath?)
     */
    fun ensureAllInstalled(context: Context): Pair<String?, String?> {
        return Pair(ensureAsrInstalled(context), ensureTtsInstalled(context))
    }

    private fun copyAssetDir(am: AssetManager, assetPath: String, destDir: File) {
        destDir.mkdirs()
        val children = am.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            // 可能是单文件
            runCatching {
                copyAssetFile(am, assetPath, File(destDir, File(assetPath).name))
            }
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
