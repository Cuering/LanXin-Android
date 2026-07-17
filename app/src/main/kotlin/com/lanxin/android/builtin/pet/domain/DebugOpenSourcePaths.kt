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
 * Debug 开源资源约定路径（相对 [Context.filesDir] 或开发机 `debug-assets/`）。
 *
 * **主路径**：App 内设置页一键下载到 `filesDir/debug-assets/`（见 [DebugAssetDownloader]）。
 * **可选**：开发者本机脚本 `bash scripts/fetch-debug-assets.sh` 后 adb push。
 *
 * **禁止**在 AstrBot 服务器上下载模型作为交付；大权重不进 git。
 *
 * @see docs/debug-assets.md
 */
object DebugOpenSourcePaths {

    const val ROOT_DIR = "debug-assets"

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
        "资源缺失：请在设置页使用「一键下载」拉取到本机 filesDir/debug-assets/；" +
            "可选开发者机 bash scripts/fetch-debug-assets.sh 后 adb push。" +
            "详见 docs/debug-assets.md。勿在 AstrBot 服务器拉模型。"

    /** 本地脑默认选型说明（路径键见 LocalInferencePreferences）。 */
    const val LOCAL_LLM_DEFAULT_HINT =
        "默认本地脑：Qwen2.5-1.5B-Instruct（MNN 量化）。" +
            "配置键 local_inference_model_path；放置 {filesDir}/models/local-llm/light/。" +
            "本阶段可不下载权重。详见 docs/local-inference.md。"

    fun live2dModelFile(filesDir: File): File {
        val primary = File(filesDir, LIVE2D_MAO_MODEL3_REL)
        if (primary.isFile) return primary
        return File(filesDir, LIVE2D_MAO_MODEL3_ALT_REL)
    }

    fun asrModelDir(filesDir: File): File {
        val zip = File(filesDir, ASR_ZIPFORMER_14M_REL)
        if (isModelDirReady(zip)) return zip
        val para = File(filesDir, ASR_PARAFORMER_SMALL_REL)
        if (isModelDirReady(para)) return para
        // 任取 asr 下第一个非空子目录
        val root = File(filesDir, "$ROOT_DIR/asr")
        val kids = root.listFiles()?.filter { isModelDirReady(it) }
        if (!kids.isNullOrEmpty()) return kids.first()
        return zip
    }

    fun ttsModelDir(filesDir: File): File {
        val matcha = File(filesDir, TTS_MATCHA_BAKER_REL)
        if (isModelDirReady(matcha)) return matcha
        val root = File(filesDir, "$ROOT_DIR/tts")
        val kids = root.listFiles()?.filter { isModelDirReady(it) }
        if (!kids.isNullOrEmpty()) return kids.first()
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
            resolved.contains("live2d/Mao") ||
            resolved.contains("live2d/mao") ||
            resolved.contains("sherpa-onnx-") ||
            resolved.contains("matcha-icefall")
    }
}
