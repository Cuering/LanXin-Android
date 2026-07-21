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

import com.lanxin.android.data.dto.ApiState
import kotlinx.coroutines.flow.Flow

/**
 * 与云端 Provider 体系对接的本地推理门面。
 *
 * Chat 层可优先选云端；失败或离线时（6.2）可选用本 Provider。
 * 6.1 仅提供可注入实现，完整路由见 6.3 ChatRouter。
 */
interface LocalInferenceProvider {

    /** 引擎是否可作为当前候选。 */
    fun canServe(): Boolean

    /**
     * 以 [ApiState] 流形式输出，对齐 [com.lanxin.android.plugins.chat.data.ChatRepository.completeChat]。
     *
     * @param prompt 用户侧文本（已拼好上下文时可整段传入）
     * @param systemPrompt 可选 system
     * @param maxTokens 可选输出上限覆盖（陪伴短答可传更小值）
     */
    fun completeAsApiState(
        prompt: String,
        systemPrompt: String? = null,
        maxTokens: Int? = null
    ): Flow<ApiState>
}
