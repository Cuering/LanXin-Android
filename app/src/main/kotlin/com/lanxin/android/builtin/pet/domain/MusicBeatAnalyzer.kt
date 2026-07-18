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

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 将 Visualizer 波形/FFT 平滑成 0..1 节拍能量（纯逻辑，可单测）。
 *
 * - 波形优先：按 RMS 归一化
 * - 无波形时用 FFT 低频 bin 平均幅值
 * - EMA 平滑，避免暴力乱抖
 */
object MusicBeatAnalyzer {

    const val DEFAULT_SMOOTH = 0.28f
    const val DEFAULT_GAIN = 2.4f

    /**
     * @param waveform Visualizer captureWaveForm 输出（0..255 无符号字节，中心约 128）
     * @param fft 可选 FFT 字节（可 null）
     * @param previous 上一帧平滑值
     * @param smooth EMA 系数 0..1，越大越跟手
     * @param gain 增益
     */
    fun levelFromCapture(
        waveform: ByteArray?,
        fft: ByteArray? = null,
        previous: Float = 0f,
        smooth: Float = DEFAULT_SMOOTH,
        gain: Float = DEFAULT_GAIN
    ): Float {
        val raw = when {
            waveform != null && waveform.isNotEmpty() -> rmsFromWaveform(waveform)
            fft != null && fft.size >= 4 -> energyFromFft(fft)
            else -> 0f
        }
        val boosted = min(1f, max(0f, raw * gain))
        val s = smooth.coerceIn(0.05f, 0.95f)
        return previous * (1f - s) + boosted * s
    }

    fun rmsFromWaveform(waveform: ByteArray): Float {
        if (waveform.isEmpty()) return 0f
        var sum = 0.0
        for (b in waveform) {
            val centered = (b.toInt() and 0xFF) - 128
            sum += centered * centered
        }
        val rms = sqrt(sum / waveform.size)
        // 典型语音/音乐 RMS 约 8–40；映射到 0..1
        return min(1f, (rms / 48.0).toFloat())
    }

    fun energyFromFft(fft: ByteArray): Float {
        // byte[0]=dc, [1]=nyquist; 之后成对 re/im
        if (fft.size < 6) return 0f
        var sum = 0.0
        var n = 0
        // 低频前 1/8 bin
        val limit = min(fft.size - 1, max(6, fft.size / 8))
        var i = 2
        while (i + 1 < limit) {
            val re = fft[i].toInt()
            val im = fft[i + 1].toInt()
            sum += re * re + im * im
            n++
            i += 2
        }
        if (n == 0) return 0f
        val mean = sqrt(sum / n)
        return min(1f, (mean / 90.0).toFloat())
    }

    /** 无 Visualizer 时的伪节拍（2Hz 呼吸增强），仅作降级。 */
    fun fallbackPulse(elapsedMs: Long, previous: Float = 0f): Float {
        val phase = (elapsedMs % 500L) / 500.0
        val pulse = (0.5 + 0.5 * kotlin.math.sin(phase * Math.PI * 2)).toFloat() * 0.35f
        return previous * 0.7f + pulse * 0.3f
    }
}
