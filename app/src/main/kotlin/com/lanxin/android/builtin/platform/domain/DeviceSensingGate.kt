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

package com.lanxin.android.builtin.platform.domain

import com.lanxin.android.plugin.ToolDef
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 设备感知门闸（纯逻辑，可单测）。
 *
 * - 关：不向 Agent 暴露 [DeviceSensingConfig.TOOL_NAME]；执行返回 denied
 * - 开：正常放行
 * - **不**改 ChatRouter needsTools；有注册工具 ≠ 本轮需要工具
 * - 与 [WebSearchGate] 风格一致，可链式 filter
 */
object DeviceSensingGate {

    const val DENIED_CODE = "system_info_disabled"

    fun isEnabled(config: DeviceSensingConfig, masterEnabled: Boolean = true): Boolean =
        masterEnabled && config.enabled

    /**
     * 从工具列表中按开关过滤 [DeviceSensingConfig.TOOL_NAME]。
     * 其它工具原样返回。
     * @param masterEnabled 智能能力主开关；关则一律移除
     */
    fun filterTools(
        tools: List<ToolDef>,
        config: DeviceSensingConfig,
        masterEnabled: Boolean = true
    ): List<ToolDef> {
        if (isEnabled(config, masterEnabled)) return tools
        return tools.filter { it.name != DeviceSensingConfig.TOOL_NAME }
    }

    /**
     * 执行前检查；允许时返回 null，拒绝时返回 JSON 错误体。
     */
    fun denyIfDisabled(
        config: DeviceSensingConfig,
        masterEnabled: Boolean = true
    ): JsonObject? {
        if (isEnabled(config, masterEnabled)) return null
        return buildJsonObject {
            put("ok", false)
            put(
                "error",
                if (!masterEnabled) {
                    "智能能力主开关已关闭（设置 → 智能能力）"
                } else {
                    "设备感知已关闭（设置 → 智能能力 → 设备感知）"
                }
            )
            put("code", DENIED_CODE)
        }
    }
}
