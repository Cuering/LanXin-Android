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

/**
 * 摄像头场景识别门闸（纯逻辑，可单测）。
 *
 * 产品约束：
 * - **默认关**
 * - 首次开启需 [SceneSensingConfig.consentGranted]
 * - 识别前需系统 CAMERA 已授权（由 [cameraGranted] 注入，不在此申请）
 * - 不后台偷拍：仅 [canCapture] 为 true 时 UI 才可拉起系统快照
 */
object SceneSensingGate {

    const val DENIED_DISABLED = "scene_sensing_disabled"
    const val DENIED_NO_CONSENT = "scene_sensing_no_consent"
    const val DENIED_NO_CAMERA = "scene_sensing_no_camera"

    /** 是否允许识别（开关 + 同意 + 相机权限）。 */
    fun canCapture(
        config: SceneSensingConfig,
        cameraGranted: Boolean,
        masterEnabled: Boolean = true
    ): Boolean {
        return masterEnabled && config.enabled && config.consentGranted && cameraGranted
    }

    /**
     * 用户尝试打开开关时：若尚未同意，必须先弹确认 Gate。
     * @return true = 需要先展示确认对话框
     */
    fun needsConsentDialog(config: SceneSensingConfig, turningOn: Boolean): Boolean {
        return turningOn && !config.consentGranted
    }

    /**
     * 执行前检查；允许时返回 null，拒绝时返回稳定 code。
     */
    fun denyReason(
        config: SceneSensingConfig,
        cameraGranted: Boolean,
        masterEnabled: Boolean = true
    ): String? {
        if (!masterEnabled) return DENIED_DISABLED
        if (!config.enabled) return DENIED_DISABLED
        if (!config.consentGranted) return DENIED_NO_CONSENT
        if (!cameraGranted) return DENIED_NO_CAMERA
        return null
    }

    /** 用户可见阻断文案。 */
    fun blockMessage(code: String?): String? = when (code) {
        null -> null
        DENIED_DISABLED ->
            "场景识别已关闭。请到「设置 → 智能能力 → 场景视觉」开启（默认关）。"
        DENIED_NO_CONSENT ->
            "尚未确认隐私说明。请在设置页阅读并同意后再开启。"
        DENIED_NO_CAMERA ->
            "需要相机权限才能识别场景。仅在你点击「识别」时使用，不会后台偷拍。"
        else -> "暂时无法识别场景（$code）"
    }

    /**
     * 开关语义：仅当 [consentGranted] 时允许 enabled=true。
     * 返回应写入的 enabled 值（钳制）。
     */
    fun clampEnabled(requestedEnabled: Boolean, consentGranted: Boolean): Boolean {
        if (!requestedEnabled) return false
        return consentGranted
    }
}
