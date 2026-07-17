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
 * Phase 6.3 路由 reason 码（可测、可日志）。
 */
object RouteReason {
    /** 用户 preferLocal 且本地就绪（且无 tool 强制云端）。 */
    const val PREFER_LOCAL = "prefer_local"

    /** 无网 + 本地就绪 → 本地离线兜底。 */
    const val OFFLINE_LOCAL = "offline_local"

    /** 需要 tool_call / MCP → 优先云端。 */
    const val NEED_TOOLS_CLOUD = "need_tools_cloud"

    /** 无网 + 本地未就绪 → 不可用（引导设置）。 */
    const val OFFLINE_LOCAL_UNAVAILABLE = "offline_local_unavailable"

    /** 有网默认云端。 */
    const val DEFAULT_CLOUD = "default_cloud"

    /** 云端不可选但本地就绪。 */
    const val LOCAL_ONLY = "local_only_available"

    /** 云端与本地均不可用（非无网场景或通用兜底）。 */
    const val NO_PROVIDER = "no_provider"

    /** 未注入 ChatRouter 时的默认云端。 */
    const val CLOUD_DEFAULT_NO_ROUTER = "cloud_default_no_router"
}

/**
 * ChatRouter 决策输入（消息上下文 + 环境）。
 *
 * @property preferLocal 用户配置 prefer_local
 * @property localReady 本地引擎 isReady（已 load）；开关关则永不 ready
 * @property networkAvailable 网络是否可用；null 表示未知（不按离线处理）
 * @property needsTools 本轮需要 tool_call / MCP 工具链（本地无 tool_call → 优先云端）
 * @property cloudAvailable 覆盖「云端是否可选」；null 时随 networkAvailable（null 网络视为可尝试云端）
 */
data class ChatRouteContext(
    val preferLocal: Boolean,
    val localReady: Boolean,
    val networkAvailable: Boolean? = null,
    val needsTools: Boolean = false,
    val cloudAvailable: Boolean? = null
)

/**
 * Phase 6.3 统一 Chat 路由层：云端 ↔ 本地自动切换（纯函数，无 Android 依赖）。
 *
 * 单一入口：给定 [ChatRouteContext] → [InferenceRouteDecision]（CLOUD / LOCAL / UNAVAILABLE + reason）。
 *
 * 产品规则（严格）：
 * 1. 开关关 / 引擎未 ready → localReady=false → 不走本地
 * 2. **需要 tool_call / MCP** 且云端可选 → **CLOUD**（[RouteReason.NEED_TOOLS_CLOUD]）
 * 3. preferLocal + ready 且无 tool 强制 → LOCAL
 * 4. 无网 + ready → LOCAL
 * 5. 无网 + 未就绪 → UNAVAILABLE（引导设置）
 * 6. 有网默认云端；否则仅本地 / 不可用
 *
 * [InferenceRouteSelector] 委托本对象，避免平行逻辑。
 */
object ChatRouter {

    /**
     * 计算路由决策。
     */
    fun decide(context: ChatRouteContext): InferenceRouteDecision {
        val networkOk = context.networkAvailable
        val localReady = context.localReady
        val cloudAvailable = context.cloudAvailable
            ?: (networkOk != false)
        val preferLocal = context.preferLocal
        val needsTools = context.needsTools

        // 1) 需要工具 → 优先云端（本地无 tool_call）
        if (needsTools && cloudAvailable) {
            return InferenceRouteDecision(
                target = InferenceRouteTarget.CLOUD,
                reason = RouteReason.NEED_TOOLS_CLOUD
            )
        }

        // 2) 用户偏好本地且就绪（无 tool 强制云端时）
        if (preferLocal && localReady) {
            return InferenceRouteDecision(
                target = InferenceRouteTarget.LOCAL,
                reason = RouteReason.PREFER_LOCAL
            )
        }

        // 3) 明确无网 + 本地就绪 → 离线本地
        if (networkOk == false && localReady) {
            return InferenceRouteDecision(
                target = InferenceRouteTarget.LOCAL,
                reason = RouteReason.OFFLINE_LOCAL
            )
        }

        // 4) 明确无网 + 本地未就绪 → 不可用（引导设置）
        if (networkOk == false && !localReady) {
            return InferenceRouteDecision(
                target = InferenceRouteTarget.UNAVAILABLE,
                reason = RouteReason.OFFLINE_LOCAL_UNAVAILABLE
            )
        }

        // 5) 云端可选 → 默认云端
        if (cloudAvailable) {
            return InferenceRouteDecision(
                target = InferenceRouteTarget.CLOUD,
                reason = RouteReason.DEFAULT_CLOUD
            )
        }

        // 6) 仅本地
        if (localReady) {
            return InferenceRouteDecision(
                target = InferenceRouteTarget.LOCAL,
                reason = RouteReason.LOCAL_ONLY
            )
        }

        return InferenceRouteDecision(
            target = InferenceRouteTarget.UNAVAILABLE,
            reason = RouteReason.NO_PROVIDER
        )
    }

    /**
     * 便捷重载（与历史 [InferenceRouteSelector.select] 参数对齐）。
     */
    fun decide(
        preferLocal: Boolean,
        localReady: Boolean,
        cloudAvailable: Boolean,
        networkAvailable: Boolean? = null,
        needsTools: Boolean = false
    ): InferenceRouteDecision = decide(
        ChatRouteContext(
            preferLocal = preferLocal,
            localReady = localReady,
            networkAvailable = networkAvailable,
            needsTools = needsTools,
            cloudAvailable = cloudAvailable
        )
    )

    /**
     * Phase 7.5：合并设备工具意图到路由上下文。
     *
     * 当 [deviceToolIntentHit] 为 true（如 [com.lanxin.android.builtin.systemtools.domain.DeviceToolBridge.detectsToolIntent]）
     * 时，将 needsTools 置 true，优先云端 tool_call；本地仍可走 DeviceToolBridge 直办。
     */
    fun decideWithDeviceToolHint(
        preferLocal: Boolean,
        localReady: Boolean,
        networkAvailable: Boolean? = null,
        cloudAvailable: Boolean? = null,
        needsTools: Boolean = false,
        deviceToolIntentHit: Boolean = false
    ): InferenceRouteDecision = decide(
        ChatRouteContext(
            preferLocal = preferLocal,
            localReady = localReady,
            networkAvailable = networkAvailable,
            needsTools = needsTools || deviceToolIntentHit,
            cloudAvailable = cloudAvailable
        )
    )
}
