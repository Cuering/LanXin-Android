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

package com.lanxin.android.builtin.voice.data

import com.lanxin.android.builtin.voice.domain.AsrConfig
import com.lanxin.android.builtin.voice.domain.RecordedAudio
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 录音 → PCM 最小链路。
 *
 * Phase 6.4：默认提供 **stub 录音**（不打开麦克风），用于设置页「试转写」与 CI。
 * 真机 AudioRecord 接入时替换 [recordStubPcm] 或增加 RealPcmAudioRecorder。
 *
 * 产品约束：不在后台偷偷录音；仅由用户显式触发。
 */
@Singleton
class PcmAudioRecorder @Inject constructor() {

    /**
     * 生成一段可预测的 stub PCM（静音帧），不触碰硬件麦克风。
     *
     * @param durationMs 时长
     * @param sampleRateHz 采样率
     */
    suspend fun recordStubPcm(
        durationMs: Long = DEFAULT_STUB_DURATION_MS,
        sampleRateHz: Int = AsrConfig.DEFAULT_SAMPLE_RATE_HZ
    ): RecordedAudio = withContext(Dispatchers.Default) {
        val clampedMs = durationMs.coerceIn(50L, 10_000L)
        val rate = sampleRateHz.coerceIn(
            AsrConfig.MIN_SAMPLE_RATE_HZ,
            AsrConfig.MAX_SAMPLE_RATE_HZ
        )
        // 模拟采集耗时（短）
        delay(5)
        val samples = ((rate * clampedMs) / 1000L).toInt().coerceAtLeast(1)
        // 16-bit mono → 2 bytes/sample；填入极低幅值正弦近似（非纯 0，便于校验非空）
        val pcm = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val sample = ((i % 32) - 16).toShort()
            pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = (sample.toInt() shr 8).toByte()
        }
        RecordedAudio(
            pcm16leMono = pcm,
            sampleRateHz = rate,
            durationMs = clampedMs
        )
    }

    companion object {
        const val DEFAULT_STUB_DURATION_MS = 500L
    }
}
