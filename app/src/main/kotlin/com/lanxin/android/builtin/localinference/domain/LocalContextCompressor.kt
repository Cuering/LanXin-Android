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
 * 本地推理上下文压缩（P0：滑动窗口；P1：规则摘要被挤出轮次）。
 *
 * - 保留 system + 最近若干轮 + 当前 user
 * - 超 `contextWindow * softRatio` 时从最旧轮次丢弃
 * - 丢轮时由 [LocalConversationSummarizer] 生成规则摘要注入 prompt
 * - token 用 CJK 粗估，不依赖 tokenizer
 */
object LocalContextCompressor {

    /** 触发压缩的软阈值（相对窗口）。 */
    const val DEFAULT_SOFT_RATIO = 0.85f

    /**
     * 一轮对话（user + 可选 assistant）。
     */
    data class Turn(
        val user: String,
        val assistant: String? = null
    )

    /**
     * 压缩结果。
     *
     * @property prompt 拼好的 user 侧多轮文本（兼容旧路径；优先用 [currentUser] + [history]）
     * @property keptTurns 保留的轮次数
     * @property droppedTurns 丢弃的旧轮次数
     * @property estimatedTokens 估算占用（不含 system 时可单独加）
     * @property compressed 是否发生了截断
     * @property summaryHint 注入 prompt 的摘要正文（规则摘要和/或外部摘要；无则 null）
     * @property history 结构化多轮历史（不含当前 user；system 摘要可在首位）
     * @property currentUser 当前轮 user 文本
     */
    data class Result(
        val prompt: String,
        val keptTurns: Int,
        val droppedTurns: Int,
        val estimatedTokens: Int,
        val compressed: Boolean,
        val summaryHint: String? = null,
        val history: List<LocalChatMessage> = emptyList(),
        val currentUser: String = ""
    )

    /**
     * CJK 友好粗估：中日韩约 1 token/字（偏保守占预算），ASCII 约 4 字 1 token。
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        var cjk = 0
        var other = 0
        for (ch in text) {
            when {
                ch.code in 0x4E00..0x9FFF ||
                    ch.code in 0x3400..0x4DBF ||
                    ch.code in 0x3040..0x30FF ||
                    ch.code in 0xAC00..0xD7AF -> cjk++
                else -> other++
            }
        }
        // 保守：CJK 1:1；ASCII /4。预算宁可略紧，少 OOM。
        val tokens = cjk + (other / 4.0)
        return tokens.toInt().coerceAtLeast(if (text.isBlank()) 0 else 1)
    }

    /**
     * 从并行 user/assistant 列表构建 turns（与 Chat 分组结构对齐）。
     *
     * @param userTexts 每轮用户文本
     * @param assistantTexts 每轮助手正文（与 user 等长；缺省用空串）
     * @param dropTrailingEmptyAssistant 最后一轮 assistant 为空时不写入（生成中占位）
     */
    fun buildTurns(
        userTexts: List<String>,
        assistantTexts: List<String> = emptyList(),
        dropTrailingEmptyAssistant: Boolean = true
    ): List<Turn> {
        if (userTexts.isEmpty()) return emptyList()
        val n = userTexts.size
        return List(n) { i ->
            val u = userTexts[i]
            val a = assistantTexts.getOrNull(i)?.takeIf { it.isNotBlank() }
            val isLast = i == n - 1
            Turn(
                user = u,
                assistant = if (dropTrailingEmptyAssistant && isLast && a == null) null else a
            )
        }.filter { it.user.isNotBlank() || !it.assistant.isNullOrBlank() }
    }

    /**
     * 滑动窗口压缩并拼 prompt。
     *
     * @param turns 历史轮次（含当前 user，最后一轮 assistant 通常为空）
     * @param systemPrompt 可选 system（计入预算）
     * @param contextWindowTokens 上下文窗口
     * @param maxNewTokens 预留给生成的 token（从窗口中扣减）
     * @param softRatio 触发阈值
     * @param conversationSummary 外部已有摘要（与丢轮规则摘要合并）
     * @param maxSummaryTokens 摘要 token 预算
     */
    fun compress(
        turns: List<Turn>,
        systemPrompt: String? = null,
        contextWindowTokens: Int = LocalInferenceConfig.DEFAULT_CONTEXT_WINDOW_TOKENS,
        maxNewTokens: Int = LocalInferenceConfig.DEFAULT_MAX_TOKENS,
        softRatio: Float = DEFAULT_SOFT_RATIO,
        conversationSummary: String? = null,
        maxSummaryTokens: Int = LocalConversationSummarizer.DEFAULT_MAX_SUMMARY_TOKENS
    ): Result {
        val window = contextWindowTokens.coerceIn(
            LocalInferenceConfig.MIN_CONTEXT_WINDOW_TOKENS,
            LocalInferenceConfig.MAX_CONTEXT_WINDOW_TOKENS
        )
        val genBudget = maxNewTokens.coerceIn(
            LocalInferenceConfig.MIN_MAX_TOKENS,
            LocalInferenceConfig.MAX_MAX_TOKENS
        )
        val summaryBudget = maxSummaryTokens.coerceAtLeast(32)
        // 先按「摘要预算上限」预留，避免丢轮后再塞摘要撑爆窗口
        val sysTokens = estimateTokens(systemPrompt.orEmpty())
        val reservedSummary = minOf(
            summaryBudget,
            estimateTokens(conversationSummary.orEmpty()).coerceAtLeast(0)
        ).let { base ->
            // 有外部摘要用其实际；否则预留默认摘要预算的一半作丢轮摘要
            if (conversationSummary.isNullOrBlank()) {
                (summaryBudget / 2).coerceAtLeast(32)
            } else {
                maxOf(base, summaryBudget / 2)
            }
        }
        val inputBudget = (
            (window * softRatio).toInt() - genBudget - sysTokens - reservedSummary
            ).coerceAtLeast(64)

        if (turns.isEmpty()) {
            val onlyExternal = LocalConversationSummarizer.mergeSummaries(
                external = conversationSummary,
                ruleFromDropped = null,
                maxTokens = summaryBudget
            )
            return Result(
                prompt = renderPrompt(
                    kept = emptyList(),
                    conversationSummary = onlyExternal,
                    droppedHint = null
                ),
                keptTurns = 0,
                droppedTurns = 0,
                estimatedTokens = sysTokens + estimateTokens(onlyExternal.orEmpty()),
                compressed = false,
                summaryHint = onlyExternal,
                history = toHistoryMessages(emptyList(), onlyExternal),
                currentUser = ""
            )
        }

        // 从尾部保留，直到超预算
        val kept = ArrayList<Turn>()
        var used = 0
        for (i in turns.indices.reversed()) {
            val t = turns[i]
            val cost = turnTokens(t)
            if (kept.isNotEmpty() && used + cost > inputBudget) {
                break
            }
            // 单轮已超预算：仍保留最后一轮，截断 user
            if (kept.isEmpty() && cost > inputBudget) {
                val truncatedUser = truncateToTokens(t.user, inputBudget)
                kept.add(Turn(user = truncatedUser, assistant = null))
                used = estimateTokens(truncatedUser)
                break
            }
            kept.add(0, t)
            used += cost
        }

        val droppedCount = turns.size - kept.size
        val droppedTurns = if (droppedCount > 0) turns.take(droppedCount) else emptyList()
        val compressed = droppedCount > 0 ||
            (kept.size == 1 && turns.isNotEmpty() && kept.first().user != turns.last().user)

        val ruleSummary = if (droppedTurns.isNotEmpty()) {
            LocalConversationSummarizer.summarizeDropped(
                droppedTurns = droppedTurns,
                maxTokens = summaryBudget
            )
        } else {
            null
        }
        val mergedSummary = LocalConversationSummarizer.mergeSummaries(
            external = conversationSummary,
            ruleFromDropped = ruleSummary,
            maxTokens = summaryBudget
        )

        val prompt = renderPrompt(
            kept = kept,
            conversationSummary = mergedSummary,
            droppedHint = null
        )
        val currentUser = kept.lastOrNull()?.user?.trim().orEmpty()
        val prior = if (kept.isEmpty()) emptyList() else kept.dropLast(1)
        return Result(
            prompt = prompt,
            keptTurns = kept.size,
            droppedTurns = droppedCount,
            estimatedTokens = sysTokens + estimateTokens(prompt),
            compressed = compressed,
            summaryHint = mergedSummary,
            history = toHistoryMessages(prior, mergedSummary),
            currentUser = currentUser
        )
    }

    /**
     * 将保留轮次 + 摘要转为 [LocalChatMessage] 列表（不含当前 user）。
     */
    fun toHistoryMessages(
        priorTurns: List<Turn>,
        conversationSummary: String?
    ): List<LocalChatMessage> {
        val out = ArrayList<LocalChatMessage>()
        conversationSummary?.takeIf { it.isNotBlank() }?.let {
            out.add(
                LocalChatMessage(
                    role = "system",
                    content = "【对话摘要】\n${it.trim()}"
                )
            )
        }
        for (turn in priorTurns) {
            if (turn.user.isNotBlank()) {
                out.add(LocalChatMessage(role = "user", content = turn.user.trim()))
            }
            turn.assistant?.takeIf { it.isNotBlank() }?.let {
                out.add(LocalChatMessage(role = "assistant", content = it.trim()))
            }
        }
        return out
    }

    /**
     * 便捷：直接从 user/assistant 文本列表压缩。
     */
    fun compressFromMessages(
        userTexts: List<String>,
        assistantTexts: List<String> = emptyList(),
        systemPrompt: String? = null,
        contextWindowTokens: Int = LocalInferenceConfig.DEFAULT_CONTEXT_WINDOW_TOKENS,
        maxNewTokens: Int = LocalInferenceConfig.DEFAULT_MAX_TOKENS,
        conversationSummary: String? = null,
        maxSummaryTokens: Int = LocalConversationSummarizer.DEFAULT_MAX_SUMMARY_TOKENS
    ): Result {
        val turns = buildTurns(userTexts, assistantTexts)
        return compress(
            turns = turns,
            systemPrompt = systemPrompt,
            contextWindowTokens = contextWindowTokens,
            maxNewTokens = maxNewTokens,
            conversationSummary = conversationSummary,
            maxSummaryTokens = maxSummaryTokens
        )
    }

    private fun turnTokens(turn: Turn): Int {
        var n = estimateTokens(turn.user) + 4 // role 开销
        turn.assistant?.let { n += estimateTokens(it) + 4 }
        return n
    }

    private fun truncateToTokens(text: String, maxTokens: Int): String {
        if (estimateTokens(text) <= maxTokens) return text
        // 二分截断尾部保留（当前 user 优先完整更重要时也可改头截；长 user 保留尾部问题句）
        var lo = 0
        var hi = text.length
        var best = ""
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val candidate = text.takeLast(mid)
            if (estimateTokens(candidate) <= maxTokens) {
                best = candidate
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return if (best.length < text.length) "…$best" else best
    }

    private fun renderPrompt(
        kept: List<Turn>,
        conversationSummary: String?,
        droppedHint: String?
    ): String = buildString {
        conversationSummary?.takeIf { it.isNotBlank() }?.let {
            append("【对话摘要】\n")
            append(it.trim())
            append("\n\n")
        }
        droppedHint?.takeIf { conversationSummary.isNullOrBlank() }?.let {
            append(it)
            append("\n\n")
        }
        kept.forEachIndexed { index, turn ->
            val isLast = index == kept.lastIndex
            append("User: ")
            append(turn.user.trim())
            append('\n')
            if (!turn.assistant.isNullOrBlank()) {
                append("Assistant: ")
                append(turn.assistant.trim())
                append('\n')
            } else if (!isLast) {
                // 中间缺 assistant 仍占位，避免模型混乱
                append("Assistant: \n")
            }
            if (!isLast) append('\n')
        }
        // 最后一轮无 assistant 时引导续写
        if (kept.isNotEmpty() && kept.last().assistant.isNullOrBlank()) {
            append("Assistant:")
        }
    }.trimEnd()
}
