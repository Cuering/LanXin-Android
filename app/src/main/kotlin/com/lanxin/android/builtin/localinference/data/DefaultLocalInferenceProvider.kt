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

import com.lanxin.android.builtin.localinference.domain.LocalGenerateRequest
import com.lanxin.android.builtin.localinference.domain.LocalInferenceProvider
import com.lanxin.android.builtin.localinference.domain.LocalInferenceSettings
import com.lanxin.android.builtin.localinference.domain.LocalLlmEngine
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
 */
@Singleton
class DefaultLocalInferenceProvider @Inject constructor(
    private val engine: LocalLlmEngine,
    private val settings: LocalInferenceSettings
) : LocalInferenceProvider {

    override fun canServe(): Boolean = engine.isAvailable || engine.isReady

    override fun completeAsApiState(
        prompt: String,
        systemPrompt: String?
    ): Flow<ApiState> = flow {
        val config = settings.getConfig()
        if (!engine.isReady) {
            val ok = engine.load(config)
            if (!ok) {
                emit(ApiState.Error(engine.lastError ?: "local_engine_not_ready"))
                return@flow
            }
        }
        engine.stream(
            LocalGenerateRequest(
                prompt = prompt,
                systemPrompt = systemPrompt,
                maxTokens = config.maxTokens,
                temperature = config.temperature
            )
        ).collect { chunk ->
            emit(ApiState.Success(chunk))
        }
    }
        .onStart { emit(ApiState.Loading) }
        .catch { t -> emit(ApiState.Error(t.message ?: "local_inference_error")) }
        .onCompletion { emit(ApiState.Done) }
}
