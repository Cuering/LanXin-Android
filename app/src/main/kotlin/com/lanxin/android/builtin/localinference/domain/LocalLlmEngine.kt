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

package com.lanxin.android.builtin.localinference.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 本地 LLM 推理引擎抽象。
 *
 * Phase 6.1：接口 + stub 实现（[StubLocalLlmEngine]）。
 * 后续接入 MNN / JNI 时替换实现，不改 Chat 调用方。
 *
 * 能力边界：
 * - 已实现：load / unload / generate / stream(stub) / isReady / isAvailable / state
 * - stub：stream 回退为整段 generate 后一次 emit
 * - 非目标：完整 ChatRouter、离线自动切换（6.2/6.3）
 */
interface LocalLlmEngine {

    /** 当前引擎状态。 */
    val state: StateFlow<LocalEngineState>

    /** 模型是否已加载且可推理。 */
    val isReady: Boolean

    /**
     * 是否可用：设置启用 + 路径非空（不一定已 load）。
     * Chat 层可用作 fallback 候选判断。
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
    suspend fun load(config: LocalInferenceConfig): Boolean

    /** 卸载模型并释放 native 资源。 */
    suspend fun unload()

    /**
     * 清空 KV / 会话状态（对齐 MNN `Llm::reset`）。
     * 单轮或完整 history 重建前调用，避免上一轮污染。
     * 默认空实现；native 引擎覆盖。
     */
    suspend fun reset() = Unit

    /**
     * 同步生成完整回复。
     *
     * @throws IllegalStateException 引擎未就绪
     */
    suspend fun generate(request: LocalGenerateRequest): LocalGenerateResult

    /**
     * 流式生成。6.1 stub 可整段返回；真实 MNN 实现按 token emit。
     */
    fun stream(request: LocalGenerateRequest): Flow<String>
}
