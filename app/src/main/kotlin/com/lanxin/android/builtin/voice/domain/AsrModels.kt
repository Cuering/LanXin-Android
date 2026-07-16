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

/**
 * 离线 ASR 引擎状态。
 */
enum class AsrEngineState {
    /** 未启用（设置关闭）。 */
    DISABLED,

    /** 已启用但模型未加载。 */
    IDLE,

    /** 正在加载模型。 */
    LOADING,

    /** 模型就绪，可转写。 */
    READY,

    /** 加载或转写失败。 */
    ERROR
}

/**
 * 离线 ASR 配置快照。
 *
 * @property enabled 是否启用离线语音识别
 * @property modelPath 模型目录路径（绝对路径，不入库大文件）
 * @property language 识别语言标签（如 zh / en）
 * @property sampleRateHz 期望 PCM 采样率
 */
data class AsrConfig(
    val enabled: Boolean = false,
    val modelPath: String = "",
    val language: String = DEFAULT_LANGUAGE,
    val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ
) {
    companion object {
        const val DEFAULT_LANGUAGE = "zh"
        const val DEFAULT_SAMPLE_RATE_HZ = 16_000
        const val MIN_SAMPLE_RATE_HZ = 8_000
        const val MAX_SAMPLE_RATE_HZ = 48_000
    }
}

/**
 * 麦克风 / RECORD_AUDIO 权限状态（纯逻辑，便于单测）。
 */
enum class MicPermissionState {
    /** 已授予。 */
    GRANTED,

    /** 尚未申请或当前拒绝（可再请求）。 */
    DENIED,

    /** 用户勾选「不再询问」后的永久拒绝。 */
    PERMANENTLY_DENIED,

    /** 平台未知（JVM 单测 / 未注入检查器）。 */
    UNKNOWN
}

/**
 * 转写请求。
 *
 * @property pcm16leMono 16-bit little-endian 单声道 PCM
 * @property sampleRateHz 采样率；null 时用配置默认
 * @property language 覆盖配置语言
 */
data class AsrTranscribeRequest(
    val pcm16leMono: ByteArray,
    val sampleRateHz: Int? = null,
    val language: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AsrTranscribeRequest) return false
        return pcm16leMono.contentEquals(other.pcm16leMono) &&
            sampleRateHz == other.sampleRateHz &&
            language == other.language
    }

    override fun hashCode(): Int {
        var result = pcm16leMono.contentHashCode()
        result = 31 * result + (sampleRateHz ?: 0)
        result = 31 * result + (language?.hashCode() ?: 0)
        return result
    }
}

/**
 * 转写结果。
 *
 * @property text 识别文本
 * @property isPartial 是否为流式 partial
 * @property isStub 是否为 stub 引擎产物
 * @property confidence 可选置信度 0~1
 */
data class AsrTranscribeResult(
    val text: String,
    val isPartial: Boolean = false,
    val isStub: Boolean = false,
    val confidence: Float? = null
)

/**
 * 录音 → PCM 链路结果。
 */
data class RecordedAudio(
    val pcm16leMono: ByteArray,
    val sampleRateHz: Int,
    val durationMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecordedAudio) return false
        return pcm16leMono.contentEquals(other.pcm16leMono) &&
            sampleRateHz == other.sampleRateHz &&
            durationMs == other.durationMs
    }

    override fun hashCode(): Int {
        var result = pcm16leMono.contentHashCode()
        result = 31 * result + sampleRateHz
        result = 31 * result + durationMs.hashCode()
        return result
    }
}
