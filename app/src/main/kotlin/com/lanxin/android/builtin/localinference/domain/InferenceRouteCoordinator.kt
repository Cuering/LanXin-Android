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
 * Phase 6.2 路由协调：把设置 / 引擎就绪 / 网络状态接到 [InferenceRouteSelector]。
 *
 * 产品边界：
 * - `local_inference_enabled` 关 → 引擎不会 ready → 永不走本地
 * - 仅 **引擎 ready（已 load）** 才算 localAvailable
 * - 无网时 cloudAvailable=false；有网默认云端（preferLocal 且 ready 时本地）
 */
@Singleton
class InferenceRouteCoordinator @Inject constructor(
    private val networkStatusProvider: NetworkStatusProvider,
    private val settings: LocalInferenceSettings,
    private val engine: LocalLlmEngine
) {

    /**
     * 计算当前推理路由。
     *
     * @param forceCloudAvailable 覆盖「云端是否可选」；默认随网络。
     *        传入 false 可模拟仅本地候选（测试 / 预览）。
     */
    suspend fun decide(
        forceCloudAvailable: Boolean? = null
    ): InferenceRouteDecision {
        val networkOk = networkStatusProvider.isNetworkAvailable()
        val preferLocal = settings.isPreferLocal()
        val localReady = engine.isReady
        val cloudAvailable = forceCloudAvailable ?: networkOk
        return InferenceRouteSelector.select(
            preferLocal = preferLocal,
            localAvailable = localReady,
            cloudAvailable = cloudAvailable,
            networkAvailable = networkOk
        )
    }

    /**
     * 设置页 / 调试用的路由预览文案。
     */
    suspend fun previewLabel(): String {
        val decision = decide()
        val networkOk = networkStatusProvider.isNetworkAvailable()
        val config = settings.getConfig()
        val engineState = engine.state.value
        val netLabel = if (networkOk) "有网" else "无网"
        val localLabel = when {
            !config.enabled -> "本地关"
            engineState == LocalEngineState.READY -> "本地就绪"
            else -> "本地未就绪(${engineState.name})"
        }
        val targetLabel = when (decision.target) {
            InferenceRouteTarget.CLOUD -> "云端"
            InferenceRouteTarget.LOCAL -> "本地"
            InferenceRouteTarget.UNAVAILABLE -> "不可用"
        }
        return "$netLabel · $localLabel · 路由=$targetLabel (${decision.reason})"
    }

    companion object {
        /**
         * 无网且本地不可用时的用户可见错误（Chat 层展示）。
         */
        const val OFFLINE_LOCAL_UNAVAILABLE_MESSAGE =
            "当前无网络，且本地推理不可用。请到「设置 → 本地推理」打开开关、填写模型路径并加载模型后再试。"

        /**
         * 路由完全不可用时的兜底文案。
         */
        const val NO_PROVIDER_MESSAGE =
            "当前没有可用的推理通道（云端与本地均不可用）。"
    }
}
