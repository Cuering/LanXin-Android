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

/**
 * 本地推理引擎状态。
 */
enum class LocalEngineState {
    /** 未启用（设置关闭）。 */
    DISABLED,

    /** 已启用但模型未加载。 */
    IDLE,

    /** 正在加载模型。 */
    LOADING,

    /** 模型就绪，可推理。 */
    READY,

    /** 加载或推理失败。 */
    ERROR
}

/**
 * 本地推理配置快照。
 *
 * @property enabled 是否启用本地推理
 * @property modelPath 模型目录或文件路径（绝对路径，不入库大文件）
 * @property maxTokens 生成上限（输出 token，与上下文窗口分离）
 * @property temperature 采样温度
 * @property showThinking 是否展示模型思考过程（默认关；关时剥离 `<think>` 并不进气泡）
 * @property contextWindowTokens 上下文窗口（历史+system+生成预留的总预算，默认 8k）
 */
data class LocalInferenceConfig(
    val enabled: Boolean = false,
    val modelPath: String = "",
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    val temperature: Float = DEFAULT_TEMPERATURE,
    val showThinking: Boolean = false,
    val contextWindowTokens: Int = DEFAULT_CONTEXT_WINDOW_TOKENS
) {
    companion object {
        /** 生成 max_new_tokens 默认。 */
        const val DEFAULT_MAX_TOKENS = 512
        const val DEFAULT_TEMPERATURE = 0.7f
        const val MIN_MAX_TOKENS = 16
        /** 生成上限（输出）；勿与 context window 混淆。 */
        const val MAX_MAX_TOKENS = 2048

        /** 12G 机默认 8k ctx；可选 4k/8k/12k。 */
        const val DEFAULT_CONTEXT_WINDOW_TOKENS = 8192
        const val MIN_CONTEXT_WINDOW_TOKENS = 2048
        const val MAX_CONTEXT_WINDOW_TOKENS = 12288

        /** UI 快捷选项。 */
        val CONTEXT_WINDOW_PRESETS = intArrayOf(4096, 8192, 12288)
    }
}

/**
 * 生成请求。
 *
 * @property prompt 用户提示（可含 system 前缀）
 * @property systemPrompt 可选 system 指令
 * @property maxTokens 覆盖配置的 token 上限
 * @property temperature 覆盖配置的温度
 */
data class LocalGenerateRequest(
    val prompt: String,
    val systemPrompt: String? = null,
    val maxTokens: Int? = null,
    val temperature: Float? = null
)

/**
 * 生成结果。
 */
data class LocalGenerateResult(
    val text: String,
    val finishReason: String = "stop",
    val isStub: Boolean = false
)

/**
 * 路由决策来源（为 6.2 / 6.3 预留）。
 */
enum class InferenceRouteTarget {
    CLOUD,
    LOCAL,
    UNAVAILABLE
}

/**
 * 路由决策结果。
 *
 * @property target 目标通道
 * @property reason 决策原因（日志 / 调试）
 */
data class InferenceRouteDecision(
    val target: InferenceRouteTarget,
    val reason: String
)
