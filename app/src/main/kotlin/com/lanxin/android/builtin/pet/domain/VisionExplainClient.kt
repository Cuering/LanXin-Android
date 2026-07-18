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

import com.lanxin.android.data.model.ClientType

/**
 * 多模态视觉讲解客户端（云端 vision Provider）。
 *
 * 无可用 vision 时返回 [VisionExplainResult.Unavailable]，**禁止**编造画面描述。
 */
interface VisionExplainClient {

    /** 解析当前配置是否可看图。 */
    suspend fun resolveCapability(): VisionCapability

    /**
     * 结合用户问题与一帧画面讲解。
     * 调用前应已确认 [VisionCapability.available]。
     */
    suspend fun explain(question: String, frame: CompanionVisionFrame): VisionExplainResult
}

data class VisionCapability(
    val available: Boolean,
    /** 用户可见阻断/说明文案；available 时可为模型摘要 */
    val message: String,
    val platformName: String = "",
    val modelId: String = "",
    val clientType: ClientType? = null,
    val apiUrl: String = "",
    val token: String? = null
)

sealed class VisionExplainResult {
    data class Ok(val replyText: String, val modelId: String) : VisionExplainResult()
    data class Unavailable(val userMessage: String) : VisionExplainResult()
    data class Error(val userMessage: String) : VisionExplainResult()
}
