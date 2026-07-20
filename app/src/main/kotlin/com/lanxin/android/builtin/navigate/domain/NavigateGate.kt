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

package com.lanxin.android.builtin.navigate.domain

import com.lanxin.android.plugin.ToolDef
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 导航 Navigate 门闸（纯逻辑）。
 *
 * 依赖：导航插件开关（默认 OFF）+ 智能能力主开关 + 位置 prefs + 联网搜索 prefs。
 * 插件关：三工具一律不进列表；不调 Overpass / 外链；不主动要定位。
 * open_navigation 在插件开时仅需主开关（外链 Intent，不读坐标也可由参数传入）。
 */
object NavigateGate {

    const val DENIED_PLUGIN = "navigate_plugin_disabled"
    const val DENIED_MASTER = "navigate_master_disabled"
    const val DENIED_LOCATION = "navigate_location_disabled"
    const val DENIED_WEB = "navigate_web_disabled"

    fun isPluginOpen(pluginEnabled: Boolean): Boolean = pluginEnabled

    fun isMasterOpen(masterEnabled: Boolean): Boolean = masterEnabled

    /**
     * nearby_poi / hotel_price 需要：插件开 + 主开关 + 位置 prefs + 联网搜索 prefs。
     * （执行时再检查运行时定位权限与坐标）
     */
    fun canQueryPoi(
        pluginEnabled: Boolean,
        masterEnabled: Boolean,
        locationPrefsOpen: Boolean,
        webSearchEnabled: Boolean
    ): Boolean =
        pluginEnabled && masterEnabled && locationPrefsOpen && webSearchEnabled

    fun canOpenNavigation(pluginEnabled: Boolean, masterEnabled: Boolean): Boolean =
        pluginEnabled && masterEnabled

    /**
     * @param pluginEnabled 导航插件/智能能力「导航」子开关；默认 false
     * @param masterEnabled 智能能力主开关
     * @param locationPrefsOpen 位置能力 prefs 开
     * @param webSearchEnabled 联网搜索有效开
     */
    fun filterTools(
        tools: List<ToolDef>,
        pluginEnabled: Boolean,
        masterEnabled: Boolean,
        locationPrefsOpen: Boolean,
        webSearchEnabled: Boolean
    ): List<ToolDef> {
        if (!pluginEnabled || !masterEnabled) {
            return tools.filter { it.name !in NavigateConfig.ALL_TOOL_NAMES }
        }
        return tools.filter { tool ->
            when (tool.name) {
                NavigateConfig.NEARBY_POI_TOOL,
                NavigateConfig.HOTEL_PRICE_TOOL ->
                    locationPrefsOpen && webSearchEnabled
                NavigateConfig.OPEN_NAVIGATION_TOOL -> true
                else -> true
            }
        }
    }

    fun denyPoiIfDisabled(
        pluginEnabled: Boolean,
        masterEnabled: Boolean,
        locationPrefsOpen: Boolean,
        webSearchEnabled: Boolean
    ): JsonObject? {
        if (!pluginEnabled) {
            return deny("导航能力已关闭（设置 → 智能能力 → 导航）", DENIED_PLUGIN)
        }
        if (!masterEnabled) {
            return deny("智能能力主开关已关闭", DENIED_MASTER)
        }
        if (!locationPrefsOpen) {
            return deny("位置能力已关闭（设置 → 智能能力 → 位置）", DENIED_LOCATION)
        }
        if (!webSearchEnabled) {
            return deny("联网搜索已关闭（附近 POI / 酒店价需联网）", DENIED_WEB)
        }
        return null
    }

    fun denyNavIfDisabled(pluginEnabled: Boolean, masterEnabled: Boolean): JsonObject? {
        if (!pluginEnabled) {
            return deny("导航能力已关闭（设置 → 智能能力 → 导航）", DENIED_PLUGIN)
        }
        if (!masterEnabled) {
            return deny("智能能力主开关已关闭", DENIED_MASTER)
        }
        return null
    }

    private fun deny(message: String, code: String): JsonObject = buildJsonObject {
        put("ok", false)
        put("error", message)
        put("code", code)
    }
}
