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

/**
 * P1：规则摘要（不调用模型）。
 *
 * 对滑动窗口挤出的旧轮，抽取用户意图短句 + 可选助手要点，拼成预算内摘要。
 */
object LocalConversationSummarizer {

    /** 摘要默认 token 预算（CJK 粗估）。 */
    const val DEFAULT_MAX_SUMMARY_TOKENS = 256

    /** 单条用户摘录最大字符。 */
    const val MAX_USER_SNIPPET_CHARS = 48

    /** 单条助手摘录最大字符（更短）。 */
    const val MAX_ASSISTANT_SNIPPET_CHARS = 24

    /** 最多纳入摘要的旧轮数（头尾抽样）。 */
    const val MAX_TURNS_IN_SUMMARY = 12

    /**
     * 为被丢弃的轮次生成规则摘要。
     *
     * @return 纯摘要正文（不含「【对话摘要】」外壳）；无可摘要内容时 null
     */
    fun summarizeDropped(
        droppedTurns: List<LocalContextCompressor.Turn>,
        maxTokens: Int = DEFAULT_MAX_SUMMARY_TOKENS
    ): String? {
        if (droppedTurns.isEmpty()) return null
        val budget = maxTokens.coerceAtLeast(32)

        val sample = sampleTurns(droppedTurns, MAX_TURNS_IN_SUMMARY)
        val bullets = sample.mapNotNull { turn ->
            val u = snippet(turn.user, MAX_USER_SNIPPET_CHARS) ?: return@mapNotNull null
            val a = snippet(turn.assistant, MAX_ASSISTANT_SNIPPET_CHARS)
            if (a != null) "用户：$u → 助手：$a" else "用户：$u"
        }
        if (bullets.isEmpty()) {
            val fallback = "更早 ${droppedTurns.size} 轮已省略"
            return if (LocalContextCompressor.estimateTokens(fallback) <= budget) {
                fallback
            } else {
                null
            }
        }

        val header = "更早 ${droppedTurns.size} 轮要点："
        val included = ArrayList<String>()
        var body = header
        for (b in bullets) {
            val next = if (included.isEmpty()) {
                "$header $b"
            } else {
                "$body；$b"
            }
            if (LocalContextCompressor.estimateTokens(next) > budget) break
            body = next
            included.add(b)
        }

        if (included.isEmpty()) {
            val fallback = "更早 ${droppedTurns.size} 轮已省略"
            return if (LocalContextCompressor.estimateTokens(fallback) <= budget) {
                fallback
            } else {
                null
            }
        }

        val omitted = droppedTurns.size - included.size
        if (omitted > 0) {
            val withMore = "$body（另约 $omitted 轮略）"
            if (LocalContextCompressor.estimateTokens(withMore) <= budget) {
                return withMore
            }
        }
        return truncateToBudget(body, budget)
    }

    /**
     * 合并外部摘要与丢轮规则摘要。
     *
     * - 仅外部 → 外部
     * - 仅规则 → 规则
     * - 两者 → 外部优先，规则附在后（预算内）
     */
    fun mergeSummaries(
        external: String?,
        ruleFromDropped: String?,
        maxTokens: Int = DEFAULT_MAX_SUMMARY_TOKENS
    ): String? {
        val ext = external?.trim()?.takeIf { it.isNotBlank() }
        val rule = ruleFromDropped?.trim()?.takeIf { it.isNotBlank() }
        return when {
            ext == null && rule == null -> null
            ext != null && rule == null -> truncateToBudget(ext, maxTokens)
            ext == null && rule != null -> rule
            else -> truncateToBudget("$ext\n$rule", maxTokens.coerceAtLeast(32))
        }
    }

    private fun sampleTurns(
        turns: List<LocalContextCompressor.Turn>,
        max: Int
    ): List<LocalContextCompressor.Turn> {
        if (turns.size <= max) return turns
        val head = max / 2
        val tail = max - head
        return turns.take(head) + turns.takeLast(tail)
    }

    private fun snippet(text: String?, maxChars: Int): String? {
        val t = text?.trim()?.replace(Regex("\\s+"), " ") ?: return null
        if (t.isEmpty()) return null
        return if (t.length <= maxChars) t else t.take(maxChars - 1) + "…"
    }

    private fun truncateToBudget(text: String, maxTokens: Int): String {
        if (LocalContextCompressor.estimateTokens(text) <= maxTokens) return text
        var lo = 0
        var hi = text.length
        var best = ""
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val candidate = text.take(mid)
            if (LocalContextCompressor.estimateTokens(candidate) <= maxTokens) {
                best = candidate
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return best.trimEnd().ifBlank { text.take(16) }
    }
}
