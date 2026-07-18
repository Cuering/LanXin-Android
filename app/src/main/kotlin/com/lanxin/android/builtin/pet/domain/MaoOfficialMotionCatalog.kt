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
 * Mao 官方免费资源清单与会话映射（与 assets/pet/live2d/Mao 及 model3 一致）。
 *
 * 只允许引用本清单内的 exp/motion 文件名；禁止套用其他模型动作。
 *
 * ## 表情映射
 * | 语义 | Cubism exp | 触发 |
 * |------|------------|------|
 * | IDLE_SMILE | exp_01 | 闲置默认 |
 * | LISTENING | exp_02 | 听用户说 |
 * | THINKING | exp_03 | 推理中 |
 * | SPEAKING | exp_04 | 播报回复 |
 * | APOLOGY | exp_05 | 出错 |
 * | IDLE_VARIANT_A | exp_06 | 闲置随机变体 |
 * | TAP_REACTION | exp_07 | 点触后短表情 |
 * | MUSIC_PEAK | exp_08 | 音乐高潮弱表情 |
 * | FALLBACK_NEUTRAL | exp_01 | 降级占位 |
 *
 * ## 动作组（model3 Groups）
 * - Idle：mtn_01, sample_01（循环/轮换）
 * - TapBody：mtn_02, mtn_03, mtn_04, special_01, special_02, special_03
 */
object MaoOfficialMotionCatalog {

    const val GROUP_IDLE = "Idle"
    const val GROUP_TAP_BODY = "TapBody"

    /** 全部官方表情名（与 model3 Expressions.Name 一致）。 */
    val ALL_EXPRESSIONS: List<String> = listOf(
        "exp_01",
        "exp_02",
        "exp_03",
        "exp_04",
        "exp_05",
        "exp_06",
        "exp_07",
        "exp_08"
    )

    /** Idle 组 motion 文件（相对 motions/）。 */
    val IDLE_MOTION_FILES: List<String> = listOf(
        "mtn_01.motion3.json",
        "sample_01.motion3.json"
    )

    /** TapBody 组 motion 文件。 */
    val TAP_BODY_MOTION_FILES: List<String> = listOf(
        "mtn_02.motion3.json",
        "mtn_03.motion3.json",
        "mtn_04.motion3.json",
        "special_01.motion3.json",
        "special_02.motion3.json",
        "special_03.motion3.json"
    )

    /** 全部官方 motion 文件。 */
    val ALL_MOTION_FILES: List<String> = IDLE_MOTION_FILES + TAP_BODY_MOTION_FILES

    /**
     * [PetExpressionController.Expression] → Cubism exp 名。
     * 相位主映射 01–05；扩展 06–08 见 [Expression] 扩展档。
     */
    fun expressionFileFor(expression: PetExpressionController.Expression): String {
        return when (expression) {
            PetExpressionController.Expression.IDLE_SMILE -> "exp_01"
            PetExpressionController.Expression.LISTENING -> "exp_02"
            PetExpressionController.Expression.THINKING -> "exp_03"
            PetExpressionController.Expression.SPEAKING -> "exp_04"
            PetExpressionController.Expression.APOLOGY -> "exp_05"
            PetExpressionController.Expression.IDLE_VARIANT_A -> "exp_06"
            PetExpressionController.Expression.TAP_REACTION -> "exp_07"
            PetExpressionController.Expression.MUSIC_PEAK -> "exp_08"
            PetExpressionController.Expression.FALLBACK_NEUTRAL -> "exp_01"
        }
    }

    /** 会话相位 → 主表情档（01–05）。 */
    fun phaseExpression(phase: VoiceSessionPhase): PetExpressionController.Expression {
        return when (phase) {
            VoiceSessionPhase.IDLE -> PetExpressionController.Expression.IDLE_SMILE
            VoiceSessionPhase.LISTENING -> PetExpressionController.Expression.LISTENING
            VoiceSessionPhase.THINKING -> PetExpressionController.Expression.THINKING
            VoiceSessionPhase.SPEAKING -> PetExpressionController.Expression.SPEAKING
            VoiceSessionPhase.ERROR -> PetExpressionController.Expression.APOLOGY
        }
    }

    /**
     * 点触应播放的 motion 组名（model3 中为 TapBody）。
     * index 由 Web 侧轮询/随机；此处只保证组名合法。
     */
    fun tapMotionGroup(): String = GROUP_TAP_BODY

    /** 闲置自动播放组。 */
    fun idleMotionGroup(): String = GROUP_IDLE

    /** Idle 组内可用 index 范围：0 until size。 */
    fun idleMotionCount(): Int = IDLE_MOTION_FILES.size

    /** TapBody 组内可用 index 范围。 */
    fun tapBodyMotionCount(): Int = TAP_BODY_MOTION_FILES.size

    fun isKnownExpression(name: String): Boolean =
        name in ALL_EXPRESSIONS

    fun isKnownMotionFile(fileName: String): Boolean {
        val bare = fileName.substringAfterLast('/')
        return bare in ALL_MOTION_FILES
    }

    /** 校验：清单与 DebugAssetCatalog 中的 Mao 文件一致（单测用）。 */
    fun expectedRelativePaths(): List<String> {
        return ALL_EXPRESSIONS.map { "expressions/$it.exp3.json" } +
            ALL_MOTION_FILES.map { "motions/$it" }
    }
}
