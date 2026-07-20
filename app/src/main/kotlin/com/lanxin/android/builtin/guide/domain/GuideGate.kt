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

package com.lanxin.android.builtin.guide.domain

import com.lanxin.android.plugin.ToolDef
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 导游 Guide 门闸（纯逻辑）。
 *
 * 依赖：
 * - **插件开关** [pluginEnabled]（默认 OFF）
 * - 智能能力主开关
 * - 视觉讲解：场景视觉 consent + 运行时 CAMERA（由 Companion 侧执行）
 * - 位置增强：主开关 + 位置 prefs；运行时再读 last known
 */
object GuideGate {

    const val DENIED_PLUGIN = "guide_plugin_disabled"
    const val DENIED_MASTER = "guide_master_disabled"

    fun isPluginOpen(pluginEnabled: Boolean): Boolean = pluginEnabled

    fun canExplain(
        pluginEnabled: Boolean,
        masterEnabled: Boolean
    ): Boolean = pluginEnabled && masterEnabled

    /**
     * 是否允许把 last known 位置注入讲解提示。
     * 视觉本身仍由相机 consent 决定；位置关闭时讲解可继续，只是无坐标上下文。
     * 插件关时也不增强。
     */
    fun canAugmentWithLocation(
        pluginEnabled: Boolean,
        masterEnabled: Boolean,
        locationPrefsOpen: Boolean
    ): Boolean = pluginEnabled && masterEnabled && locationPrefsOpen

    /**
     * 视觉讲解语义：插件开 + 用户已 consent 且会话「看世界」开、有相机权限。
     */
    fun canExplainWithVision(
        pluginEnabled: Boolean,
        visionLooking: Boolean,
        consentGranted: Boolean,
        cameraGranted: Boolean
    ): Boolean = pluginEnabled && visionLooking && consentGranted && cameraGranted

    /**
     * 插件关时不暴露「看世界」入口（UI 隐藏/禁用）。
     */
    fun canShowVisionEntry(pluginEnabled: Boolean, masterEnabled: Boolean): Boolean =
        pluginEnabled && masterEnabled

    fun filterTools(
        tools: List<ToolDef>,
        pluginEnabled: Boolean,
        masterEnabled: Boolean
    ): List<ToolDef> {
        if (!pluginEnabled || !masterEnabled) {
            return tools.filter { it.name !in GuideConfig.ALL_TOOL_NAMES }
        }
        return tools
    }

    fun denyExplainIfDisabled(
        pluginEnabled: Boolean,
        masterEnabled: Boolean
    ): JsonObject? {
        if (!pluginEnabled) {
            return deny(
                "导游插件已关闭（默认关；设置 → 智能能力 → 导游 或 插件管理开启 lanxin.guide）",
                DENIED_PLUGIN
            )
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
