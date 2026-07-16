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
 * Debug 专用：妹居参考资源在设备上的**约定相对路径**（相对 [filesDir]）。
 *
 * - **绝不**把 so / moc3 / mnn / wav 打进 git；仅本地 / adb push / 从 APK 抽取。
 * - Release 不得依赖这些默认；路径配置始终是一等公民，换模型不改状态机。
 * - 妹居文件名只作 debug 常量，业务层只读配置路径。
 */
object MeijuDebugPaths {

    /** 设备侧根目录名（位于 Context.filesDir 下）。 */
    const val ROOT_DIR = "meiju-ref"

    /** Live2D 模型目录（相对 filesDir）。 */
    const val L2D_DIR = "$ROOT_DIR/L2D/high"

    /**
     * Live2D model3 相对路径（debug 默认常量；正式版改设置项即可）。
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
     * 资源来源标签（设置页文案，勿宣传为可分发）。
     */
    enum class ResourceSource {
        /** 用户显式配置的自定义路径。 */
        CUSTOM,

        /** Debug 构建且命中 meiju-ref 约定目录。 */
        DEBUG_MEIJU_REF,

        /** 无可用资源 / 占位。 */
        PLACEHOLDER
    }

    fun filesRoot(filesDir: File): File = File(filesDir, ROOT_DIR)

    fun live2dModelFile(filesDir: File): File = File(filesDir, L2D_MODEL3_REL)

    fun ttsModelDirFile(filesDir: File): File = File(filesDir, TTS_MODEL_DIR)

    fun ttsReferenceAudioFile(filesDir: File): File = File(filesDir, TTS_REFERENCE_AUDIO_REL)

    fun asrModelDirFile(filesDir: File): File = File(filesDir, ASR_MODEL_DIR)

    fun live2dExists(filesDir: File): Boolean = live2dModelFile(filesDir).isFile

    fun ttsModelsExist(filesDir: File): Boolean {
        val dir = ttsModelDirFile(filesDir)
        return dir.isDirectory && (dir.listFiles()?.isNotEmpty() == true)
    }

    fun ttsReferenceExists(filesDir: File): Boolean = ttsReferenceAudioFile(filesDir).isFile

    /**
     * Debug 时：若用户未配置路径且设备上存在约定文件，则返回绝对路径；否则返回空。
     */
    fun resolveLive2dIfPresent(filesDir: File, configured: String): String {
        if (configured.isNotBlank()) return configured.trim()
        val f = live2dModelFile(filesDir)
        return if (f.isFile) f.absolutePath else ""
    }

    fun resolveTtsModelDirIfPresent(filesDir: File, configured: String): String {
        if (configured.isNotBlank()) return configured.trim()
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
        val d = asrModelDirFile(filesDir)
        return if (d.isDirectory) d.absolutePath else ""
    }

    /**
     * 判定当前有效路径的展示来源。
     *
     * @param isDebug BuildConfig.DEBUG
     * @param configured 用户 DataStore 中的原始值（未 resolve）
     * @param resolved 解析后路径
     * @param meijuMarker 若 resolved 落在 meiju-ref 下则为 debug 参考
     */
    fun classifySource(
        isDebug: Boolean,
        configured: String,
        resolved: String,
        meijuMarker: String = ROOT_DIR
    ): ResourceSource {
        if (resolved.isBlank()) return ResourceSource.PLACEHOLDER
        if (configured.isNotBlank()) {
            // 用户手填：即使路径碰巧在 meiju-ref 下也标自定义
            return ResourceSource.CUSTOM
        }
        return if (isDebug && resolved.contains(meijuMarker)) {
            ResourceSource.DEBUG_MEIJU_REF
        } else {
            ResourceSource.PLACEHOLDER
        }
    }

    fun sourceLabel(source: ResourceSource): String = when (source) {
        ResourceSource.CUSTOM -> "当前：自定义"
        ResourceSource.DEBUG_MEIJU_REF -> "当前：Debug 妹居参考（仅本地）"
        ResourceSource.PLACEHOLDER -> "当前：占位 / 未配置"
    }
}
