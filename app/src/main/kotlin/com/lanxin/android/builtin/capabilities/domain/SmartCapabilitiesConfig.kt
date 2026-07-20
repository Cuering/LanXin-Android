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
 * 主开关关闭时子能力一律拒；本地推理 / 场景视觉 / 导航 / 导游 默认 OFF；
 * 语音 / 系统工具 / 联网搜索 / 设备感知 / 位置 默认 ON。
 * Claw / 桌宠悬浮不在本模块默认 ON 列表。
 */
enum class SmartCapabilityId {
    LOCAL_INFERENCE,
    VOICE,
    SYSTEM_TOOLS,
    WEB_SEARCH,
    DEVICE_SENSING,
    LOCATION,
    NAVIGATE,
    GUIDE,
    SCENE_VISION
}

/**
 * 智能能力总配置快照（设置页「智能能力」聚合）。
 *
 * @property masterEnabled 主开关；关则 [SmartCapabilitiesGate.effective] 一律 false
 * @property localInferenceEnabled 本地脑；**默认 OFF**，不随迁移抬 ON
 * @property voiceEnabled ASR+TTS 会话能力；默认 ON；不绑定悬浮窗
 * @property systemToolsEnabled 系统工具总入口；默认 ON；写操作仍确认
 * @property webSearchEnabled 联网搜索；默认 ON
 * @property deviceSensingEnabled 设备感知 system_info；默认 ON
 * @property locationEnabled 位置（只读、按需权限）；默认 ON
 * @property navigateEnabled 导航插件镜像；**默认 OFF**
 * @property guideEnabled 导游插件镜像；**默认 OFF**
 * @property sceneVisionEnabled 场景视觉；默认 OFF + consent
 * @property migratedV1 是否已跑过 v1 默认值迁移
 */
data class SmartCapabilitiesConfig(
    val masterEnabled: Boolean = DEFAULT_MASTER,
    val localInferenceEnabled: Boolean = DEFAULT_LOCAL_INFERENCE,
    val voiceEnabled: Boolean = DEFAULT_VOICE,
    val systemToolsEnabled: Boolean = DEFAULT_SYSTEM_TOOLS,
    val webSearchEnabled: Boolean = DEFAULT_WEB_SEARCH,
    val deviceSensingEnabled: Boolean = DEFAULT_DEVICE_SENSING,
    val locationEnabled: Boolean = DEFAULT_LOCATION,
    val navigateEnabled: Boolean = DEFAULT_NAVIGATE,
    val guideEnabled: Boolean = DEFAULT_GUIDE,
    val sceneVisionEnabled: Boolean = DEFAULT_SCENE_VISION,
    val migratedV1: Boolean = false
) {
    fun isChildEnabled(id: SmartCapabilityId): Boolean = when (id) {
        SmartCapabilityId.LOCAL_INFERENCE -> localInferenceEnabled
        SmartCapabilityId.VOICE -> voiceEnabled
        SmartCapabilityId.SYSTEM_TOOLS -> systemToolsEnabled
        SmartCapabilityId.WEB_SEARCH -> webSearchEnabled
        SmartCapabilityId.DEVICE_SENSING -> deviceSensingEnabled
        SmartCapabilityId.LOCATION -> locationEnabled
        SmartCapabilityId.NAVIGATE -> navigateEnabled
        SmartCapabilityId.GUIDE -> guideEnabled
        SmartCapabilityId.SCENE_VISION -> sceneVisionEnabled
    }

    /** 状态摘要：主开关 + 已开子能力数。 */
    fun summaryLine(): String {
        if (!masterEnabled) return "主开关已关 · 子能力一律拒"
        val on = SmartCapabilityId.entries.count { isChildEnabled(it) }
        val total = SmartCapabilityId.entries.size
        return "主开关开 · $on/$total 子能力已开"
    }

    companion object {
        const val DEFAULT_MASTER = true
        const val DEFAULT_LOCAL_INFERENCE = false
        const val DEFAULT_VOICE = true
        const val DEFAULT_SYSTEM_TOOLS = true
        const val DEFAULT_WEB_SEARCH = true
        const val DEFAULT_DEVICE_SENSING = true
        const val DEFAULT_LOCATION = true

        /** 导航插件默认关 */
        const val DEFAULT_NAVIGATE = false

        /** 导游插件默认关 */
        const val DEFAULT_GUIDE = false
        const val DEFAULT_SCENE_VISION = false
    }
}
