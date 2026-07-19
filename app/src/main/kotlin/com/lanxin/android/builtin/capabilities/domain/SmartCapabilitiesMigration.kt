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
 * v1 默认值迁移（纯逻辑，可单测）。
 *
 * 规则（哥哥拍板）：
 * - 从未配置（legacy key 不存在）→ 抬到新默认（语音/搜索/系统工具/设备感知 ON）
 * - 用户曾显式关（legacy key == false）→ **保留 false**
 * - 用户曾显式开（legacy key == true）→ 保留 true
 * - **本地脑 / 场景 / Claw / 悬浮 不抬 ON**
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
        val sceneVision: Boolean? = null
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
        // 任一侧显式关 → 关；任一侧显式开且无关 → 开
        if (asr == false || tts == false) return false
        if (asr == true || tts == true) return true
        return SmartCapabilitiesConfig.DEFAULT_VOICE
    }

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
            systemToolsEnabled = resolveChild(
                legacy = legacy.systemToolsMaster,
                newDefault = SmartCapabilitiesConfig.DEFAULT_SYSTEM_TOOLS
            ),
            webSearchEnabled = resolveChild(
                legacy = legacy.webSearch,
                newDefault = SmartCapabilitiesConfig.DEFAULT_WEB_SEARCH
            ),
            deviceSensingEnabled = resolveChild(
                legacy = legacy.deviceSensing,
                newDefault = SmartCapabilitiesConfig.DEFAULT_DEVICE_SENSING
            ),
            locationEnabled = SmartCapabilitiesConfig.DEFAULT_LOCATION,
            sceneVisionEnabled = resolveChild(
                legacy = legacy.sceneVision,
                newDefault = SmartCapabilitiesConfig.DEFAULT_SCENE_VISION,
                neverLiftOn = true
            ),
            migratedV1 = true
        )
    }
}
