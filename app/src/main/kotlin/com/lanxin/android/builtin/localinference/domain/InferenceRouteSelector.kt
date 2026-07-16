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
 * 云端 ↔ 本地路由选择纯逻辑（无 Android 依赖，便于 JVM 单测）。
 *
 * Phase 6.1：骨架决策，供 Chat 层手动选用或后续 ChatRouter 调用。
 * 完整离线兜底与自动切换在 6.2 / 6.3 扩展。
 */
object InferenceRouteSelector {

    /**
     * 选择推理目标。
     *
     * 规则（6.1 MVP）：
     * 1. preferLocal && localAvailable → LOCAL
     * 2. !networkAvailable && localAvailable → LOCAL（离线兜底预览）
     * 3. cloudAvailable → CLOUD
     * 4. localAvailable → LOCAL
     * 5. 否则 UNAVAILABLE
     *
     * @param preferLocal 用户显式偏好本地
     * @param localAvailable 本地引擎可用（启用+路径/就绪）
     * @param cloudAvailable 云端平台可用
     * @param networkAvailable 网络是否可用；null 表示未知（不按离线处理）
     */
    fun select(
        preferLocal: Boolean,
        localAvailable: Boolean,
        cloudAvailable: Boolean,
        networkAvailable: Boolean? = null
    ): InferenceRouteDecision {
        if (preferLocal && localAvailable) {
            return InferenceRouteDecision(
                target = InferenceRouteTarget.LOCAL,
                reason = "user_prefer_local"
            )
        }
        if (networkAvailable == false && localAvailable) {
            return InferenceRouteDecision(
                target = InferenceRouteTarget.LOCAL,
                reason = "offline_fallback"
            )
        }
        if (cloudAvailable) {
            return InferenceRouteDecision(
                target = InferenceRouteTarget.CLOUD,
                reason = "cloud_preferred"
            )
        }
        if (localAvailable) {
            return InferenceRouteDecision(
                target = InferenceRouteTarget.LOCAL,
                reason = "local_only_available"
            )
        }
        return InferenceRouteDecision(
            target = InferenceRouteTarget.UNAVAILABLE,
            reason = "no_provider"
        )
    }
}
