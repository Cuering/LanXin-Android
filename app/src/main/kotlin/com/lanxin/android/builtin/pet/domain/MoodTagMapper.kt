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
 * 回复文本中的显式情绪标签 `[[mood=…]]` → Mao 官方 exp/motion。
 *
 * ## 原则（哥哥定调）
 * - **不**发明模型没有的表情/动作
 * - mood 枚举 **从现有** [MaoOfficialMotionCatalog] / [PetExpressionController.Expression] **反推**
 * - 没有合适动作就：只播表情；没有合适表情就：不命中（回落关键词 / 相位默认）
 * - 生成侧只允许 [ALLOWED_MOODS] 内的值
 *
 * ## 标签语法
 * - 主协议：`[[mood=joy]]`（大小写不敏感）
 * - 兼容：`[[pet mood=joy]]` / `[[pet:joy]]`
 * - 同文多标签时取 **首个合法** mood
 * - 气泡 / TTS / 历史展示前用 [stripTags] 剥离全部标签
 *
 * ## mood → 资源对照（仅现有）
 * | mood | Expression | exp | Motion |
 * |------|------------|-----|--------|
 * | smile | IDLE_SMILE | exp_01 | Idle[0] |
 * | listen | LISTENING | exp_02 | — |
 * | think | THINKING | exp_03 | — |
 * | speak | SPEAKING | exp_04 | — |
 * | sorry | APOLOGY | exp_05 | — |
 * | idle | IDLE_VARIANT_A | exp_06 | — |
 * | joy | TAP_REACTION | exp_07 | TapBody[3] |
 * | music | MUSIC_PEAK | exp_08 | Idle[1] |
 * | tap | TAP_REACTION | exp_07 | TapBody[0] |
 *
 * 别名（归一到上表，不增资源）：happy/excited→joy；apology/sad→sorry；
 * greeting/neutral→smile；relax→idle。
 */
object MoodTagMapper {

    /** 生成侧 / 文档允许输出的 canonical mood（与资源一一对应）。 */
    val ALLOWED_MOODS: List<String> = listOf(
        "smile",
        "listen",
        "think",
        "speak",
        "sorry",
        "idle",
        "joy",
        "music",
        "tap"
    )

    /**
     * 匹配三类隐藏标签（仅用于 [match] 解析）：
     * - `[[mood=joy]]`
     * - `[[pet mood=joy]]`
     * - `[[pet:joy]]`
     */
    private val TAG_REGEX = Regex(
        """\[\[\s*(?:pet\s+)?mood\s*=\s*([a-zA-Z_]+)\s*]]|\[\[\s*pet\s*:\s*([a-zA-Z_]+)\s*]]""",
        RegexOption.IGNORE_CASE
    )

    /** 展示路径：任意 `[[…]]` 均剥（防御扩展标签 / 脏标签 / 非法 mood）。 */
    private val ANY_BRACKET_TAG_REGEX = Regex("""\[\[[^\]]*]]""")

    /**
     * 别名 → canonical。仅归一字符串，映射结果仍落 [ALLOWED_MOODS]。
     */
    private val ALIASES: Map<String, String> = mapOf(
        "happy" to "joy",
        "excited" to "joy",
        "apology" to "sorry",
        "sad" to "sorry",
        "greeting" to "smile",
        "neutral" to "smile",
        "relax" to "idle"
    )

    private data class MoodDef(
        val mood: String,
        val expression: PetExpressionController.Expression,
        val motionGroup: String? = null,
        val motionIndex: Int? = null,
        val shortLabel: String,
        val emoji: String
    )

    /**
     * 与 [TextExpressionMotionMapper.Match.ruleId] 对齐：用 `mood:<name>` 便于去重。
     */
    private val DEFS: Map<String, MoodDef> = listOf(
        MoodDef(
            mood = "smile",
            expression = PetExpressionController.Expression.IDLE_SMILE,
            motionGroup = MaoOfficialMotionCatalog.GROUP_IDLE,
            motionIndex = 0,
            shortLabel = "微笑",
            emoji = "😊"
        ),
        MoodDef(
            mood = "listen",
            expression = PetExpressionController.Expression.LISTENING,
            shortLabel = "在听",
            emoji = "👂"
        ),
        MoodDef(
            mood = "think",
            expression = PetExpressionController.Expression.THINKING,
            shortLabel = "想想",
            emoji = "💭"
        ),
        MoodDef(
            mood = "speak",
            expression = PetExpressionController.Expression.SPEAKING,
            shortLabel = "说话",
            emoji = "💬"
        ),
        MoodDef(
            mood = "sorry",
            expression = PetExpressionController.Expression.APOLOGY,
            shortLabel = "抱歉",
            emoji = "😅"
        ),
        MoodDef(
            mood = "idle",
            expression = PetExpressionController.Expression.IDLE_VARIANT_A,
            shortLabel = "闲变",
            emoji = "😌"
        ),
        MoodDef(
            mood = "joy",
            expression = PetExpressionController.Expression.TAP_REACTION,
            motionGroup = MaoOfficialMotionCatalog.GROUP_TAP_BODY,
            motionIndex = 3, // special_01
            shortLabel = "开心",
            emoji = "✨"
        ),
        MoodDef(
            mood = "music",
            expression = PetExpressionController.Expression.MUSIC_PEAK,
            motionGroup = MaoOfficialMotionCatalog.GROUP_IDLE,
            motionIndex = 1, // sample_01
            shortLabel = "律动",
            emoji = "🎵"
        ),
        MoodDef(
            mood = "tap",
            expression = PetExpressionController.Expression.TAP_REACTION,
            motionGroup = MaoOfficialMotionCatalog.GROUP_TAP_BODY,
            motionIndex = 0, // mtn_02
            shortLabel = "点触",
            emoji = "✨"
        )
    ).associateBy { it.mood }

    /** 是否为允许的 mood（含别名）。 */
    fun isAllowedMood(raw: String): Boolean {
        val key = normalize(raw) ?: return false
        return key in DEFS
    }

    /** 别名归一；未知 → null。 */
    fun normalize(raw: String): String? {
        val t = raw.trim().lowercase()
        if (t.isEmpty()) return null
        val canonical = ALIASES[t] ?: t
        return if (canonical in DEFS) canonical else null
    }

    /** 剥离全部 `[[…]]` 隐藏标签，压缩多余空白，供气泡 / TTS / 历史展示。 */
    fun stripTags(text: String): String {
        if (text.isEmpty() || !text.contains("[[", ignoreCase = false)) {
            return text
        }
        return ANY_BRACKET_TAG_REGEX.replace(text, " ")
            .replace(Regex("""[ \t]{2,}"""), " ")
            .replace(Regex(""" *\n *"""), "\n")
            .trim()
    }

    /**
     * 从文本解析首个合法 mood 标签 → [TextExpressionMotionMapper.Match]。
     * 无标签 / 非法 mood → null（调用方回落关键词）。
     */
    fun match(text: String): TextExpressionMotionMapper.Match? {
        if (text.isBlank()) return null
        val found = TAG_REGEX.findAll(text)
        for (m in found) {
            val raw = m.groupValues.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
                ?: m.groupValues.getOrNull(2)
                ?: continue
            val mood = normalize(raw) ?: continue
            val def = DEFS[mood] ?: continue
            // 与关键词路径一致：校验 motion 合法
            val group = def.motionGroup
            val index = def.motionIndex
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
            return TextExpressionMotionMapper.Match(
                expression = def.expression,
                motionGroup = group,
                motionIndex = index,
                ruleId = "mood:$mood",
                shortLabel = def.shortLabel,
                emoji = def.emoji
            )
        }
        return null
    }

    /** 供单测 / 生成提示：canonical 列表。 */
    fun knownMoods(): List<String> = ALLOWED_MOODS

    /** 别名表（只读视图）。 */
    fun knownAliases(): Map<String, String> = ALIASES
}
