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

/**
 * 摄像头场景识别配置（M5 最小可用）。
 *
 * **默认全关**：不打开摄像头、不采集帧、不写会话缓存。
 * 开启前必须用户明确确认（隐私说明）+ 系统 CAMERA 权限。
 *
 * 不绑定不存在的 Live2D exp/motion；结果仅轻量文案/状态反馈。
 */
data class SceneRecognitionConfig(
    /** 总开关；默认 false */
    val enabled: Boolean = false,
    /** 用户已在 App 内确认隐私说明；默认 false */
    val consentGranted: Boolean = false
) {
    companion object {
        const val PREFS_PREFIX = "scene_recognition_"
        const val FEATURE_NAME = "scene_recognition"
    }
}

/**
 * 轻量场景标签（规则/启发式，无 ML 大模型）。
 * 仅用于文案反馈，**禁止**映射到未入库的 Live2D 资源。
 */
enum class SceneLabel(
    val id: String,
    val displayName: String,
    val feedback: String
) {
    UNKNOWN("unknown", "未知", "还看不太清环境呢"),
    BRIGHT_INDOOR("bright_indoor", "明亮室内", "看起来光线挺好，室内环境"),
    DIM_INDOOR("dim_indoor", "偏暗室内", "有点暗哦，要注意眼睛"),
    OUTDOOR_DAY("outdoor_day", "户外日间", "像是户外的明亮场景"),
    NIGHT("night", "夜间/低光", "现在好暗，是夜里吗？"),
    WARM_DESK("warm_desk", "暖色桌面", "暖暖的色调，像在书桌前"),
    COOL_SCREEN("cool_screen", "冷色屏幕", "偏冷的光，是在看屏幕吗？");

    companion object {
        fun fromId(id: String?): SceneLabel {
            if (id.isNullOrBlank()) return UNKNOWN
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

/**
 * 单次识别结果（会话缓存条目，不落盘隐私图）。
 */
data class SceneRecognitionResult(
    val label: SceneLabel = SceneLabel.UNKNOWN,
    val confidence: Float = 0f,
    val capturedAtMs: Long = 0L,
    /** 调试用摘要（亮度/色温等），不含图像 */
    val summary: String = ""
) {
    val feedbackText: String
        get() = label.feedback
}
