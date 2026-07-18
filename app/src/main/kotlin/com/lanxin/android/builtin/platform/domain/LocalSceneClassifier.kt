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

/**
 * 本地场景标签（无 ML、无网络）。
 *
 * 仅用于映射**现有**陪伴背景 / mood 提示，不对应任何外部模型类名。
 */
enum class SceneLabel(
    val id: String,
    val displayName: String
) {
    DAYLIGHT("daylight", "晴天/明亮"),
    NIGHT("night", "夜色/昏暗"),
    SUNSET_WARM("sunset_warm", "暖色/晚霞"),
    GREEN_NATURE("green_nature", "绿色/自然"),
    COOL_INDOOR("cool_indoor", "冷色/室内"),
    UNKNOWN("unknown", "未识别")
    ;

    companion object {
        fun fromId(raw: String?): SceneLabel {
            val key = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.id == key } ?: UNKNOWN
        }
    }
}

/**
 * 从像素抽样得到的本地特征（0–1 归一）。
 *
 * 不持有 Bitmap；测试可用合成像素。
 */
data class SceneFeatures(
    /** 平均亮度 (0 黑 → 1 白) */
    val avgBrightness: Float,
    /** 暖色偏向：R 相对 B 的优势 (约 -1…1，正=偏暖) */
    val warmBias: Float,
    /** 绿色通道占比优势 (约 0…1) */
    val greenDominance: Float,
    /** 蓝色通道占比优势 (约 0…1) */
    val blueDominance: Float
)

/**
 * 场景 → 现有陪伴效果（背景预设 / mood / 状态文案）。
 *
 * **禁止**发明 Live2D 资源；mood 必须落 [com.lanxin.android.builtin.pet.domain.MoodTagMapper.ALLOWED_MOODS]；
 * 背景必须落 [com.lanxin.android.builtin.pet.domain.CompanionBackgrounds.PRESETS]。
 */
data class SceneCompanionEffect(
    val scene: SceneLabel,
    /** [CompanionBackgrounds] 预设 id；null = 不改背景 */
    val backgroundPresetId: String?,
    /** 可选 mood 提示（仅文案/后续注入，不直接播不存在的 exp） */
    val moodHint: String?,
    /** 陪伴/设置页状态文案 */
    val statusText: String
)

/**
 * 本地启发式场景分类 + 陪伴映射（纯逻辑）。
 */
object LocalSceneClassifier {

    /**
     * 从 ARGB 像素抽样特征。
     * @param step 抽样步长（≥1）；大图可增大步长
     */
    fun featuresFromArgb(pixels: IntArray, step: Int = 1): SceneFeatures {
        if (pixels.isEmpty()) {
            return SceneFeatures(0.5f, 0f, 0f, 0f)
        }
        val s = step.coerceAtLeast(1)
        var n = 0
        var sumL = 0.0
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var i = 0
        while (i < pixels.size) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            // 感知亮度近似
            val l = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            sumL += l
            sumR += r
            sumG += g
            sumB += b
            n++
            i += s
        }
        if (n == 0) {
            return SceneFeatures(0.5f, 0f, 0f, 0f)
        }
        val avgR = (sumR / n) / 255.0
        val avgG = (sumG / n) / 255.0
        val avgB = (sumB / n) / 255.0
        val avgL = (sumL / n).toFloat().coerceIn(0f, 1f)
        val warm = ((avgR - avgB) * 2.0).toFloat().coerceIn(-1f, 1f)
        val maxC = maxOf(avgR, avgG, avgB).coerceAtLeast(1e-6)
        val greenDom = (avgG / maxC).toFloat().coerceIn(0f, 1f)
        val blueDom = (avgB / maxC).toFloat().coerceIn(0f, 1f)
        return SceneFeatures(
            avgBrightness = avgL,
            warmBias = warm,
            greenDominance = greenDom,
            blueDominance = blueDom
        )
    }

    /** 启发式分类：明亮 / 夜 / 暖霞 / 绿植 / 冷室内 / 未知。 */
    fun classify(features: SceneFeatures): SceneLabel {
        val l = features.avgBrightness
        val warm = features.warmBias
        val g = features.greenDominance
        val b = features.blueDominance

        // 明显偏绿且不太暗 → 自然
        if (g >= 0.72f && l in 0.18f..0.85f && g > b + 0.08f) {
            return SceneLabel.GREEN_NATURE
        }
        // 很暗
        if (l < 0.22f) {
            return SceneLabel.NIGHT
        }
        // 暖 + 中等亮 → 晚霞/暖光
        if (warm >= 0.18f && l in 0.28f..0.78f) {
            return SceneLabel.SUNSET_WARM
        }
        // 很亮 + 偏蓝/中性 → 晴天
        if (l >= 0.62f && warm <= 0.12f) {
            return SceneLabel.DAYLIGHT
        }
        // 中低亮 + 偏冷
        if (l < 0.55f && b >= 0.55f && warm <= 0f) {
            return SceneLabel.COOL_INDOOR
        }
        // 中等偏亮兜底晴天
        if (l >= 0.55f) {
            return SceneLabel.DAYLIGHT
        }
        if (l < 0.35f) {
            return SceneLabel.NIGHT
        }
        return SceneLabel.UNKNOWN
    }

    /**
     * 映射到现有陪伴能力。
     *
     * 背景 id 仅 [knownBackgroundIds] 内；mood 仅 [allowedMoods] 内。
     * 默认传入 CompanionBackgrounds / MoodTagMapper 的合法集合。
     */
    fun toCompanionEffect(
        scene: SceneLabel,
        knownBackgroundIds: Set<String>,
        allowedMoods: Set<String>
    ): SceneCompanionEffect {
        val (bg, mood, text) = when (scene) {
            SceneLabel.DAYLIGHT -> Triple("sky", "smile", "场景：晴天 · 已映射晴空背景")
            SceneLabel.NIGHT -> Triple("night", "idle", "场景：夜色 · 已映射夜色背景")
            SceneLabel.SUNSET_WARM -> Triple("sunset", "joy", "场景：暖色 · 已映射晚霞背景")
            SceneLabel.GREEN_NATURE -> Triple("mint", "smile", "场景：绿色 · 已映射薄荷绿背景")
            SceneLabel.COOL_INDOOR -> Triple("lavender", "think", "场景：冷色室内 · 已映射薰衣紫背景")
            SceneLabel.UNKNOWN -> Triple(null, null, "场景：未识别 · 未改背景")
        }
        val safeBg = bg?.takeIf { it in knownBackgroundIds }
        val safeMood = mood?.takeIf { it in allowedMoods }
        return SceneCompanionEffect(
            scene = scene,
            backgroundPresetId = safeBg,
            moodHint = safeMood,
            statusText = text
        )
    }

    /** 一站式：特征 → 效果。 */
    fun analyzeToEffect(
        features: SceneFeatures,
        knownBackgroundIds: Set<String>,
        allowedMoods: Set<String>
    ): SceneCompanionEffect {
        return toCompanionEffect(classify(features), knownBackgroundIds, allowedMoods)
    }
}
