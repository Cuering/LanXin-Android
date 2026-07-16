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
 * Chat 完成路径上的本地 fallback 决策辅助（纯逻辑，JVM 可测）。
 *
 * 与 [InferenceRouteSelector] 配合：Repository 在 completeChat 入口调用。
 */
object ChatLocalFallback {

    /**
     * 是否应走本地 Provider，而不是云端 API。
     */
    fun shouldUseLocal(decision: InferenceRouteDecision): Boolean =
        decision.target == InferenceRouteTarget.LOCAL

    /**
     * 是否应直接返回明确错误（无网且本地不可用等）。
     */
    fun shouldEmitUnavailable(decision: InferenceRouteDecision): Boolean =
        decision.target == InferenceRouteTarget.UNAVAILABLE

    /**
     * 从用户消息列表提取本地推理 prompt（最后一条用户文本）。
     */
    fun extractPrompt(userTexts: List<String>): String =
        userTexts.lastOrNull { it.isNotBlank() }.orEmpty()

    /**
     * 映射 UNAVAILABLE 原因到用户可见错误。
     */
    fun unavailableMessage(decision: InferenceRouteDecision, networkAvailable: Boolean): String {
        return when {
            !networkAvailable -> InferenceRouteCoordinator.OFFLINE_LOCAL_UNAVAILABLE_MESSAGE
            decision.reason == "no_provider" -> InferenceRouteCoordinator.NO_PROVIDER_MESSAGE
            else -> InferenceRouteCoordinator.NO_PROVIDER_MESSAGE
        }
    }

    /**
     * 是否本地离线/偏好本地生成（用于状态文案）。
     */
    fun isLocalGeneration(decision: InferenceRouteDecision): Boolean =
        decision.target == InferenceRouteTarget.LOCAL
}
