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

package com.lanxin.android.builtin.localinference.data

import com.lanxin.android.builtin.localinference.domain.LocalChatMessage
import com.lanxin.android.builtin.localinference.domain.LocalGenerateRequest
import com.lanxin.android.builtin.localinference.domain.LocalInferenceProvider
import com.lanxin.android.builtin.localinference.domain.LocalInferenceSettings
import com.lanxin.android.builtin.localinference.domain.LocalLlmEngine
import com.lanxin.android.builtin.localinference.domain.LocalReplySanitizer
import com.lanxin.android.data.dto.ApiState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * 本地推理 Provider：把 [LocalLlmEngine] 适配为 [ApiState] 流。
 *
 * Chat 层可选调用；完整自动切换见 Phase 6.3。
 * 引擎出口统一清洗：剥离 think / mood / 动作标签；[showThinking] 默认关。
 */
@Singleton
class DefaultLocalInferenceProvider @Inject constructor(
    private val engine: LocalLlmEngine,
    private val settings: LocalInferenceSettings
) : LocalInferenceProvider {

    override fun canServe(): Boolean = engine.isAvailable || engine.isReady

    override fun completeAsApiState(
        prompt: String,
        systemPrompt: String?,
        maxTokens: Int?,
        history: List<LocalChatMessage>
    ): Flow<ApiState> = flow {
        val config = settings.getConfig()
        if (!config.enabled) {
            emit(
                ApiState.Error(
                    "本地推理未启用。请到「设置 → 本地推理」打开开关并加载模型。"
                )
            )
            return@flow
        }
        if (config.modelPath.isBlank()) {
            emit(
                ApiState.Error(
                    "本地脑模型路径为空。请到桌宠设置「一键下载本地脑」或导入模型目录。"
                )
            )
            return@flow
        }
        if (!engine.isReady) {
            val ok = engine.load(config)
            if (!ok) {
                emit(
                    ApiState.Error(
                        engine.lastError
                            ?: "local_engine_not_ready：模型加载失败，请检查路径 ${config.modelPath}"
                    )
                )
                return@flow
            }
        }
        val effectiveSystem = LocalReplySanitizer.appendOutputConstraint(
            systemPrompt = systemPrompt,
            showThinking = config.showThinking
        )
        val effectiveMax = (maxTokens ?: config.maxTokens).coerceAtLeast(16)
        // 本地现为整段 emit；累积后一次清洗，避免流式半标签泄漏
        val rawBuilder = StringBuilder()
        engine.stream(
            LocalGenerateRequest(
                prompt = prompt,
                systemPrompt = effectiveSystem,
                maxTokens = effectiveMax,
                temperature = config.temperature,
                history = history
            )
        ).collect { chunk ->
            rawBuilder.append(chunk)
        }
        val cleaned = LocalReplySanitizer.clean(
            raw = rawBuilder.toString(),
            showThinking = config.showThinking
        )
        if (config.showThinking) {
            cleaned.thinkingText?.takeIf { it.isNotEmpty() }?.let {
                emit(ApiState.Thinking(it))
            }
        }
        if (cleaned.displayText.isNotEmpty()) {
            emit(ApiState.Success(cleaned.displayText))
        }
    }
        .onStart { emit(ApiState.Loading) }
        .catch { t -> emit(ApiState.Error(t.message ?: "local_inference_error")) }
        .onCompletion { emit(ApiState.Done) }
}
