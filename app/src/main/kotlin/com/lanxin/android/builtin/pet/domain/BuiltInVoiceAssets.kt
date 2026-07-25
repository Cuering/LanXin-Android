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
 * - **ASR**：`assets/voice/asr/` 下 sherpa-onnx 小模型 → `filesDir/builtin-voice/asr/`
 * - **TTS（默认 VITS）**：`assets/voice/tts/vits-melo-tts-zh_en/`
 *   → `filesDir/builtin-voice/tts/vits-melo-tts-zh_en/`
 *   构建期由 `BUNDLE_TTS=1 bash scripts/ci-bundle-voice-assets.sh` 写入 assets（**不进 git**）
 * - 兼容旧包：若 assets 仍是 matcha，也可提取并识别
 *
 * 许可：sherpa-onnx 模型 Apache 2.0
 * https://github.com/k2-fsa/sherpa-onnx
 */
object BuiltInVoiceAssets {

    /** assets 下 ASR 模型根（相对 AssetManager）。 */
    const val ASR_ASSET_ROOT = "voice/asr/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"

    /** 默认内置 TTS：VITS Melo 中英（更自然）。 */
    const val TTS_ASSET_ROOT = "voice/tts/vits-melo-tts-zh_en"

    /** 兼容旧 APK / 半迁移：Matcha baker。 */
    const val TTS_ASSET_ROOT_LEGACY_MATCHA = "voice/tts/matcha-icefall-zh-baker"

    /** ASR 提取后相对 filesDir 的根。 */
    const val ASR_INSTALLED_ROOT_REL =
        "builtin-voice/asr/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"

    /** TTS 提取后相对 filesDir 的根（VITS）。 */
    const val TTS_INSTALLED_ROOT_REL = "builtin-voice/tts/vits-melo-tts-zh_en"

    /** 兼容旧提取路径。 */
    const val TTS_INSTALLED_ROOT_LEGACY_MATCHA = "builtin-voice/tts/matcha-icefall-zh-baker"

    /** ASR 标志文件。 */
    const val ASR_MARKER = "tokens.txt"

    /** VITS 完整性：tokens + 至少一个非 vocoder 的 onnx。 */
    const val TTS_TOKENS_MARKER = "tokens.txt"

    const val ASR_LICENSE_HINT =
        "内置轻量 ASR 模型：sherpa-onnx-streaming-zipformer-zh-14M（量化 int8）。" +
            "许可 Apache 2.0，https://github.com/k2-fsa/sherpa-onnx"

    const val TTS_LICENSE_HINT =
        "内置 TTS：vits-melo-tts-zh_en（VITS，随 APK 打包，首次启动提取）。" +
            "许可 Apache 2.0，https://github.com/k2-fsa/sherpa-onnx"

    fun asrPackaged(am: AssetManager): Boolean {
        return runCatching {
            am.list(ASR_ASSET_ROOT)?.isNotEmpty() == true
        }.getOrDefault(false)
    }

    /** 优先 VITS assets；否则兼容旧 Matcha。 */
    fun resolveTtsAssetRoot(am: AssetManager): String? {
        if (ttsPackagedAt(am, TTS_ASSET_ROOT)) return TTS_ASSET_ROOT
        if (ttsPackagedAt(am, TTS_ASSET_ROOT_LEGACY_MATCHA)) return TTS_ASSET_ROOT_LEGACY_MATCHA
        return null
    }

    fun ttsPackaged(am: AssetManager): Boolean = resolveTtsAssetRoot(am) != null

    private fun ttsPackagedAt(am: AssetManager, root: String): Boolean {
        return runCatching {
            am.open("$root/$TTS_TOKENS_MARKER").close()
            // 至少一个 onnx
            val kids = am.list(root) ?: return@runCatching false
            kids.any { it.endsWith(".onnx", ignoreCase = true) }
        }.getOrDefault(false)
    }

    fun asrInstalledDir(filesDir: File): File = File(filesDir, ASR_INSTALLED_ROOT_REL)

    fun ttsInstalledDir(filesDir: File): File = File(filesDir, TTS_INSTALLED_ROOT_REL)

    fun ttsInstalledDirLegacyMatcha(filesDir: File): File =
        File(filesDir, TTS_INSTALLED_ROOT_LEGACY_MATCHA)

    fun isAsrInstalled(filesDir: File): Boolean {
        val marker = File(asrInstalledDir(filesDir), ASR_MARKER)
        return marker.isFile && marker.length() > 0L
    }

    fun isTtsInstalled(filesDir: File): Boolean {
        val vits = ttsInstalledDir(filesDir)
        if (DebugOpenSourcePaths.isTtsModelDirReady(vits)) return true
        val matcha = ttsInstalledDirLegacyMatcha(filesDir)
        return DebugOpenSourcePaths.isTtsModelDirReady(matcha)
    }

    /** 已安装目录（优先 VITS）。 */
    fun resolveInstalledTtsDir(filesDir: File): File? {
        val vits = ttsInstalledDir(filesDir)
        if (DebugOpenSourcePaths.isTtsModelDirReady(vits)) return vits
        val matcha = ttsInstalledDirLegacyMatcha(filesDir)
        if (DebugOpenSourcePaths.isTtsModelDirReady(matcha)) return matcha
        return null
    }

    fun resolveAsrIfPackaged(context: Context): String {
        val filesDir = context.filesDir
        if (isAsrInstalled(filesDir)) return asrInstalledDir(filesDir).absolutePath
        if (asrPackaged(context.assets)) return "asset://$ASR_ASSET_ROOT"
        return ""
    }

    fun resolveTtsIfPackaged(context: Context): String {
        val filesDir = context.filesDir
        resolveInstalledTtsDir(filesDir)?.let { return it.absolutePath }
        val assetRoot = resolveTtsAssetRoot(context.assets) ?: return ""
        return "asset://$assetRoot"
    }

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
     * 将 assets 中 TTS（优先 VITS Melo）提取到 filesDir。
     * 已完整安装则直接返回路径；半套会重新提取。
     */
    fun ensureTtsInstalled(context: Context): String? {
        val filesDir = context.filesDir
        resolveInstalledTtsDir(filesDir)?.let { return it.absolutePath }
        return runCatching {
            val am = context.assets
            val assetRoot = resolveTtsAssetRoot(am) ?: return@runCatching null
            val destRel = if (assetRoot.contains("matcha", ignoreCase = true)) {
                TTS_INSTALLED_ROOT_LEGACY_MATCHA
            } else {
                TTS_INSTALLED_ROOT_REL
            }
            val destDir = File(filesDir, destRel)
            if (destDir.exists()) destDir.deleteRecursively()
            copyAssetDir(am, assetRoot, destDir)
            if (DebugOpenSourcePaths.isTtsModelDirReady(destDir)) destDir.absolutePath else null
        }.getOrNull()
    }

    fun ensureAllInstalled(context: Context): Pair<String?, String?> {
        return Pair(ensureAsrInstalled(context), ensureTtsInstalled(context))
    }

    private fun copyAssetDir(am: AssetManager, assetPath: String, destDir: File) {
        destDir.mkdirs()
        val children = am.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
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
