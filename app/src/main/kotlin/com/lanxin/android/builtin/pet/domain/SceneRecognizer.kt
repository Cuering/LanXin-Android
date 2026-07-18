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
 * 从帧统计识别场景（无 ML、无网络）。
 *
 * 输入为下采样像素统计，**不**依赖 CameraX / GPU。
 */
interface SceneRecognizer {
    fun recognize(stats: FrameStats, nowMs: Long = System.currentTimeMillis()): SceneRecognitionResult
}

/**
 * 一帧的轻量统计（由 Bitmap 下采样得到，单测可直接构造）。
 *
 * @param meanLuma 平均亮度 0..1
 * @param meanR 平均 R 0..1
 * @param meanG 平均 G 0..1
 * @param meanB 平均 B 0..1
 * @param sampleCount 采样像素数
 */
data class FrameStats(
    val meanLuma: Float,
    val meanR: Float,
    val meanG: Float,
    val meanB: Float,
    val sampleCount: Int
) {
    /** 粗略色温：>0 偏暖，<0 偏冷 */
    val warmth: Float
        get() = meanR - meanB
}

/**
 * 启发式场景识别：亮度 + 冷暖。
 * 仅输出 [SceneLabel]，不映射 Live2D 资源。
 */
@Singleton
class HeuristicSceneRecognizer @Inject constructor() : SceneRecognizer {

    override fun recognize(stats: FrameStats, nowMs: Long): SceneRecognitionResult {
        if (stats.sampleCount <= 0) {
            return SceneRecognitionResult(
                label = SceneLabel.UNKNOWN,
                confidence = 0f,
                capturedAtMs = nowMs,
                summary = "empty"
            )
        }
        val luma = stats.meanLuma.coerceIn(0f, 1f)
        val warmth = stats.warmth
        val (label, conf) = classify(luma, warmth)
        return SceneRecognitionResult(
            label = label,
            confidence = conf,
            capturedAtMs = nowMs,
            summary = "luma=%.2f warmth=%.2f n=%d".format(luma, warmth, stats.sampleCount)
        )
    }

    internal fun classify(luma: Float, warmth: Float): Pair<SceneLabel, Float> {
        return when {
            luma < 0.12f -> SceneLabel.NIGHT to 0.85f
            luma < 0.28f -> SceneLabel.DIM_INDOOR to 0.75f
            luma > 0.72f && warmth < 0.04f -> SceneLabel.OUTDOOR_DAY to 0.7f
            luma >= 0.28f && warmth > 0.08f -> SceneLabel.WARM_DESK to 0.65f
            luma >= 0.28f && warmth < -0.06f -> SceneLabel.COOL_SCREEN to 0.65f
            luma >= 0.28f -> SceneLabel.BRIGHT_INDOOR to 0.6f
            else -> SceneLabel.UNKNOWN to 0.3f
        }
    }
}

/**
 * 从 ARGB 像素数组下采样统计（纯 Kotlin，单测友好）。
 */
object FrameStatsSampler {

    /**
     * @param pixels ARGB 打包像素
     * @param width 宽
     * @param height 高
     * @param step 采样步长（>=1）
     */
    fun fromArgbPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        step: Int = 8
    ): FrameStats {
        if (width <= 0 || height <= 0 || pixels.isEmpty()) {
            return FrameStats(0f, 0f, 0f, 0f, 0)
        }
        val stride = step.coerceAtLeast(1)
        var sumY = 0.0
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var n = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val idx = y * width + x
                if (idx in pixels.indices) {
                    val c = pixels[idx]
                    val r = ((c shr 16) and 0xFF) / 255.0
                    val g = ((c shr 8) and 0xFF) / 255.0
                    val b = (c and 0xFF) / 255.0
                    // Rec. 601 luma
                    val luma = 0.299 * r + 0.587 * g + 0.114 * b
                    sumY += luma
                    sumR += r
                    sumG += g
                    sumB += b
                    n++
                }
                x += stride
            }
            y += stride
        }
        if (n == 0) return FrameStats(0f, 0f, 0f, 0f, 0)
        return FrameStats(
            meanLuma = (sumY / n).toFloat(),
            meanR = (sumR / n).toFloat(),
            meanG = (sumG / n).toFloat(),
            meanB = (sumB / n).toFloat(),
            sampleCount = n
        )
    }
}
