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

import java.io.File

/**
 * Debug 专用：设备上资源路径解析（相对 [filesDir]）。
 *
 * 用户配置为空时优先级：
 * 1. **仓内官方 Sample** [BuiltInLive2dAssets]（APK assets → filesDir/builtin-live2d）
 * 2. **开源 debug 包** [DebugOpenSourcePaths]（`debug-assets/`，脚本拉取）
 * 3. **妹居参考旁路** `meiju-ref/`（仅本机 adb push，**禁止入库**）
 *
 * Live2D 内置 Sample 在 debug/release 均可默认选用（官方 Sample Terms）。
 * ASR/TTS 大权重仍仅 debug 旁路；路径配置始终是一等公民。
 */
object MeijuDebugPaths {

    /** 妹居参考根目录名（位于 Context.filesDir 下）。 */
    const val ROOT_DIR = "meiju-ref"

    /** Live2D 模型目录（相对 filesDir）。 */
    const val L2D_DIR = "$ROOT_DIR/L2D/high"

    /**
     * 妹居 Live2D model3 相对路径（**仅本机**；正式测试用 Mao）。
     * 对应妹居 APK：`assets/assets/L2D/high/棕发1.model3.json`
     */
    const val L2D_MODEL3_REL = "$L2D_DIR/棕发1.model3.json"

    /** TTS 模型目录（相对 filesDir）。对应 `assets/tts_models/`。 */
    const val TTS_MODEL_DIR = "$ROOT_DIR/tts_models"

    /**
     * TTS 参考音（相对 filesDir）。对应 `assets/assets/tts/yuki-1/high.wav`。
     * 仅作 debug 默认常量。
     */
    const val TTS_REFERENCE_AUDIO_REL = "$ROOT_DIR/tts/yuki-1/high.wav"

    /** ASR / sherpa 侧模型目录（可选）。 */
    const val ASR_MODEL_DIR = "$ROOT_DIR/asr_models"

    /** native so 旁路目录（可选，debug 动态加载用）。 */
    const val NATIVE_LIBS_DIR = "$ROOT_DIR/libs"

    /**
     * 资源来源标签（设置页文案）。
     */
    enum class ResourceSource {
        /** 用户显式配置的自定义路径。 */
        CUSTOM,

        /** 仓内官方 Live2D Sample（Niziiro Mao）。 */
        BUILTIN_SAMPLE,

        /** Debug 开源包（filesDir/debug-assets，脚本拉取）。 */
        DEBUG_OPEN_SOURCE,

        /** Debug 构建且命中 meiju-ref 约定目录（仅本地）。 */
        DEBUG_MEIJU_REF,

        /** 无可用资源 / 占位。 */
        PLACEHOLDER
    }

    fun filesRoot(filesDir: File): File = File(filesDir, ROOT_DIR)

    fun live2dModelFile(filesDir: File): File = File(filesDir, L2D_MODEL3_REL)

    fun ttsModelDirFile(filesDir: File): File = File(filesDir, TTS_MODEL_DIR)

    fun ttsReferenceAudioFile(filesDir: File): File = File(filesDir, TTS_REFERENCE_AUDIO_REL)

    fun asrModelDirFile(filesDir: File): File = File(filesDir, ASR_MODEL_DIR)

    fun live2dExists(filesDir: File): Boolean =
        BuiltInLive2dAssets.isInstalled(filesDir) ||
            DebugOpenSourcePaths.live2dModelFile(filesDir).isFile ||
            live2dModelFile(filesDir).isFile

    fun ttsModelsExist(filesDir: File): Boolean {
        if (DebugOpenSourcePaths.isModelDirReady(DebugOpenSourcePaths.ttsModelDir(filesDir))) {
            return true
        }
        val dir = ttsModelDirFile(filesDir)
        return dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)
    }

    fun ttsReferenceExists(filesDir: File): Boolean = ttsReferenceAudioFile(filesDir).isFile

    /**
     * Live2D：用户配置优先 → 已安装内置 Sample → debug-assets → 妹居旁路 →
     * （[preferBuiltinLogical]）仓内 Sample 逻辑路径。
     *
     * @param preferBuiltinLogical 无落盘文件时是否返回 [BuiltInLive2dAssets.LOGICAL_PATH]
     * @param allowMeijuRef debug 才应 true
     */
    fun resolveLive2dIfPresent(
        filesDir: File,
        configured: String,
        preferBuiltinLogical: Boolean = true,
        allowMeijuRef: Boolean = true
    ): String {
        if (configured.isNotBlank()) return configured.trim()
        if (BuiltInLive2dAssets.isInstalled(filesDir)) {
            return BuiltInLive2dAssets.installedModelFile(filesDir).absolutePath
        }
        val open = DebugOpenSourcePaths.live2dModelFile(filesDir)
        if (open.isFile) return open.absolutePath
        if (allowMeijuRef) {
            val f = live2dModelFile(filesDir)
            if (f.isFile) return f.absolutePath
        }
        return if (preferBuiltinLogical) BuiltInLive2dAssets.LOGICAL_PATH else ""
    }

    fun resolveTtsModelDirIfPresent(filesDir: File, configured: String): String {
        if (configured.isNotBlank()) return configured.trim()
        val open = DebugOpenSourcePaths.ttsModelDir(filesDir)
        if (DebugOpenSourcePaths.isModelDirReady(open)) return open.absolutePath
        val d = ttsModelDirFile(filesDir)
        return if (d.isDirectory) d.absolutePath else ""
    }

    fun resolveTtsReferenceIfPresent(filesDir: File, configured: String): String {
        if (configured.isNotBlank()) return configured.trim()
        val f = ttsReferenceAudioFile(filesDir)
        return if (f.isFile) f.absolutePath else ""
    }

    fun resolveAsrIfPresent(filesDir: File, configured: String): String {
        if (configured.isNotBlank()) return configured.trim()
        val open = DebugOpenSourcePaths.asrModelDir(filesDir)
        if (DebugOpenSourcePaths.isModelDirReady(open)) return open.absolutePath
        val d = asrModelDirFile(filesDir)
        return if (d.isDirectory) d.absolutePath else ""
    }

    /**
     * 判定当前有效路径的展示来源。
     */
    fun classifySource(
        isDebug: Boolean,
        configured: String,
        resolved: String
    ): ResourceSource {
        if (resolved.isBlank()) return ResourceSource.PLACEHOLDER
        if (configured.isNotBlank()) return ResourceSource.CUSTOM
        if (BuiltInLive2dAssets.pathLooksBuiltin(resolved)) {
            return ResourceSource.BUILTIN_SAMPLE
        }
        if (DebugOpenSourcePaths.pathLooksOpenSource(resolved)) {
            return ResourceSource.DEBUG_OPEN_SOURCE
        }
        if (!isDebug) return ResourceSource.PLACEHOLDER
        return when {
            resolved.contains(ROOT_DIR) -> ResourceSource.DEBUG_MEIJU_REF
            else -> ResourceSource.PLACEHOLDER
        }
    }

    fun sourceLabel(source: ResourceSource): String = when (source) {
        ResourceSource.CUSTOM -> "当前：自定义"
        ResourceSource.BUILTIN_SAMPLE -> "当前：内置示例（Mao）"
        ResourceSource.DEBUG_OPEN_SOURCE -> "当前：Debug 开源包（可测）"
        ResourceSource.DEBUG_MEIJU_REF -> "当前：Debug 妹居参考（仅本地）"
        ResourceSource.PLACEHOLDER -> "当前：占位 / 未配置"
    }
}
