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

package com.lanxin.android.builtin.localinference.domain

import com.lanxin.android.builtin.pet.domain.MoodTagMapper

/**
 * 本地模型回复展示清洗：剥离 think 块、无标签元分析、`[[…]]` 隐藏标签。
 *
 * 用于引擎出口 / 气泡 / TTS / 陪伴路径，保证用户默认只见正文。
 */
object LocalReplySanitizer {

    private const val THINK_OPEN = "<think>"
    private const val THINK_CLOSE = "</think>"

    /**
     * 默认 system 侧引导：短答、角色正文、不输出 think / 隐藏标签 / 元分析
     * （[LocalInferenceConfig.showThinking] 关闭时注入）。
     */
    const val NO_THINK_OR_TAGS_INSTRUCTION: String =
        "【输出约束】你是陪伴角色「兰心/兰儿」，用第一人称直接对用户说话。" +
            "只输出面向用户的可见短正文（问候约 1～2 句，日常不超过 4 句）。" +
            "禁止输出思考过程、工具检查、分析/理由/回应建议、Markdown 报告结构。" +
            "不要输出 <think>、</think>，不要输出 [[mood=…]]、[[listen]] 等双方括号隐藏标签或动作标签。" +
            "不要写「让我分析」「查看可用工具」「没有 greeting_tool」等元话术。"

    /**
     * 已闭合的 `<think>…</think>`（跨行、大小写不敏感）。
     */
    private val CLOSED_THINK_REGEX = Regex(
        """(?is)<think\b[^>]*>.*?</think\s*>"""
    )

    /** 孤立闭合标签。 */
    private val ORPHAN_THINK_CLOSE = Regex("""(?is)</think\s*>""")

    /** 常见「分析报告」分节标题行。 */
    private val META_SECTION_HEADER = Regex(
        """(?im)^\s{0,3}(?:#{1,6}\s*)?(?:\*\*)?(?:回应建议|分析|理由|判断|工具可用性|检查工具|注意事项)(?:\*\*)?[：:\s]*$"""
    )

    /** 元分析起手句。 */
    private val META_LEAD_LINE = Regex(
        """(?im)^\s*(?:让我分析一下|接下来分析|分析一下这个问题|查看可用工具|检查工具可用性|生成友好回应|注意[：:].*隐藏标签|只能用可见内容回复).*"""
    )

    /** 行内元话术关键词（整行丢弃）。 */
    private val META_LINE_MARKERS = listOf(
        "查看可用工具",
        "greeting_tool",
        "没有专门",
        "没有 greeting",
        "直接回复即可",
        "craft a warm",
        "Let me craft",
        "Let me analyze",
        "系统时间",
        "可用工具",
        "tool can",
        "不需要复杂的推理",
        "不需要调用工具",
        "自然语言回复即可",
        "系统已明确角色设定与输出规范"
    )

    /**
     * 清洗结果。
     *
     * @property displayText 气泡 / TTS 用干净正文（永远无 mood/动作标签；默认无 think）
     * @property thinkingText 剥离出的思考内容；[showThinking]=false 时为 null
     */
    data class CleanedReply(
        val displayText: String,
        val thinkingText: String? = null
    )

    /**
     * 从 [raw] 提取思考与正文。
     *
     * - 始终剥离 mood / 动作 `[[…]]` 标签
     * - 始终剥离无标签元分析块（分析/理由/工具检查等）
     * - [showThinking]=false：丢弃 think 块，只返回正文
     * - [showThinking]=true：正文仍无 think 标签；思考进 [CleanedReply.thinkingText]
     */
    fun clean(raw: String, showThinking: Boolean = false): CleanedReply {
        if (raw.isEmpty()) {
            return CleanedReply(displayText = "")
        }
        val thinking = extractThinking(raw)?.trim()?.takeIf { it.isNotEmpty() }
        val withoutThink = stripThinkingBlocks(raw)
        val withoutMeta = stripMetaAnalysis(withoutThink)
        val display = MoodTagMapper.stripTags(withoutMeta)
        return if (showThinking) {
            CleanedReply(displayText = display, thinkingText = thinking)
        } else {
            CleanedReply(displayText = display, thinkingText = null)
        }
    }

    /** 仅要干净气泡正文（mood/动作永不保留；think/元分析按规则剥）。 */
    fun forDisplay(raw: String, showThinking: Boolean = false): String =
        clean(raw, showThinking = showThinking).displayText

    /**
     * 剥离已闭合与未闭合的 think 块，返回剩余正文（可能仍含 `[[…]]` / 元分析）。
     */
    fun stripThinkingBlocks(text: String): String {
        if (text.isEmpty()) return text
        var result = text
        if (result.contains("think", ignoreCase = true)) {
            result = CLOSED_THINK_REGEX.replace(result, " ")
            // 未闭合：从首个 <think…> 起丢到文末
            val openIdx = indexOfIgnoreCase(result, THINK_OPEN)
            if (openIdx >= 0) {
                result = result.substring(0, openIdx)
            }
        }
        // 孤立 </think>
        result = ORPHAN_THINK_CLOSE.replace(result, " ")
        return collapseWhitespace(result)
    }

    /**
     * 剥离无标签「分析报告」式泄漏：起手句、分节标题、工具检查行、--- 后的分析/理由块。
     * 尽量保留真正面向用户的短句。
     */
    fun stripMetaAnalysis(text: String): String {
        if (text.isEmpty()) return text
        // 优先：`## 回应建议` 到下一 `---`/`**分析**` 之间若有正文则抽取
        val suggested = extractSuggestedReply(text)
        if (suggested != null) {
            return collapseWhitespace(suggested)
        }

        val lines = text.replace("\r\n", "\n").split('\n')
        val kept = mutableListOf<String>()
        var dropRest = false
        for (line in lines) {
            val trimmed = line.trim()
            if (dropRest) continue
            if (trimmed.isEmpty()) {
                if (kept.isNotEmpty() && kept.last().isNotEmpty()) kept += ""
                continue
            }
            // 水平线后若下一段是分析，丢弃后续
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                // 若已有可见正文，后面大概率是分析区
                if (kept.any { it.isNotBlank() }) {
                    dropRest = true
                }
                continue
            }
            if (META_SECTION_HEADER.containsMatchIn(trimmed)) {
                // 「## 回应建议」本身丢；后续正文在 extract 失败时按行继续过滤
                if (trimmed.contains("分析") || trimmed.contains("理由") ||
                    trimmed.contains("判断") || trimmed.contains("工具")
                ) {
                    dropRest = true
                }
                continue
            }
            if (META_LEAD_LINE.containsMatchIn(trimmed)) {
                continue
            }
            if (META_LINE_MARKERS.any { trimmed.contains(it, ignoreCase = true) }) {
                continue
            }
            // 编号列表式「1. 用户是…」元分析
            if (Regex("""^\d+[\.、]\s*(用户|检查|生成|注意|查看)""").containsMatchIn(trimmed)) {
                continue
            }
            // 粗体键值 **分析：**
            if (Regex("""^\*\*[^*]+\*\*[：:]?""").containsMatchIn(trimmed) &&
                (trimmed.contains("分析") || trimmed.contains("理由") || trimmed.contains("判断"))
            ) {
                dropRest = true
                continue
            }
            kept += line
        }
        val joined = kept.joinToString("\n")
        return collapseWhitespace(joined)
    }

    /**
     * 从「## 回应建议」块抽出真正回复；失败返回 null。
     */
    fun extractSuggestedReply(text: String): String? {
        val m = Regex(
            """(?is)(?:^|\n)\s{0,3}#{1,6}\s*回应建议\s*\n+(.*?)(?=\n\s*---|\n\s*\*\*分析|\n\s*#{1,6}\s*分析|\z)"""
        ).find(text) ?: return null
        val body = m.groupValues[1]
            .lines()
            .map { it.trimEnd() }
            .filter { line ->
                val t = line.trim()
                t.isNotEmpty() &&
                    t != "---" &&
                    !META_SECTION_HEADER.containsMatchIn(t) &&
                    !META_LEAD_LINE.containsMatchIn(t) &&
                    META_LINE_MARKERS.none { t.contains(it, ignoreCase = true) }
            }
            .joinToString("\n")
            .trim()
        return body.takeIf { it.isNotEmpty() && !looksLikePureMeta(it) }
    }

    /** 提取所有 think 内容（闭合块 + 未闭合尾部），供 UI 折叠展示。 */
    fun extractThinking(text: String): String? {
        if (text.isEmpty() || !text.contains("think", ignoreCase = true)) {
            return null
        }
        val parts = mutableListOf<String>()
        CLOSED_THINK_REGEX.findAll(text).forEach { m ->
            val inner = m.value
                .replace(Regex("""(?is)^<think\b[^>]*>"""), "")
                .replace(Regex("""(?is)</think\s*>$"""), "")
                .trim()
            if (inner.isNotEmpty()) parts += inner
        }
        // 未闭合尾部
        val afterClosed = CLOSED_THINK_REGEX.replace(text, "")
        val openIdx = indexOfIgnoreCase(afterClosed, THINK_OPEN)
        if (openIdx >= 0) {
            val fromOpen = afterClosed.substring(openIdx)
            val afterTag = fromOpen.indexOf('>').let { if (it >= 0) fromOpen.substring(it + 1) else "" }
            val tail = afterTag.trim()
            if (tail.isNotEmpty()) parts += tail
        }
        return parts.joinToString("\n").trim().takeIf { it.isNotEmpty() }
    }

    /**
     * 在 system prompt 上按需追加「不输出 think/标签/元分析」约束。
     * [showThinking]=true 时不追加（允许模型输出 think 供折叠 UI）。
     */
    fun appendOutputConstraint(systemPrompt: String?, showThinking: Boolean): String? {
        if (showThinking) {
            return systemPrompt?.trim()?.takeIf { it.isNotEmpty() }
        }
        val base = systemPrompt?.trim().orEmpty()
        if (base.contains("【输出约束】") && base.contains("不要输出")) {
            return base.ifEmpty { null }
        }
        return if (base.isEmpty()) {
            NO_THINK_OR_TAGS_INSTRUCTION
        } else {
            "$base\n\n$NO_THINK_OR_TAGS_INSTRUCTION"
        }
    }

    private fun looksLikePureMeta(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true
        if (META_LEAD_LINE.containsMatchIn(t)) return true
        if (META_LINE_MARKERS.any { t.contains(it, ignoreCase = true) }) return true
        if (t.contains("分析：") || t.contains("**分析**")) return true
        return false
    }

    private fun collapseWhitespace(text: String): String =
        text
            .replace(Regex("""[ \t]{2,}"""), " ")
            .replace(Regex(""" *\n[ \t]*"""), "\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()

    private fun indexOfIgnoreCase(haystack: String, needle: String): Int {
        val h = haystack.lowercase()
        val n = needle.lowercase()
        return h.indexOf(n)
    }
}
