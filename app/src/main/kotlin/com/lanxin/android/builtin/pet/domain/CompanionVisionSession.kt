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

import com.lanxin.android.builtin.platform.domain.SceneSensingConfig
import com.lanxin.android.builtin.platform.domain.SceneSensingGate

/**
 * 全屏陪伴「看世界」会话门闸（纯逻辑）。
 *
 * 复用 #99 [SceneSensingGate] 的 consent / camera 语义；
 * 陪伴页另有 session 级 [lookingEnabled]（默认关，离开页即停）。
 */
object CompanionVisionSession {

    const val FEATURE_NAME = "companion_vision_explain"

    const val STATUS_LOOKING = "正在看"
    const val STATUS_PAUSED = "已暂停"
    const val STATUS_OFF = "看世界·关"

    /**
     * 是否允许打开预览 / 抓帧。
     * @param lookingEnabled 陪伴页「看世界」开关（session）
     * @param consentGranted 来自 #99 SceneSensingConfig
     * @param cameraGranted 系统 CAMERA
     */
    fun canUseCamera(
        lookingEnabled: Boolean,
        consentGranted: Boolean,
        cameraGranted: Boolean
    ): Boolean {
        if (!lookingEnabled) return false
        // 与 #99 对齐：无 consent 绝不拍
        val config = SceneSensingConfig(enabled = true, consentGranted = consentGranted)
        return SceneSensingGate.canCapture(config, cameraGranted = cameraGranted)
    }

    fun needsConsentDialog(consentGranted: Boolean, turningOn: Boolean): Boolean {
        return SceneSensingGate.needsConsentDialog(
            SceneSensingConfig(consentGranted = consentGranted),
            turningOn = turningOn
        )
    }

    fun denyReason(
        lookingEnabled: Boolean,
        consentGranted: Boolean,
        cameraGranted: Boolean
    ): String? {
        if (!lookingEnabled) return "companion_vision_off"
        val config = SceneSensingConfig(enabled = true, consentGranted = consentGranted)
        return SceneSensingGate.denyReason(config, cameraGranted = cameraGranted)
    }

    fun statusLabel(lookingEnabled: Boolean, previewReady: Boolean): String {
        if (!lookingEnabled) return STATUS_OFF
        return if (previewReady) STATUS_LOOKING else "看世界·准备中"
    }

    /**
     * 用户提问时是否应尝试抓帧（P0 帧策略）。
     * 仅 looking 开 + consent + 相机权限。
     */
    fun shouldCaptureOnAsk(
        lookingEnabled: Boolean,
        consentGranted: Boolean,
        cameraGranted: Boolean
    ): Boolean = canUseCamera(lookingEnabled, consentGranted, cameraGranted)
}
