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

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 6.3 ChatRouter 运行时门面：收集设置 / 引擎就绪 / 网络，委托 [ChatRouter]。
 *
 * 产品边界：
 * - `local_inference_enabled` 关 → 引擎不会 ready → 永不走本地（**除非** forceLocal 懒加载）
 * - 仅 **引擎 ready（已 load）** 才算 localReady
 * - **forceLocal** + 已有 modelPath：decide 前 [LocalInferenceBootstrap.ensureReady]（#120）
 * - **needsTools** → 优先云端（本地无 tool_call）
 * - 无网时 cloudAvailable=false；有网默认云端（preferLocal 且 ready 时本地）
 *
 * 保留类名以兼容 6.2 DI / 调用方；语义上即 ChatRouter Coordinator。
 */
@Singleton
class InferenceRouteCoordinator @Inject constructor(
    private val networkStatusProvider: NetworkStatusProvider,
    private val settings: LocalInferenceSettings,
    private val engine: LocalLlmEngine,
    private val bootstrap: LocalInferenceBootstrap
) {

    /**
     * 计算当前推理路由。
     *
     * @param needsTools 本轮是否需要 tool_call / MCP 工具链
     * @param forceCloudAvailable 覆盖「云端是否可选」；默认随网络。
     *        传入 false 可模拟仅本地候选（测试 / 预览）。
     * @param forceLocal 会话显式选中本地模型（最高优先级；会触发懒加载）
     */
    suspend fun decide(
        needsTools: Boolean = false,
        forceCloudAvailable: Boolean? = null,
        forceLocal: Boolean = false
    ): InferenceRouteDecision {
        // forceLocal：有路径则自动 enable + load（冷启动懒加载 / 重试）
        if (forceLocal) {
            runCatching { bootstrap.ensureReady(enableIfNeeded = true) }
        }
        val networkOk = networkStatusProvider.isNetworkAvailable()
        val preferLocal = settings.isPreferLocal()
        val localReady = engine.isReady
        val cloudAvailable = forceCloudAvailable ?: networkOk
        return ChatRouter.decide(
            ChatRouteContext(
                preferLocal = preferLocal,
                localReady = localReady,
                networkAvailable = networkOk,
                needsTools = needsTools,
                cloudAvailable = cloudAvailable,
                forceLocal = forceLocal
            )
        )
    }

    /**
     * 设置页 / 调试用的路由预览文案（默认按无 tool 需求预览）。
     * 预览不触发懒加载，避免设置页误 load。
     */
    suspend fun previewLabel(needsTools: Boolean = false): String {
        val networkOk = networkStatusProvider.isNetworkAvailable()
        val preferLocal = settings.isPreferLocal()
        val config = settings.getConfig()
        val localReady = engine.isReady
        val decision = ChatRouter.decide(
            ChatRouteContext(
                preferLocal = preferLocal,
                localReady = localReady,
                networkAvailable = networkOk,
                needsTools = needsTools,
                cloudAvailable = networkOk
            )
        )
        val engineState = engine.state.value
        val netLabel = if (networkOk) "有网" else "无网"
        val localLabel = when {
            !config.enabled -> "本地关"
            engineState == LocalEngineState.READY -> "本地就绪"
            else -> "本地未就绪(${engineState.name})"
        }
        val toolsLabel = if (needsTools) "需工具" else "纯对话"
        val targetLabel = when (decision.target) {
            InferenceRouteTarget.CLOUD -> "云端"
            InferenceRouteTarget.LOCAL -> "本地"
            InferenceRouteTarget.UNAVAILABLE -> "不可用"
        }
        return "$netLabel · $localLabel · $toolsLabel · 路由=$targetLabel (${decision.reason})"
    }

    companion object {
        /**
         * 无网且本地不可用时的用户可见错误（缩短，可重试）。
         */
        const val OFFLINE_LOCAL_UNAVAILABLE_MESSAGE =
            LocalInferenceBootstrap.OFFLINE_LOCAL_UNAVAILABLE_SHORT

        /**
         * 路由完全不可用时的兜底文案。
         */
        const val NO_PROVIDER_MESSAGE =
            "当前没有可用的推理通道（云端与本地均不可用）。"

        /**
         * 会话强制本地但引擎未就绪（缩短，引导重试 / 导入）。
         */
        const val FORCE_LOCAL_UNAVAILABLE_MESSAGE =
            LocalInferenceBootstrap.FORCE_LOCAL_UNAVAILABLE_SHORT
    }
}
