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
 * M2b 打磨：会话相位 → 表情 / 口型表现（纯逻辑，可单测）。
 *
 * WebView 占位 / Live2D 壳 / Cubism 真渲染共用同一套语义。
 * P3：Cubism 侧映射 ParamMouthOpenY + expression；失败仍降级壳。
 * 官方 Mao exp/motion 全量映射见 [MaoOfficialMotionCatalog]。
 */
object PetExpressionController {

    /**
     * 桌宠表情档位（Web / Bridge 线格式用 name）。
     *
     * 相位主映射 01–05；扩展 06–08 用于闲置变体 / 点触 / 音乐高潮。
     * Cubism 文件名见 [MaoOfficialMotionCatalog.expressionFileFor]。
     */
    enum class Expression {
        /** 闲置微笑 → exp_01 */
        IDLE_SMILE,

        /** 在听 → exp_02 */
        LISTENING,

        /** 思考 → exp_03 */
        THINKING,

        /** 说话 → exp_04 */
        SPEAKING,

        /** 出错 / 抱歉 → exp_05 */
        APOLOGY,

        /** 闲置随机变体 → exp_06 */
        IDLE_VARIANT_A,

        /** 点触后短表情 → exp_07 */
        TAP_REACTION,

        /** 音乐高潮弱表情 → exp_08 */
        MUSIC_PEAK,

        /** 降级提示态（资源缺失时仍可表达相位）→ exp_01 */
        FALLBACK_NEUTRAL
    }

    /**
     * 口型驱动强度 0.0–1.0。
     * SPEAKING 时给中高值供 HTML 做开合动画；其余相位闭嘴。
     */
    data class Pose(
        val expression: Expression,
        /** 0 闭嘴 · 1 最大开合目标 */
        val mouthOpen: Float,
        /** 是否应做说话口型循环动画 */
        val mouthAnimating: Boolean,
        val shortLabel: String,
        val emoji: String
    )

    /**
     * @param phase 当前 VoiceSession 相位
     * @param displayMode Live2D 显示模式（缺资源时仍按相位表情，标签可区分）
     */
    fun poseFor(
        phase: VoiceSessionPhase,
        displayMode: Live2dDisplayController.Live2dDisplayMode =
            Live2dDisplayController.Live2dDisplayMode.PLACEHOLDER
    ): Pose {
        val base = when (phase) {
            VoiceSessionPhase.IDLE -> Pose(
                expression = Expression.IDLE_SMILE,
                mouthOpen = 0f,
                mouthAnimating = false,
                shortLabel = "闲置",
                emoji = "😊"
            )
            VoiceSessionPhase.LISTENING -> Pose(
                expression = Expression.LISTENING,
                mouthOpen = 0f,
                mouthAnimating = false,
                shortLabel = "在听",
                emoji = "👂"
            )
            VoiceSessionPhase.THINKING -> Pose(
                expression = Expression.THINKING,
                mouthOpen = 0f,
                mouthAnimating = false,
                shortLabel = "想想",
                emoji = "💭"
            )
            VoiceSessionPhase.SPEAKING -> Pose(
                expression = Expression.SPEAKING,
                mouthOpen = 0.55f,
                mouthAnimating = true,
                shortLabel = "在说",
                emoji = "💬"
            )
            VoiceSessionPhase.ERROR -> Pose(
                expression = Expression.APOLOGY,
                mouthOpen = 0f,
                mouthAnimating = false,
                shortLabel = "出错",
                emoji = "😅"
            )
        }
        return if (displayMode == Live2dDisplayController.Live2dDisplayMode.FALLBACK &&
            phase == VoiceSessionPhase.IDLE
        ) {
            base.copy(
                expression = Expression.FALLBACK_NEUTRAL,
                shortLabel = "降级·闲置",
                emoji = "🙂"
            )
        } else {
            base
        }
    }

    /** 线格式：固定两位小数，便于 Web 解析。 */
    fun mouthOpenWire(value: Float): String =
        "%.2f".format(value.coerceIn(0f, 1f))

    /** Cubism exp 文件名（exp_01…exp_08），供 Bridge / 单测。 */
    fun cubismExpressionName(expression: Expression): String =
        MaoOfficialMotionCatalog.expressionFileFor(expression)

    /**
     * 闲置随机变体 pose（exp_06）；口型仍闭嘴。
     * Web 侧在 IDLE 长闲时偶发切换。
     */
    fun idleVariantPose(): Pose = Pose(
        expression = Expression.IDLE_VARIANT_A,
        mouthOpen = 0f,
        mouthAnimating = false,
        shortLabel = "闲变",
        emoji = "😌"
    )

    /** 点触反应表情（exp_07）。 */
    fun tapReactionPose(): Pose = Pose(
        expression = Expression.TAP_REACTION,
        mouthOpen = 0f,
        mouthAnimating = false,
        shortLabel = "点触",
        emoji = "✨"
    )

    /** 音乐高潮弱表情（exp_08）。 */
    fun musicPeakPose(): Pose = Pose(
        expression = Expression.MUSIC_PEAK,
        mouthOpen = 0f,
        mouthAnimating = false,
        shortLabel = "律动",
        emoji = "🎵"
    )

    fun guideForMissingResources(
        live2dReady: Boolean,
        asrReady: Boolean,
        ttsReady: Boolean
    ): String {
        if (live2dReady && asrReady && ttsReady) {
            return "路径均已就绪；悬浮层按会话相位驱动表情/口型（壳层，非 Cubism Core）。"
        }
        val missing = buildList {
            if (!live2dReady) add("Live2D")
            if (!asrReady) add("ASR")
            if (!ttsReady) add("TTS")
        }
        return "缺失 ${missing.joinToString("、")}：设置页「一键下载」到本机 LanXin/ 目录，" +
            "或可选脚本 fetch-debug-assets.sh。缺资源时仍可用占位/降级表情演示听→想→说。"
    }
}
