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

import android.graphics.Bitmap
import com.lanxin.android.builtin.pet.domain.CompanionBackgrounds
import com.lanxin.android.builtin.pet.domain.MoodTagMapper

/**
 * Bitmap → 场景效果（Android 边界薄封装；核心逻辑在 [LocalSceneClassifier]）。
 *
 * - 不落盘、不上传
 * - 调用方负责 recycle Bitmap
 * - 背景 / mood 只取现有合法集合
 */
object SceneCaptureAnalyzer {

    private val knownBackgroundIds: Set<String> =
        CompanionBackgrounds.PRESETS.map { it.id }.toSet()

    private val allowedMoods: Set<String> =
        MoodTagMapper.ALLOWED_MOODS.toSet()

    /**
     * @param maxSample 最多抽样像素数，控制 CPU
     */
    fun analyze(bitmap: Bitmap, maxSample: Int = 4096): SceneCompanionEffect {
        val w = bitmap.width.coerceAtLeast(1)
        val h = bitmap.height.coerceAtLeast(1)
        val total = w * h
        val step = ((total + maxSample - 1) / maxSample).coerceAtLeast(1)
        val pixels = IntArray(total)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val features = LocalSceneClassifier.featuresFromArgb(pixels, step)
        return LocalSceneClassifier.analyzeToEffect(
            features = features,
            knownBackgroundIds = knownBackgroundIds,
            allowedMoods = allowedMoods
        )
    }
}
