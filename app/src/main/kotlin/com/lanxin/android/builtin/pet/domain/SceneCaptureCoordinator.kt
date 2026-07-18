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

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 场景采集协调：门闸 → 识别 → 会话缓存。
 *
 * 实际打开摄像头由 UI/平台层提供 [FrameStats]（或后续 CameraX 一帧回调）；
 * 本类保持纯逻辑，便于单测且不绑硬件。
 */
@Singleton
class SceneCaptureCoordinator @Inject constructor(
    private val settings: SceneRecognitionSettings,
    private val recognizer: SceneRecognizer,
    private val session: SceneRecognitionSession
) {

    sealed class CaptureOutcome {
        data class Success(val result: SceneRecognitionResult) : CaptureOutcome()
        data class Denied(val code: String) : CaptureOutcome()
    }

    suspend fun currentConfig(): SceneRecognitionConfig = settings.getConfig()

    /**
     * 在已通过门闸且调用方已拿到帧统计时执行识别并写入会话。
     */
    suspend fun captureWithStats(
        stats: FrameStats,
        nowMs: Long = System.currentTimeMillis()
    ): CaptureOutcome {
        val config = settings.getConfig()
        val denied = SceneRecognitionGate.denyIfCannotCapture(config)
        if (denied != null) {
            return CaptureOutcome.Denied(denied)
        }
        val result = recognizer.recognize(stats, nowMs)
        session.publish(result)
        return CaptureOutcome.Success(result)
    }

    /**
     * 开启：必须 [userConfirmed]=true；成功返回新配置。
     */
    suspend fun enableWithConsent(userConfirmed: Boolean): SceneRecognitionConfig? {
        val current = settings.getConfig()
        val next = SceneRecognitionGate.tryEnable(current, userConfirmed) ?: return null
        settings.update(next)
        return next
    }

    /**
     * 关闭并清空会话缓存。
     */
    suspend fun disableAndClear(revokeConsent: Boolean = true): SceneRecognitionConfig {
        val current = settings.getConfig()
        val next = SceneRecognitionGate.disable(current, revokeConsent)
        settings.update(next)
        if (SceneRecognitionGate.shouldClearSessionOnDisable()) {
            session.clear()
        }
        return next
    }

    fun clearSessionOnly() {
        session.clear()
    }

    fun lastFeedback(): String? = session.feedbackLine()
}
