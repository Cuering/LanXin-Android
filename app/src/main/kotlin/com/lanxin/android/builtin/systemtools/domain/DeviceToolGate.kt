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

package com.lanxin.android.builtin.systemtools.domain

/**
 * 设备工具权限门闸（纯逻辑，可单测）。
 *
 * 顺序：总开关 → 分项开关 → 确认策略 → 放行。
 * 真机权限（Calendar 等）在具体 Gateway 内再检查；此处不绑定 Android API。
 */
class DeviceToolGate(
    private val configProvider: suspend () -> SystemToolsConfig,
    /** 智能能力主开关；默认 true 兼容旧调用 */
    private val smartMasterProvider: suspend () -> Boolean = { true }
) {

    suspend fun evaluate(
        tool: DeviceTool,
        confirmed: Boolean
    ): DeviceToolOutcome? {
        if (!smartMasterProvider()) {
            return DeviceToolOutcome.Denied(
                reason = "智能能力或助手工具已关闭（设置 → 智能能力）",
                code = "smart_master_disabled"
            )
        }
        val config = configProvider()
        if (!config.masterEnabled) {
            return DeviceToolOutcome.Denied(
                reason = "系统能力总开关已关闭（设置 → 智能能力 → 助手工具 / 高级）",
                code = "master_disabled"
            )
        }
        if (!config.isCapabilityEnabled(tool.capability)) {
            return DeviceToolOutcome.Denied(
                reason = "能力 ${tool.capability.name} 未启用",
                code = "capability_disabled"
            )
        }
        val needsConfirm = when (tool.confirmationLevel) {
            ConfirmationLevel.NONE -> false
            ConfirmationLevel.CONFIRM ->
                config.requireConfirmOnWrite &&
                    tool.sideEffect != DeviceToolSideEffect.READ
            ConfirmationLevel.EXPLICIT_APPROVE -> true
        }
        if (needsConfirm && !confirmed) {
            return DeviceToolOutcome.NeedsConfirmation(
                summary = "${tool.name}: ${tool.description}",
                toolName = tool.name,
                sideEffect = tool.sideEffect
            )
        }
        return null // 放行
    }

    suspend fun invoke(
        tool: DeviceTool,
        args: Map<String, Any?>,
        confirmed: Boolean = false
    ): DeviceToolOutcome {
        evaluate(tool, confirmed)?.let { return it }
        return try {
            tool.invoke(args, confirmed = true)
        } catch (e: Exception) {
            DeviceToolOutcome.Error(message = e.message ?: e.toString())
        }
    }
}
