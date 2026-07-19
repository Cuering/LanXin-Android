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
 * 智能能力子模块标识。
 *
 * 主开关关闭时子能力一律拒。
 * 主 UI 五组：本地推理 / 语音 / 助手工具 / 位置与周边 / 看世界。
 * 导航 / 导游为独立插件开关（默认 OFF，设置页可见）。
 * 旧 id（SYSTEM_TOOLS / WEB_SEARCH / DEVICE_SENSING / LOCATION）保留作兼容别名，
 * 门闸映射到对应聚合开关。
 */
enum class SmartCapabilityId {
    LOCAL_INFERENCE,
    VOICE,
    /** 聚合：系统工具 + 联网搜索 + 设备感知 */
    ASSISTANT_TOOLS,
    /** 聚合：位置（导航/周边门闸可复用）；不含场景视觉 */
    LOCATION_AROUND,
    SCENE_VISION,
    /** 导航独立插件镜像；默认 OFF */
    NAVIGATE,
    /** 导游独立插件镜像；默认 OFF */
    GUIDE,
    // —— 兼容别名（高级细项 / 旧调用点）——
    SYSTEM_TOOLS,
    WEB_SEARCH,
    DEVICE_SENSING,
    LOCATION
}

/**
 * 智能能力总配置快照（设置页「智能能力」聚合）。
 *
 * 主 UI 字段：master / localInference / voice / assistantTools / locationAround / sceneVision。
 * 插件镜像：[navigateEnabled] / [guideEnabled]（默认 OFF，可见可开）。
 * 兼容属性 [systemToolsEnabled]、[webSearchEnabled]、[deviceSensingEnabled]、[locationEnabled]
 * 一律映射到对应聚合开关，避免调用点大改。
 *
 * @property masterEnabled 主开关；关则 [SmartCapabilitiesGate.effective] 一律 false
 * @property localInferenceEnabled 本地脑；**默认 OFF**，不随迁移抬 ON
 * @property voiceEnabled ASR+TTS 会话能力；默认 ON
 * @property assistantToolsEnabled 助手工具（系统工具+搜索+设备感知）；默认 ON
 * @property locationAroundEnabled 位置与周边；默认 ON；导航可复用
 * @property navigateEnabled 导航插件镜像；**默认 OFF**
 * @property guideEnabled 导游插件镜像；**默认 OFF**
 * @property sceneVisionEnabled 看世界 / 场景视觉；默认 OFF + consent
 * @property migratedV1 是否已跑过 v1 默认值迁移
 * @property migratedV2 是否已跑过 v2 聚合开关迁移
 */
data class SmartCapabilitiesConfig(
    val masterEnabled: Boolean = DEFAULT_MASTER,
    val localInferenceEnabled: Boolean = DEFAULT_LOCAL_INFERENCE,
    val voiceEnabled: Boolean = DEFAULT_VOICE,
    val assistantToolsEnabled: Boolean = DEFAULT_ASSISTANT_TOOLS,
    val locationAroundEnabled: Boolean = DEFAULT_LOCATION_AROUND,
    val navigateEnabled: Boolean = DEFAULT_NAVIGATE,
    val guideEnabled: Boolean = DEFAULT_GUIDE,
    val sceneVisionEnabled: Boolean = DEFAULT_SCENE_VISION,
    val migratedV1: Boolean = false,
    val migratedV2: Boolean = false
) {
    /** 兼容：系统工具跟随助手工具组。 */
    val systemToolsEnabled: Boolean get() = assistantToolsEnabled

    /** 兼容：联网搜索跟随助手工具组。 */
    val webSearchEnabled: Boolean get() = assistantToolsEnabled

    /** 兼容：设备感知跟随助手工具组。 */
    val deviceSensingEnabled: Boolean get() = assistantToolsEnabled

    /** 兼容：位置跟随位置与周边组。 */
    val locationEnabled: Boolean get() = locationAroundEnabled

    fun isChildEnabled(id: SmartCapabilityId): Boolean = when (id) {
        SmartCapabilityId.LOCAL_INFERENCE -> localInferenceEnabled
        SmartCapabilityId.VOICE -> voiceEnabled
        SmartCapabilityId.ASSISTANT_TOOLS,
        SmartCapabilityId.SYSTEM_TOOLS,
        SmartCapabilityId.WEB_SEARCH,
        SmartCapabilityId.DEVICE_SENSING -> assistantToolsEnabled
        SmartCapabilityId.LOCATION_AROUND,
        SmartCapabilityId.LOCATION -> locationAroundEnabled
        SmartCapabilityId.NAVIGATE -> navigateEnabled
        SmartCapabilityId.GUIDE -> guideEnabled
        SmartCapabilityId.SCENE_VISION -> sceneVisionEnabled
    }

    /** 状态摘要：主开关 + 主 UI 已开组数（5 组）。 */
    fun summaryLine(): String {
        if (!masterEnabled) return "主开关已关 · 子能力一律拒"
        val on = PRIMARY_IDS.count { isChildEnabled(it) }
        val total = PRIMARY_IDS.size
        return "主开关开 · $on/$total 组已开"
    }

    companion object {
        const val DEFAULT_MASTER = true
        const val DEFAULT_LOCAL_INFERENCE = false
        const val DEFAULT_VOICE = true
        const val DEFAULT_ASSISTANT_TOOLS = true
        const val DEFAULT_LOCATION_AROUND = true
        /** 导航插件默认关 */
        const val DEFAULT_NAVIGATE = false
        /** 导游插件默认关 */
        const val DEFAULT_GUIDE = false
        const val DEFAULT_SCENE_VISION = false

        /** 兼容旧默认名 */
        const val DEFAULT_SYSTEM_TOOLS = DEFAULT_ASSISTANT_TOOLS
        const val DEFAULT_WEB_SEARCH = DEFAULT_ASSISTANT_TOOLS
        const val DEFAULT_DEVICE_SENSING = DEFAULT_ASSISTANT_TOOLS
        const val DEFAULT_LOCATION = DEFAULT_LOCATION_AROUND

        /** 主 UI 展示的五组（不含兼容别名 / 插件镜像）。 */
        val PRIMARY_IDS: List<SmartCapabilityId> = listOf(
            SmartCapabilityId.LOCAL_INFERENCE,
            SmartCapabilityId.VOICE,
            SmartCapabilityId.ASSISTANT_TOOLS,
            SmartCapabilityId.LOCATION_AROUND,
            SmartCapabilityId.SCENE_VISION
        )
    }
}
