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
 * 文本 → 表情 / 动作规则映射（无 ML、无网络）。
 *
 * 设计：
 * - 优先级：显式 `[[mood=…]]`（[MoodTagMapper]）→ 关键词规则 → null（回落相位默认）。
 * - 仅在 SPEAKING 相位叠加（由调用方约束）。
 * - 表情只落 [PetExpressionController.Expression] / Mao exp_01…08。
 * - 动作只落 [MaoOfficialMotionCatalog] Idle / TapBody 组内 index。
 * - 关键词按列表顺序首命中；偏中文陪伴口语。
 *
 * 不复制任何商业角色文案；关键词为通用情绪/场景词。
 * mood 枚举从现有 exp/motion **反推**，禁止映射不存在的资源。
 */
object TextExpressionMotionMapper {

    /**
     * @param expression 覆盖 SPEAKING 默认 SPEAKING 表情
     * @param motionGroup null 表示不额外 PLAY_MOTION
     * @param motionIndex 组内序号；null 则 Web 侧随机/轮询
     * @param ruleId 稳定 id，便于去重（同 round 不重复推 motion）
     * @param shortLabel 可选短标签覆盖
     * @param emoji 可选 emoji 覆盖
     */
    data class Match(
        val expression: PetExpressionController.Expression,
        val motionGroup: String? = null,
        val motionIndex: Int? = null,
        val ruleId: String,
        val shortLabel: String? = null,
        val emoji: String? = null
    )

    /**
     * 规则表：`(ruleId, keywords, expression, motionGroup?, motionIndex?, label?, emoji?)`
     * 顺序即优先级。
     */
    private data class Rule(
        val id: String,
        val keywords: List<String>,
        val expression: PetExpressionController.Expression,
        val motionGroup: String? = null,
        val motionIndex: Int? = null,
        val shortLabel: String? = null,
        val emoji: String? = null
    )

    private val RULES: List<Rule> = listOf(
        // 道歉 / 出错
        Rule(
            id = "apology",
            keywords = listOf(
                "抱歉", "对不起", "出错", "失败", "糟糕", "不好意思", "没听清", "搞砸"
            ),
            expression = PetExpressionController.Expression.APOLOGY,
            shortLabel = "抱歉",
            emoji = "😅"
        ),
        // 兴奋 / 开心 → TapBody special
        Rule(
            id = "joy",
            keywords = listOf(
                "哈哈", "开心", "太好了", "好棒", "棒棒", "喜欢", "爱你",
                "耶", "哇", "太棒", "兴奋", "欢呼", "庆祝", "恭喜"
            ),
            expression = PetExpressionController.Expression.TAP_REACTION,
            motionGroup = MaoOfficialMotionCatalog.GROUP_TAP_BODY,
            motionIndex = 3, // special_01
            shortLabel = "开心",
            emoji = "✨"
        ),
        // 音乐 / 律动
        Rule(
            id = "music",
            keywords = listOf("音乐", "听歌", "律动", "节拍", "BGM", "bgm", "放歌", "唱歌"),
            expression = PetExpressionController.Expression.MUSIC_PEAK,
            motionGroup = MaoOfficialMotionCatalog.GROUP_IDLE,
            motionIndex = 1, // sample_01 稍活泼
            shortLabel = "律动",
            emoji = "🎵"
        ),
        // 点触 / 互动邀请
        Rule(
            id = "tap_invite",
            keywords = listOf("点我", "摸摸", "戳戳", "碰碰", "摸摸头", "拍拍"),
            expression = PetExpressionController.Expression.TAP_REACTION,
            motionGroup = MaoOfficialMotionCatalog.GROUP_TAP_BODY,
            motionIndex = 0, // mtn_02
            shortLabel = "点触",
            emoji = "✨"
        ),
        // 思考口吻（回复里自述在想）
        Rule(
            id = "think_tone",
            keywords = listOf("让我想", "想想看", "思考一下", "嗯…", "嗯...", "稍等"),
            expression = PetExpressionController.Expression.THINKING,
            shortLabel = "想想",
            emoji = "💭"
        ),
        // 问候 / 在场
        Rule(
            id = "greeting",
            keywords = listOf("你好", "在呢", "我在", "嗯嗯", "哈喽", "hello", "Hi", "hi"),
            expression = PetExpressionController.Expression.IDLE_SMILE,
            motionGroup = MaoOfficialMotionCatalog.GROUP_IDLE,
            motionIndex = 0,
            shortLabel = "问候",
            emoji = "😊"
        ),
        // 低落
        Rule(
            id = "sad",
            keywords = listOf("难过", "伤心", "呜呜", "哭", "失望", "郁闷"),
            expression = PetExpressionController.Expression.APOLOGY,
            shortLabel = "低落",
            emoji = "😔"
        ),
        // 闲变（轻松闲聊尾巴）
        Rule(
            id = "idle_variant",
            keywords = listOf("随便聊聊", "闲着", "发呆", "摸鱼"),
            expression = PetExpressionController.Expression.IDLE_VARIANT_A,
            shortLabel = "闲变",
            emoji = "😌"
        )
    )

    /**
     * 对任意文本匹配：先 [MoodTagMapper] 显式标签，再关键词；空串 / 无命中 → null。
     *
     * 关键词匹配不区分大小写（英文）；中文按包含。
     */
    fun match(text: String): Match? {
        val t = text.trim()
        if (t.isEmpty()) return null
        // 显式 mood 标签优先（只映射已有 exp/motion）
        MoodTagMapper.match(t)?.let { return it }
        // 关键词对「剥标签后」文本兜底，避免标签字面干扰
        val forKeywords = MoodTagMapper.stripTags(t).ifBlank { t }
        val lower = forKeywords.lowercase()
        for (rule in RULES) {
            val hit = rule.keywords.any { kw ->
                if (kw.any { ch -> ch.code < 128 && ch.isLetter() }) {
                    lower.contains(kw.lowercase())
                } else {
                    forKeywords.contains(kw)
                }
            }
            if (!hit) continue
            // 校验 motion 合法
            val group = rule.motionGroup
            val index = rule.motionIndex
            if (group != null) {
                require(
                    group == MaoOfficialMotionCatalog.GROUP_IDLE ||
                        group == MaoOfficialMotionCatalog.GROUP_TAP_BODY
                ) { "illegal motion group: $group" }
                if (index != null) {
                    val max = when (group) {
                        MaoOfficialMotionCatalog.GROUP_IDLE ->
                            MaoOfficialMotionCatalog.idleMotionCount()
                        else -> MaoOfficialMotionCatalog.tapBodyMotionCount()
                    }
                    require(index in 0 until max) { "motion index OOB: $group[$index]" }
                }
            }
            return Match(
                expression = rule.expression,
                motionGroup = group,
                motionIndex = index,
                ruleId = rule.id,
                shortLabel = rule.shortLabel,
                emoji = rule.emoji
            )
        }
        return null
    }

    /**
     * SPEAKING 相位：用文本规则覆盖表情，口型仍保持说话态。
     * 非 SPEAKING 或未命中 → 原 [phasePose]。
     */
    fun overlaySpeakingPose(
        phasePose: PetExpressionController.Pose,
        phase: VoiceSessionPhase,
        text: String?
    ): PetExpressionController.Pose {
        if (phase != VoiceSessionPhase.SPEAKING) return phasePose
        val m = match(text.orEmpty()) ?: return phasePose
        return phasePose.copy(
            expression = m.expression,
            shortLabel = m.shortLabel ?: phasePose.shortLabel,
            emoji = m.emoji ?: phasePose.emoji
            // mouthOpen / mouthAnimating 保持 SPEAKING
        )
    }

    /** 供单测 / 文档：已注册 ruleId 列表。 */
    fun knownRuleIds(): List<String> = RULES.map { it.id }
}
