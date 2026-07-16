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

package com.lanxin.android.builtin.voice.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 离线语音识别（ASR）引擎抽象。
 *
 * Phase 6.4：接口 + [com.lanxin.android.builtin.voice.data.StubAsrEngine]。
 * 后续接入 Sherpa-ONNX / JNI 时替换实现，不改 Chat 调用方。
 *
 * 能力边界：
 * - 已实现：load / unload / transcribe / streamPartial(stub) / isReady / isAvailable / state
 * - stub：无真实 so 时返回可控 stub 文本
 * - 非目标：打包真实 sherpa-onnx so / 大模型文件（见 docs/voice-asr.md）
 */
interface AsrEngine {

    /** 当前引擎状态。 */
    val state: StateFlow<AsrEngineState>

    /** 模型是否已加载且可转写。 */
    val isReady: Boolean

    /**
     * 是否可用：设置启用 + 路径非空（不一定已 load）。
     */
    val isAvailable: Boolean

    /** 最近一次错误信息；无错误时为 null。 */
    val lastError: String?

    /**
     * 按配置加载模型。
     *
     * @param config 启用开关与模型路径等
     * @return true 加载成功
     */
    suspend fun load(config: AsrConfig): Boolean

    /** 卸载模型并释放 native 资源。 */
    suspend fun unload()

    /**
     * 整段 PCM 转写。
     *
     * @throws IllegalStateException 引擎未就绪
     */
    suspend fun transcribe(request: AsrTranscribeRequest): AsrTranscribeResult

    /**
     * 流式 partial 结果。6.4 stub 可整段 transcribe 后一次 emit。
     */
    fun streamPartial(request: AsrTranscribeRequest): Flow<AsrTranscribeResult>
}
