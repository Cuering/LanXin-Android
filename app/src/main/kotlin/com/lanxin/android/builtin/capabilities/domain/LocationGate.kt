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

import com.lanxin.android.plugin.ToolDef
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 位置门闸（纯逻辑，可单测）。
 *
 * prefs 开 = smart.master && smart.locationAroundEnabled && location.enabled
 * （locationEnabled 为兼容属性，等同 locationAroundEnabled）
 * 可用 = prefs 开 && permissionGranted
 * 不后台持续定位；权限仅在 tool 用时检查。
 */
object LocationGate {

    const val DENIED_CODE = "location_disabled"
    const val DENIED_NO_PERMISSION = "location_permission_denied"

    /**
     * prefs 层是否允许（不含运行时权限）。
     */
    fun isPrefsOpen(smart: SmartCapabilitiesConfig, location: LocationConfig): Boolean =
        smart.masterEnabled && smart.locationAroundEnabled && location.enabled

    fun canUse(
        smart: SmartCapabilitiesConfig,
        location: LocationConfig,
        permissionGranted: Boolean
    ): Boolean = isPrefsOpen(smart, location) && permissionGranted

    fun filterTools(
        tools: List<ToolDef>,
        smart: SmartCapabilitiesConfig,
        location: LocationConfig
    ): List<ToolDef> {
        if (isPrefsOpen(smart, location)) return tools
        return tools.filter { it.name != LocationConfig.TOOL_NAME }
    }

    fun denyIfDisabled(
        smart: SmartCapabilitiesConfig,
        location: LocationConfig,
        permissionGranted: Boolean
    ): JsonObject? {
        if (!isPrefsOpen(smart, location)) {
            return buildJsonObject {
                put("ok", false)
                put("error", "位置能力已关闭（设置 → 智能能力 → 位置与周边）")
                put("code", DENIED_CODE)
            }
        }
        if (!permissionGranted) {
            return buildJsonObject {
                put("ok", false)
                put("error", "未授予定位权限；请在系统设置中允许后重试")
                put("code", DENIED_NO_PERMISSION)
            }
        }
        return null
    }
}
