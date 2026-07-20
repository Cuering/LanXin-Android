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

package com.lanxin.android.builtin.capabilities.domain

/**
 * 智能能力门闸（纯逻辑，可单测）。
 *
 * [effective] = master && child.enabled && runtime_ready
 *
 * - 主开关关：子能力一律拒，不改各子模块 DataStore 原值
 * - runtime_ready：权限/模型/引擎等运行时条件；缺省 true
 * - 本地脑默认 OFF：不因 master ON 而抬升
 * - ASSISTANT_TOOLS 同时门控系统工具 / 联网搜索 / 设备感知（含旧 id 别名）
 * - LOCATION_AROUND 门控位置（含旧 LOCATION）
 */
object SmartCapabilitiesGate {

    const val DENIED_MASTER = "smart_capabilities_master_disabled"
    const val DENIED_CHILD = "smart_capabilities_child_disabled"
    const val DENIED_RUNTIME = "smart_capabilities_runtime_not_ready"

    /**
     * 子能力是否有效可用。
     *
     * @param config 总配置
     * @param id 子能力（主 UI 组或兼容别名均可）
     * @param runtimeReady 运行时就绪（权限已授、模型已 load 等）；默认 true
     */
    fun effective(
        config: SmartCapabilitiesConfig,
        id: SmartCapabilityId,
        runtimeReady: Boolean = true
    ): Boolean {
        if (!config.masterEnabled) return false
        if (!config.isChildEnabled(id)) return false
        return runtimeReady
    }

    /**
     * 拒绝原因码；允许时返回 null。
     */
    fun denyReason(
        config: SmartCapabilitiesConfig,
        id: SmartCapabilityId,
        runtimeReady: Boolean = true
    ): String? {
        if (!config.masterEnabled) return DENIED_MASTER
        if (!config.isChildEnabled(id)) return DENIED_CHILD
        if (!runtimeReady) return DENIED_RUNTIME
        return null
    }

    fun denyMessage(code: String?): String? = when (code) {
        DENIED_MASTER -> "智能能力主开关已关闭（设置 → 智能能力）"
        DENIED_CHILD -> "该子能力已关闭（设置 → 智能能力）"
        DENIED_RUNTIME -> "运行时未就绪（权限或模型）"
        else -> null
    }

    /**
     * 旧模块开关与主开关合成：master && legacyChild。
     * 用于 WebSearch / DeviceSensing 等已有 config.enabled 字段。
     *
     * 调用方应传入 master && assistantTools（或 master 本身）作为 masterEnabled。
     */
    fun effectiveLegacy(
        masterEnabled: Boolean,
        childEnabled: Boolean,
        runtimeReady: Boolean = true
    ): Boolean = masterEnabled && childEnabled && runtimeReady

    /** 助手工具组是否有效（系统工具 / 搜索 / 设备感知共用）。 */
    fun assistantToolsEffective(
        config: SmartCapabilitiesConfig,
        runtimeReady: Boolean = true
    ): Boolean = effective(config, SmartCapabilityId.ASSISTANT_TOOLS, runtimeReady)

    /** 位置与周边是否有效。 */
    fun locationAroundEffective(
        config: SmartCapabilitiesConfig,
        runtimeReady: Boolean = true
    ): Boolean = effective(config, SmartCapabilityId.LOCATION_AROUND, runtimeReady)
}
