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
 * Debug 开源资源约定路径（相对下载 baseDir，或开发机仓库 `debug-assets/`）。
 *
 * **主路径**：App 内设置页一键下载到用户可访问的 `LanXin/` 目录
 * （公共存储优先，失败回退 `Android/data/.../files/LanXin/`，见 [DebugAssetStorage]）。
 * **可选**：开发者本机脚本 `bash scripts/fetch-debug-assets.sh` 后 adb push。
 *
 * **禁止**在 AstrBot 服务器上下载模型作为交付；大权重不进 git。
 *
 * @see docs/debug-assets.md
 */
object DebugOpenSourcePaths {

    /** 用户可见的资源根目录名（手机文件管理器 → LanXin）。 */
    const val ROOT_DIR = "LanXin"

    /** 历史落盘目录名（兼容旧路径识别）。 */
    const val LEGACY_ROOT_DIR = "debug-assets"

    /** Live2D Mao model3（download-debug-live2d 默认大小写 Mao）。 */
    const val LIVE2D_MAO_MODEL3_REL = "$ROOT_DIR/live2d/Mao/Mao.model3.json"

    /** 兼容小写目录（部分脚本 / adb 布局）。 */
    const val LIVE2D_MAO_MODEL3_ALT_REL = "$ROOT_DIR/live2d/mao/Mao.model3.json"

    /** ASR 推荐：streaming zipformer zh-14M（见 docs/debug-assets.md）。 */
    const val ASR_ZIPFORMER_14M_REL =
        "$ROOT_DIR/asr/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"

    /** ASR 备选：paraformer zh-small。 */
    const val ASR_PARAFORMER_SMALL_REL =
        "$ROOT_DIR/asr/sherpa-onnx-paraformer-zh-small-2024-03-09"

    /** TTS 推荐目录名（脚本解压后）。 */
    const val TTS_MATCHA_BAKER_REL = "$ROOT_DIR/tts/matcha-icefall-zh-baker"

    /** 设置页缺失时的一键说明（App 内下载优先；脚本可选）。 */
    const val FETCH_SCRIPT_HINT =
        "资源缺失：请在设置页使用「一键下载」拉取到本机 LanXin/ 目录；" +
            "可选开发者机 bash scripts/fetch-debug-assets.sh 后 adb push。" +
            "详见 docs/debug-assets.md。勿在 AstrBot 服务器拉模型。"

    /** 本地脑默认选型说明（路径键见 LocalInferencePreferences）。 */
    const val LOCAL_LLM_DEFAULT_HINT =
        "默认本地脑：Qwen2.5-1.5B-Instruct（MNN 量化）。" +
            "配置键 local_inference_model_path；放置 {filesDir}/models/local-llm/light/。" +
            "本阶段可不下载权重。详见 docs/local-inference.md。"

    fun live2dModelFile(baseDir: File): File {
        val primary = File(baseDir, LIVE2D_MAO_MODEL3_REL)
        if (primary.isFile) return primary
        val alt = File(baseDir, LIVE2D_MAO_MODEL3_ALT_REL)
        if (alt.isFile) return alt
        // 兼容旧版 filesDir/debug-assets 落盘
        val legacy = File(baseDir, "$LEGACY_ROOT_DIR/live2d/Mao/Mao.model3.json")
        if (legacy.isFile) return legacy
        val legacyAlt = File(baseDir, "$LEGACY_ROOT_DIR/live2d/mao/Mao.model3.json")
        if (legacyAlt.isFile) return legacyAlt
        return primary
    }

    fun asrModelDir(baseDir: File): File {
        val zip = File(baseDir, ASR_ZIPFORMER_14M_REL)
        if (isModelDirReady(zip)) return zip
        val para = File(baseDir, ASR_PARAFORMER_SMALL_REL)
        if (isModelDirReady(para)) return para
        // 任取 asr 下第一个非空子目录
        for (rootName in listOf(ROOT_DIR, LEGACY_ROOT_DIR)) {
            val root = File(baseDir, "$rootName/asr")
            val kids = root.listFiles()?.filter { isModelDirReady(it) }
            if (!kids.isNullOrEmpty()) return kids.first()
        }
        return zip
    }

    fun ttsModelDir(baseDir: File): File {
        val matcha = File(baseDir, TTS_MATCHA_BAKER_REL)
        if (isModelDirReady(matcha)) return matcha
        for (rootName in listOf(ROOT_DIR, LEGACY_ROOT_DIR)) {
            val root = File(baseDir, "$rootName/tts")
            val kids = root.listFiles()?.filter { isModelDirReady(it) }
            if (!kids.isNullOrEmpty()) return kids.first()
        }
        return matcha
    }

    fun isModelDirReady(dir: File): Boolean {
        if (!dir.isDirectory) return false
        val children = dir.listFiles() ?: return false
        return children.isNotEmpty()
    }

    fun pathLooksOpenSource(resolved: String): Boolean {
        if (resolved.isBlank()) return false
        return resolved.contains("/$ROOT_DIR/") ||
            resolved.contains("\\$ROOT_DIR\\") ||
            resolved.endsWith(ROOT_DIR) ||
            resolved.contains("/$LEGACY_ROOT_DIR/") ||
            resolved.contains("\\$LEGACY_ROOT_DIR\\") ||
            resolved.endsWith(LEGACY_ROOT_DIR) ||
            resolved.contains("live2d/Mao") ||
            resolved.contains("live2d/mao") ||
            resolved.contains("sherpa-onnx-") ||
            resolved.contains("matcha-icefall")
    }
}
