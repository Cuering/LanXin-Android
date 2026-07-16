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

import kotlinx.coroutines.flow.StateFlow

/**
 * 语音合成（TTS）引擎抽象。
 *
 * M1：接口 + [com.lanxin.android.builtin.voice.data.StubTtsEngine]。
 * 后续 Bert-VITS2 / JNI 替换实现，不改 [VoiceSessionCoordinator]。
 *
 * 非目标（本阶段）：打包真实 so / 模型 / 参考 wav。
 */
interface TtsEngine {

    val state: StateFlow<TtsEngineState>

    val isReady: Boolean

    val isAvailable: Boolean

    val lastError: String?

    suspend fun load(config: TtsConfig): Boolean

    suspend fun unload()

    /**
     * 文本 → 音频（stub 可返回空 PCM + 字幕文本）。
     *
     * @throws IllegalStateException 引擎未就绪
     */
    suspend fun synthesize(request: TtsSynthesizeRequest): TtsSynthesizeResult
}

/**
 * TTS 引擎状态。
 */
enum class TtsEngineState {
    DISABLED,
    IDLE,
    LOADING,
    READY,
    SPEAKING,
    ERROR
}

/**
 * TTS 配置快照。
 *
 * @property enabled 是否启用 TTS（桌宠会话可独立开关；默认关）
 * @property modelPath 兼容旧键 / 单文件路径（不入库大文件）
 * @property modelDir TTS 模型目录（DataStore `tts_model_dir`；一等公民）
 * @property referenceAudio 参考音 wav 路径（DataStore `tts_reference_audio`）
 * @property voiceId 音色 / 说话人 id（stub 忽略）
 * @property sampleRateHz 输出采样率
 */
data class TtsConfig(
    val enabled: Boolean = false,
    val modelPath: String = "",
    val modelDir: String = "",
    val referenceAudio: String = "",
    val voiceId: String = DEFAULT_VOICE_ID,
    val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ
) {
    companion object {
        const val DEFAULT_VOICE_ID = "lanxin"
        const val DEFAULT_SAMPLE_RATE_HZ = 22_050
    }
}

/**
 * 合成请求。
 */
data class TtsSynthesizeRequest(
    val text: String,
    val voiceId: String? = null,
    val sampleRateHz: Int? = null
)

/**
 * 合成结果。
 *
 * @property pcm16leMono 16-bit LE 单声道；stub 可为空数组
 * @property sampleRateHz 采样率
 * @property durationMs 预估时长
 * @property isStub 是否 stub
 * @property subtitle 气泡字幕（通常等于输入 text）
 */
data class TtsSynthesizeResult(
    val pcm16leMono: ByteArray,
    val sampleRateHz: Int,
    val durationMs: Long,
    val isStub: Boolean = false,
    val subtitle: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TtsSynthesizeResult) return false
        return pcm16leMono.contentEquals(other.pcm16leMono) &&
            sampleRateHz == other.sampleRateHz &&
            durationMs == other.durationMs &&
            isStub == other.isStub &&
            subtitle == other.subtitle
    }

    override fun hashCode(): Int {
        var result = pcm16leMono.contentHashCode()
        result = 31 * result + sampleRateHz
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + isStub.hashCode()
        result = 31 * result + subtitle.hashCode()
        return result
    }
}

/**
 * TTS 设置门面。
 */
interface TtsSettings {
    suspend fun getConfig(): TtsConfig
    suspend fun setEnabled(enabled: Boolean)
    suspend fun setModelPath(path: String?)
    suspend fun setModelDir(path: String?)
    suspend fun setReferenceAudio(path: String?)
    suspend fun setVoiceId(voiceId: String)
}
