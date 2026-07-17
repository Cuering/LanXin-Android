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
 * 桌宠 / 语音资源路径安装与就绪校验（纯逻辑，可单测）。
 *
 * - **已就绪**：路径指向存在的文件/非空目录（引擎 so 另议，M2a 只闭环路径）
 * - **缺失**：空路径或无效 → 引导 `scripts/fetch-debug-assets.sh`
 */
object PetPathReadiness {

    enum class Kind {
        LIVE2D,
        ASR,
        TTS,
        LOCAL_LLM
    }

    data class Check(
        val kind: Kind,
        val path: String,
        val ready: Boolean,
        /** 短标签：已就绪 / 未就绪 / 路径无效 */
        val label: String,
        /** 设置页明细 */
        val detail: String
    )

    /**
     * @param path 解析后的绝对路径或 stub://
     */
    fun check(kind: Kind, path: String): Check {
        val trimmed = path.trim()
        if (trimmed.isBlank()) {
            return Check(
                kind = kind,
                path = "",
                ready = false,
                label = "未就绪",
                detail = missingHint(kind)
            )
        }
        if (trimmed.startsWith("stub://")) {
            return Check(
                kind = kind,
                path = trimmed,
                ready = true,
                label = "已就绪（stub）",
                detail = "stub 路径：$trimmed"
            )
        }
        val f = File(trimmed)
        val ready = when (kind) {
            Kind.LIVE2D -> f.isFile
            Kind.ASR, Kind.TTS, Kind.LOCAL_LLM ->
                (f.isDirectory && (f.listFiles()?.isNotEmpty() == true)) || f.isFile
        }
        return if (ready) {
            Check(
                kind = kind,
                path = trimmed,
                ready = true,
                label = "已就绪",
                detail = when (kind) {
                    Kind.LIVE2D -> "Live2D model3 文件存在（M2b 渲染壳可加载）"
                    Kind.ASR -> "ASR 模型路径存在（待 sherpa 引擎 / so）"
                    Kind.TTS -> "TTS 模型路径存在（待引擎 / so）"
                    Kind.LOCAL_LLM -> "本地模型路径存在（待 MNN 引擎）"
                }
            )
        } else {
            Check(
                kind = kind,
                path = trimmed,
                ready = false,
                label = "路径无效",
                detail = "配置了路径但文件/目录不存在：$trimmed"
            )
        }
    }

    fun missingHint(kind: Kind): String = when (kind) {
        Kind.LIVE2D, Kind.ASR, Kind.TTS -> DebugOpenSourcePaths.FETCH_SCRIPT_HINT
        Kind.LOCAL_LLM -> DebugOpenSourcePaths.LOCAL_LLM_DEFAULT_HINT
    }

    /**
     * 汇总卡片文案：任一缺失则追加拉取说明。
     */
    fun summaryMessage(
        live2d: Check,
        asr: Check,
        tts: Check,
        localLlm: Check? = null
    ): String {
        val parts = buildList {
            add("Live2D：${live2d.label}")
            add("ASR：${asr.label}")
            add("TTS：${tts.label}")
            if (localLlm != null) add("本地脑：${localLlm.label}")
        }
        val base = parts.joinToString(" · ")
        val anyMissing = !live2d.ready || !asr.ready || !tts.ready
        return if (anyMissing) {
            "$base\n${DebugOpenSourcePaths.FETCH_SCRIPT_HINT}"
        } else {
            base
        }
    }
}
