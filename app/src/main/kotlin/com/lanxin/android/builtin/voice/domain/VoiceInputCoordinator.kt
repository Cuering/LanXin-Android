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

package com.lanxin.android.builtin.voice.domain

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chat / 设置页共用的语音输入门面。
 *
 * 边界：只产出文本，交给现有发送消息链路；不实现 tool_call，不阻塞 ChatRouter。
 *
 * Phase 6.4 P0：提供 [transcribePcm] 与 [previewStatus]。
 * Chat 麦克风听写通过 [ChatMicSession] 接入本 API（转写填入输入框，不自动发送）。
 */
@Singleton
class VoiceInputCoordinator @Inject constructor(
    private val engine: AsrEngine,
    private val settings: AsrSettings,
    private val permissionChecker: MicPermissionChecker
) {

    /**
     * 状态预览（设置页 / 调试）。
     */
    suspend fun previewStatus(): String {
        val config = settings.getConfig()
        val perm = permissionChecker.check()
        return buildString {
            append("enabled=")
            append(config.enabled)
            append(" · ready=")
            append(engine.isReady)
            append(" · state=")
            append(engine.state.value)
            append(" · lang=")
            append(config.language)
            append(" · mic=")
            append(perm)
            if (!engine.lastError.isNullOrBlank()) {
                append(" · err=")
                append(engine.lastError)
            }
        }
    }

    /**
     * 对已采集的 PCM 做转写（不触碰麦克风）。
     *
     * 设置页「试转写」与 Chat 侧录音结束后均可调用。
     */
    suspend fun transcribePcm(
        pcm16leMono: ByteArray,
        sampleRateHz: Int? = null,
        requireReady: Boolean = true
    ): Result<AsrTranscribeResult> {
        val config = settings.getConfig()
        if (!config.enabled) {
            return Result.failure(
                IllegalStateException(
                    MicPermissionGate.blockReason(
                        permission = MicPermissionState.GRANTED,
                        engineReady = false,
                        enabled = false,
                        requireMic = false
                    )
                )
            )
        }
        // 开关已开但未 load：调用前自动尝试加载（修复「开关开了却无法调用」）
        if (!engine.isReady) {
            if (config.modelPath.isBlank()) {
                return Result.failure(
                    IllegalStateException(
                        "语音识别模型路径为空。请到设置下载/导入 ASR 模型后再试。"
                    )
                )
            }
            val loaded = runCatching { engine.load(config) }.getOrDefault(false)
            if (!loaded && requireReady) {
                return Result.failure(
                    IllegalStateException(
                        engine.lastError
                            ?: MicPermissionGate.blockReason(
                                permission = MicPermissionState.GRANTED,
                                engineReady = false,
                                enabled = true,
                                requireMic = false
                            )
                            ?: "语音识别模型未就绪"
                    )
                )
            }
        }
        if (requireReady && !engine.isReady) {
            return Result.failure(
                IllegalStateException(
                    MicPermissionGate.blockReason(
                        permission = MicPermissionState.GRANTED,
                        engineReady = false,
                        enabled = true,
                        requireMic = false
                    )
                )
            )
        }
        return runCatching {
            engine.transcribe(
                AsrTranscribeRequest(
                    pcm16leMono = pcm16leMono,
                    sampleRateHz = sampleRateHz ?: config.sampleRateHz,
                    language = config.language
                )
            )
        }
    }

    /**
     * 在发起真实录音前检查权限与引擎（不录音）。
     *
     * @return null 可继续；否则为用户可见阻断文案
     */
    suspend fun preflightForRecording(): String? {
        val config = settings.getConfig()
        return MicPermissionGate.blockReason(
            permission = permissionChecker.check(),
            engineReady = engine.isReady,
            enabled = config.enabled,
            requireMic = true
        )
    }
}
