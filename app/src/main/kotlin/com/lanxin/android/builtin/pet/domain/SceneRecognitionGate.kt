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

package com.lanxin.android.builtin.pet.domain

/**
 * 场景识别门闸（纯逻辑，可单测）。
 *
 * - 默认关：不可采集
 * - 开启需 [SceneRecognitionConfig.enabled] 且 [SceneRecognitionConfig.consentGranted]
 * - 关闭时必须清理会话缓存（由调用方配合 [SceneRecognitionSession.clear]）
 * - **不**偷偷后台采集；**不**硬绑 Live2D 资源
 */
object SceneRecognitionGate {

    const val DENIED_CODE = "scene_recognition_disabled"
    const val NO_CONSENT_CODE = "scene_recognition_no_consent"

    /** 隐私确认文案（开启 Gate 时展示）。 */
    const val PRIVACY_NOTICE =
        "开启后将在你主动触发时使用摄像头采集一帧画面，在本机做轻量场景判断" +
            "（亮度/色温启发式），结果仅保存在本次会话内存，不上传、不落盘原图。" +
            "可随时关闭；关闭后立即清空会话缓存。默认关闭。"

    fun isEnabled(config: SceneRecognitionConfig): Boolean = config.enabled

    fun hasConsent(config: SceneRecognitionConfig): Boolean = config.consentGranted

    /**
     * 是否允许真正采集/识别。
     * 必须：总开关开 + 用户已确认隐私。
     * 系统 CAMERA 权限由 UI 层再校验。
     */
    fun canCapture(config: SceneRecognitionConfig): Boolean =
        config.enabled && config.consentGranted

    /**
     * 尝试开启：仅当用户已明确确认时返回新配置；否则返回 null（UI 应弹确认）。
     */
    fun tryEnable(
        current: SceneRecognitionConfig,
        userConfirmed: Boolean
    ): SceneRecognitionConfig? {
        if (!userConfirmed) return null
        return current.copy(enabled = true, consentGranted = true)
    }

    /**
     * 关闭：关总开关；可选撤销确认。
     * [revokeConsent]=true 时下次开启需重新确认。
     */
    fun disable(
        current: SceneRecognitionConfig,
        revokeConsent: Boolean = true
    ): SceneRecognitionConfig {
        return current.copy(
            enabled = false,
            consentGranted = if (revokeConsent) false else current.consentGranted
        )
    }

    /**
     * 执行前检查；允许返回 null，拒绝返回错误码。
     */
    fun denyIfCannotCapture(config: SceneRecognitionConfig): String? {
        if (!config.enabled) return DENIED_CODE
        if (!config.consentGranted) return NO_CONSENT_CODE
        return null
    }

    /** 关闭后是否应清理会话缓存（始终 true）。 */
    fun shouldClearSessionOnDisable(): Boolean = true
}
