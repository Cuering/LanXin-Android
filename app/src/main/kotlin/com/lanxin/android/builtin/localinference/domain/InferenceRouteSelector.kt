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
 * 云端 ↔ 本地路由选择（兼容入口）。
 *
 * Phase 6.3：完整决策收敛到 [ChatRouter]；本对象仅作薄委托，避免平行逻辑。
 * 新代码请优先使用 [ChatRouter.decide]。
 */
object InferenceRouteSelector {

    /**
     * 选择推理目标（委托 [ChatRouter]）。
     *
     * @param preferLocal 用户显式偏好本地
     * @param localAvailable 本地引擎可用（启用+就绪）
     * @param cloudAvailable 云端平台可用
     * @param networkAvailable 网络是否可用；null 表示未知（不按离线处理）
     * @param needsTools 是否需要 tool_call / MCP（优先云端）
     */
    fun select(
        preferLocal: Boolean,
        localAvailable: Boolean,
        cloudAvailable: Boolean,
        networkAvailable: Boolean? = null,
        needsTools: Boolean = false
    ): InferenceRouteDecision = ChatRouter.decide(
        preferLocal = preferLocal,
        localReady = localAvailable,
        cloudAvailable = cloudAvailable,
        networkAvailable = networkAvailable,
        needsTools = needsTools
    )
}
