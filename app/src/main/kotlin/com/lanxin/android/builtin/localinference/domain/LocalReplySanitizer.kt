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
 * 本地模型回复展示清洗：剥离 think 块与 `[[…]]` 隐藏标签。
 *
 * 用于引擎出口 / 气泡 / TTS / 陪伴路径，保证用户默认只见正文。
 */
object LocalReplySanitizer {

    private const val THINK_OPEN = "<think>"
    private const val THINK_CLOSE = "</think>"

    /**
     * 默认 system 侧引导：不输出 think / 隐藏标签（[LocalInferenceConfig.showThinking] 关闭时注入）。
     */
    const val NO_THINK_OR_TAGS_INSTRUCTION: String =
        "【输出约束】只输出面向用户的可见正文。" +
            "不要输出 <think> 思考过程，不要输出 [[mood=…]]、[[listen]] 等双方括号隐藏标签或动作标签。"

    /**
     * 已闭合的 `<think>…</think>`（跨行、大小写不敏感）。
     */
    private val CLOSED_THINK_REGEX = Regex(
        """(?is)<think\b[^>]*>.*?</think\s*>"""
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
     * - [showThinking]=false：丢弃 think 块，只返回正文
     * - [showThinking]=true：正文仍无 think 标签；思考进 [CleanedReply.thinkingText]
     */
    fun clean(raw: String, showThinking: Boolean = false): CleanedReply {
        if (raw.isEmpty()) {
            return CleanedReply(displayText = "")
        }
        val thinking = extractThinking(raw)?.trim()?.takeIf { it.isNotEmpty() }
        val withoutThink = stripThinkingBlocks(raw)
        val display = MoodTagMapper.stripTags(withoutThink)
        return if (showThinking) {
            CleanedReply(displayText = display, thinkingText = thinking)
        } else {
            CleanedReply(displayText = display, thinkingText = null)
        }
    }

    /** 仅要干净气泡正文（mood/动作永不保留；think 按开关）。 */
    fun forDisplay(raw: String, showThinking: Boolean = false): String =
        clean(raw, showThinking = showThinking).displayText

    /**
     * 剥离已闭合与未闭合的 think 块，返回剩余正文（可能仍含 `[[…]]`）。
     */
    fun stripThinkingBlocks(text: String): String {
        if (text.isEmpty()) return text
        if (!text.contains("think", ignoreCase = true)) {
            return text
        }
        var result = CLOSED_THINK_REGEX.replace(text, " ")
        // 未闭合：从首个 <think…> 起丢到文末
        val openIdx = indexOfIgnoreCase(result, THINK_OPEN)
        if (openIdx >= 0) {
            result = result.substring(0, openIdx)
        }
        return collapseWhitespace(result)
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
     * 在 system prompt 上按需追加「不输出 think/标签」约束。
     * [showThinking]=true 时不追加（允许模型输出 think 供折叠 UI）。
     */
    fun appendOutputConstraint(systemPrompt: String?, showThinking: Boolean): String? {
        if (showThinking) {
            return systemPrompt?.trim()?.takeIf { it.isNotEmpty() }
        }
        val base = systemPrompt?.trim().orEmpty()
        if (base.contains(NO_THINK_OR_TAGS_INSTRUCTION)) {
            return base.ifEmpty { null }
        }
        return if (base.isEmpty()) {
            NO_THINK_OR_TAGS_INSTRUCTION
        } else {
            "$base\n\n$NO_THINK_OR_TAGS_INSTRUCTION"
        }
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
