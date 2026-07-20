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

package com.lanxin.android.presentation.ui.chat

import com.lanxin.android.builtin.capabilities.domain.LocationConfig
import com.lanxin.android.builtin.capabilities.domain.LocationGate
import com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesConfig
import com.lanxin.android.builtin.navigate.domain.NavigateGate
import com.lanxin.android.builtin.persona.domain.PersonaCapabilityFilter
import com.lanxin.android.builtin.platform.domain.DeviceSensingConfig
import com.lanxin.android.builtin.platform.domain.DeviceSensingGate
import com.lanxin.android.builtin.platform.domain.WebSearchConfig
import com.lanxin.android.builtin.platform.domain.WebSearchGate
import com.lanxin.android.plugin.ToolDef

/**
 * 发送路径工具门闸纯逻辑（可单测）。
 *
 * 将 WebSearch / DeviceSensing / Location / Navigate / Persona 过滤串成一条链路，
 * 任一步异常不得打穿发送协程；调用方仍应再包一层 runCatching。
 */
object ChatSendToolFilterLogic {

    data class FilterInput(
        val tools: List<ToolDef>,
        val smart: SmartCapabilitiesConfig = SmartCapabilitiesConfig(),
        val webSearch: WebSearchConfig = WebSearchConfig(),
        val deviceSensing: DeviceSensingConfig = DeviceSensingConfig(),
        val location: LocationConfig = LocationConfig(),
        val allowedTools: List<String>? = null,
        val allowedSkills: List<String>? = null,
        val knownSkillNames: Set<String> = emptySet()
    )

    data class FilterOutput(
        val tools: List<ToolDef>,
        /** null 表示不限制执行侧工具名 */
        val allowedNames: Set<String>?
    )

    fun filter(input: FilterInput): FilterOutput {
        val master = input.smart.masterEnabled
        val afterWeb = WebSearchGate.filterTools(
            tools = input.tools,
            config = input.webSearch,
            masterEnabled = master && input.smart.webSearchEnabled
        )
        val afterDevice = DeviceSensingGate.filterTools(
            tools = afterWeb,
            config = input.deviceSensing,
            masterEnabled = master && input.smart.deviceSensingEnabled
        )
        val afterLocation = LocationGate.filterTools(
            tools = afterDevice,
            smart = input.smart,
            location = input.location
        )
        val locationPrefsOpen = LocationGate.isPrefsOpen(input.smart, input.location)
        val webOn = WebSearchGate.isEnabled(
            input.webSearch,
            master && input.smart.webSearchEnabled
        )
        val gated = NavigateGate.filterTools(
            tools = afterLocation,
            masterEnabled = master,
            locationPrefsOpen = locationPrefsOpen,
            webSearchEnabled = webOn
        )

        if (input.allowedTools == null && input.allowedSkills == null) {
            return FilterOutput(tools = gated, allowedNames = null)
        }

        val filtered = PersonaCapabilityFilter.filterTools(
            tools = gated,
            allowedTools = input.allowedTools,
            allowedSkills = input.allowedSkills,
            knownSkillNames = input.knownSkillNames
        )
        return FilterOutput(
            tools = filtered,
            allowedNames = filtered.map { it.name }.toSet()
        )
    }
}
