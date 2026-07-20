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
 * 智能能力默认值 / 聚合迁移（纯逻辑，可单测）。
 *
 * ## v1
 * - 从未配置（legacy key 不存在）→ 抬到新默认（语音/搜索/系统工具/设备感知 ON）
 * - 用户曾显式关 → **保留 false**
 * - **本地脑 / 场景不抬 ON**
 *
 * ## v2（开关精简）
 * - 助手工具组 = 系统工具 + 联网搜索 + 设备感知
 * - 位置与周边组 = 位置
 * - 组策略：**任一项曾显式关 → 组默认关；从未配置 → 组默认 ON**
 * - 本地脑独立保留，默认 OFF
 * - 导航 / 导游独立默认 OFF，不参与聚合
 *
 * [LegacyCapabilitySnapshot] 用 Boolean?：null=从未写过，true/false=显式值。
 */
object SmartCapabilitiesMigration {

    data class LegacyCapabilitySnapshot(
        /** web_search_enabled */
        val webSearch: Boolean? = null,
        /** device_sensing_enabled */
        val deviceSensing: Boolean? = null,
        /** system_tools_master_enabled */
        val systemToolsMaster: Boolean? = null,
        /** offline_asr_enabled（语音能力代表） */
        val voiceAsr: Boolean? = null,
        /** tts_enabled */
        val voiceTts: Boolean? = null,
        /** local_inference_enabled — 不抬 ON */
        val localInference: Boolean? = null,
        /** scene_sensing / camera_scene enabled — 不抬 ON */
        val sceneVision: Boolean? = null,
        /** location / smart_capabilities_location */
        val location: Boolean? = null,
        /** 已有 smart 聚合键（v1 写过） */
        val smartSystemTools: Boolean? = null,
        val smartWebSearch: Boolean? = null,
        val smartDeviceSensing: Boolean? = null,
        val smartLocation: Boolean? = null,
        val smartAssistantTools: Boolean? = null,
        val smartLocationAround: Boolean? = null,
        /** 导航 / 导游插件镜像；默认 OFF */
        val navigate: Boolean? = null,
        val guide: Boolean? = null
    )

    /**
     * 从未配置 → 新默认；曾显式写过 → 保留。
     * 本地脑 / 场景：显式 true 保留 true；否则保持 DEFAULT（false）。
     */
    fun resolveChild(
        legacy: Boolean?,
        newDefault: Boolean,
        neverLiftOn: Boolean = false
    ): Boolean {
        if (legacy != null) return legacy
        if (neverLiftOn) return false
        return newDefault
    }

    /**
     * 语音：ASR 或 TTS 任一显式关则关；任一显式开则开；均未配置 → ON。
     */
    fun resolveVoice(asr: Boolean?, tts: Boolean?): Boolean {
        if (asr == null && tts == null) return SmartCapabilitiesConfig.DEFAULT_VOICE
        if (asr == false || tts == false) return false
        if (asr == true || tts == true) return true
        return SmartCapabilitiesConfig.DEFAULT_VOICE
    }

    /**
     * 聚合组：任一项曾显式关 → 关；从未配置 → 默认 ON；否则 ON。
     *
     * 用于 ASSISTANT_TOOLS / LOCATION_AROUND。
     */
    fun resolveGroup(vararg members: Boolean?, defaultOn: Boolean = true): Boolean {
        if (members.any { it == false }) return false
        if (members.all { it == null }) return defaultOn
        return true
    }

    /**
     * 优先已有聚合键；否则从成员（smart 细项优先，再 legacy 模块键）推导。
     */
    fun resolveAssistantTools(legacy: LegacyCapabilitySnapshot): Boolean {
        legacy.smartAssistantTools?.let { return it }
        return resolveGroup(
            legacy.smartSystemTools ?: legacy.systemToolsMaster,
            legacy.smartWebSearch ?: legacy.webSearch,
            legacy.smartDeviceSensing ?: legacy.deviceSensing,
            defaultOn = SmartCapabilitiesConfig.DEFAULT_ASSISTANT_TOOLS
        )
    }

    fun resolveLocationAround(legacy: LegacyCapabilitySnapshot): Boolean {
        legacy.smartLocationAround?.let { return it }
        return resolveGroup(
            legacy.smartLocation ?: legacy.location,
            defaultOn = SmartCapabilitiesConfig.DEFAULT_LOCATION_AROUND
        )
    }

    /** v1+v2 一次构建（新装或全量从旧模块键迁）。 */
    fun buildConfig(legacy: LegacyCapabilitySnapshot): SmartCapabilitiesConfig {
        val voice = resolveVoice(legacy.voiceAsr, legacy.voiceTts)
        return SmartCapabilitiesConfig(
            masterEnabled = SmartCapabilitiesConfig.DEFAULT_MASTER,
            localInferenceEnabled = resolveChild(
                legacy = legacy.localInference,
                newDefault = SmartCapabilitiesConfig.DEFAULT_LOCAL_INFERENCE,
                neverLiftOn = true
            ),
            voiceEnabled = voice,
            assistantToolsEnabled = resolveAssistantTools(legacy),
            locationAroundEnabled = resolveLocationAround(legacy),
            navigateEnabled = resolveChild(
                legacy = legacy.navigate,
                newDefault = SmartCapabilitiesConfig.DEFAULT_NAVIGATE,
                neverLiftOn = true
            ),
            guideEnabled = resolveChild(
                legacy = legacy.guide,
                newDefault = SmartCapabilitiesConfig.DEFAULT_GUIDE,
                neverLiftOn = true
            ),
            sceneVisionEnabled = resolveChild(
                legacy = legacy.sceneVision,
                newDefault = SmartCapabilitiesConfig.DEFAULT_SCENE_VISION,
                neverLiftOn = true
            ),
            migratedV1 = true,
            migratedV2 = true
        )
    }

    /**
     * 仅 v2：在已有 v1 配置上折叠为聚合开关。
     * 输入为 v1 已落盘的细项（或模块键），输出更新后的聚合字段。
     * 导航 / 导游保持独立默认 OFF（或传入已有值）。
     */
    fun collapseToV2(
        masterEnabled: Boolean,
        localInferenceEnabled: Boolean,
        voiceEnabled: Boolean,
        systemTools: Boolean?,
        webSearch: Boolean?,
        deviceSensing: Boolean?,
        location: Boolean?,
        sceneVisionEnabled: Boolean,
        existingAssistantTools: Boolean? = null,
        existingLocationAround: Boolean? = null,
        navigateEnabled: Boolean = SmartCapabilitiesConfig.DEFAULT_NAVIGATE,
        guideEnabled: Boolean = SmartCapabilitiesConfig.DEFAULT_GUIDE
    ): SmartCapabilitiesConfig {
        val assistant = existingAssistantTools
            ?: resolveGroup(
                systemTools,
                webSearch,
                deviceSensing,
                defaultOn = SmartCapabilitiesConfig.DEFAULT_ASSISTANT_TOOLS
            )
        val locationAround = existingLocationAround
            ?: resolveGroup(
                location,
                defaultOn = SmartCapabilitiesConfig.DEFAULT_LOCATION_AROUND
            )
        return SmartCapabilitiesConfig(
            masterEnabled = masterEnabled,
            localInferenceEnabled = localInferenceEnabled,
            voiceEnabled = voiceEnabled,
            assistantToolsEnabled = assistant,
            locationAroundEnabled = locationAround,
            navigateEnabled = navigateEnabled,
            guideEnabled = guideEnabled,
            sceneVisionEnabled = sceneVisionEnabled,
            migratedV1 = true,
            migratedV2 = true
        )
    }
}
