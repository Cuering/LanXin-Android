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

package com.lanxin.android.builtin.knowledge.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * P3 自动知识抽取的纯函数：阈值决策、标签合并、JSON 序列化。
 * 便于单测，无 Android / IO 依赖。
 */
object AutoKnowledgeMath {

    const val SIMILARITY_SKIP = 0.85f
    const val SIMILARITY_MERGE = 0.70f

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    enum class DedupAction {
        SKIP,
        MERGE,
        INSERT
    }

    /**
     * 按相似度阈值决定去重动作。
     * - > 0.85 → SKIP
     * - [0.70, 0.85] → MERGE
     * - < 0.70 → INSERT
     * - 无命中 → INSERT
     */
    fun decideDedupAction(bestScore: Float?): DedupAction {
        if (bestScore == null) return DedupAction.INSERT
        return when {
            bestScore > SIMILARITY_SKIP -> DedupAction.SKIP
            bestScore >= SIMILARITY_MERGE -> DedupAction.MERGE
            else -> DedupAction.INSERT
        }
    }

    /** tags 并集，保持顺序并去重。 */
    fun mergeTags(existing: List<String>, incoming: List<String>): List<String> {
        val seen = linkedSetOf<String>()
        existing.forEach { t ->
            val n = t.trim()
            if (n.isNotEmpty()) seen.add(n)
        }
        incoming.forEach { t ->
            val n = t.trim()
            if (n.isNotEmpty()) seen.add(n)
        }
        return seen.toList()
    }

    /** importance 取 max，并夹到 [0, 1]。 */
    fun mergeImportance(existing: Float, incoming: Float): Float =
        maxOf(existing, incoming).coerceIn(0f, 1f)

    /** JSON importance 0..1 → MemoryEntity importance 1..10。 */
    fun toMemoryImportance(importance01: Float): Float =
        (importance01.coerceIn(0f, 1f) * 9f + 1f).coerceIn(1f, 10f)

    /** MemoryEntity importance 1..10 → 0..1。 */
    fun fromMemoryImportance(importance: Float): Float =
        ((importance.coerceIn(1f, 10f) - 1f) / 9f).coerceIn(0f, 1f)

    fun normalizeType(raw: String): String {
        val t = raw.trim().lowercase()
        return when (t) {
            "preference", "pref" -> KnowledgeCategory.PREFERENCE
            "fact", "factual" -> KnowledgeCategory.FACT
            "event", "daily" -> KnowledgeCategory.EVENT
            "decision" -> KnowledgeCategory.DECISION
            "opinion", "insight" -> KnowledgeCategory.OPINION
            else -> if (t in KnowledgeCategory.ALL) t else KnowledgeCategory.FACT
        }
    }

    fun parseExtractionResponse(raw: String): List<ExtractedKnowledgeItem> {
        val cleaned = stripMarkdownFence(raw).trim()
        if (cleaned.isEmpty()) return emptyList()

        // 尝试整段解析；失败则截取首个 {...}
        val candidates = listOfNotNull(
            cleaned,
            extractJsonObject(cleaned)
        ).distinct()

        for (candidate in candidates) {
            runCatching {
                val envelope = json.decodeFromString(KnowledgeExtractionEnvelope.serializer(), candidate)
                return envelope.knowledgeItems
                    .mapNotNull { it.normalizedOrNull() }
            }
            runCatching {
                val list = json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(ExtractedKnowledgeItem.serializer()),
                    candidate
                )
                return list.mapNotNull { it.normalizedOrNull() }
            }
        }
        return emptyList()
    }

    fun encodeMetadata(
        source: String = VectorSource.AUTO_KNOWLEDGE,
        tags: List<String>,
        knowledgeType: String,
        sessionId: String,
        importance01: Float
    ): String {
        val meta = AutoKnowledgeMetadata(
            source = source,
            tags = tags,
            knowledgeType = knowledgeType,
            sessionId = sessionId,
            importance01 = importance01.coerceIn(0f, 1f)
        )
        return json.encodeToString(AutoKnowledgeMetadata.serializer(), meta)
    }

    fun decodeMetadata(raw: String?): AutoKnowledgeMetadata? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            json.decodeFromString(AutoKnowledgeMetadata.serializer(), raw)
        }.getOrNull()
    }

    fun buildExtractionPrompt(messages: List<ConversationMessage>): String {
        val transcript = messages.joinToString("\n") { msg ->
            val role = when (msg.role.lowercase()) {
                "user", "human" -> "用户"
                "assistant", "ai", "bot" -> "助手"
                else -> msg.role
            }
            "$role: ${msg.content.trim()}"
        }
        return """
你是知识抽取器。从下列对话中提炼有长期价值的知识点（用户偏好、事实、事件、决策、观点）。
忽略寒暄、无意义闲聊、一次性指令。

严格输出 JSON（不要 markdown fence，不要解释）：
{"knowledge_items":[{"content":"...","type":"preference|fact|event|decision|opinion","importance":0.0,"tags":["..."]}]}

规则：
- content 用简洁中文陈述句
- type 仅限 preference / fact / event / decision / opinion
- importance 为 0.0~1.0
- tags 为短标签数组，可为空
- 若无可抽取知识，返回 {"knowledge_items":[]}

对话：
$transcript
""".trimIndent()
    }

    fun stripMarkdownFence(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```")
            val nl = s.indexOf('\n')
            if (nl >= 0) {
                val lang = s.substring(0, nl).trim()
                if (lang.isEmpty() || lang.all { it.isLetterOrDigit() }) {
                    s = s.substring(nl + 1)
                }
            }
            if (s.endsWith("```")) {
                s = s.removeSuffix("```")
            }
        }
        return s.trim()
    }

    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return raw.substring(start, end + 1)
    }

    private fun ExtractedKnowledgeItem.normalizedOrNull(): ExtractedKnowledgeItem? {
        val c = content.trim()
        if (c.isEmpty()) return null
        return copy(
            content = c,
            type = normalizeType(type),
            importance = importance.coerceIn(0f, 1f),
            tags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        )
    }
}

object KnowledgeCategory {
    const val PREFERENCE = "preference"
    const val FACT = "fact"
    const val EVENT = "event"
    const val DECISION = "decision"
    const val OPINION = "opinion"

    val ALL = listOf(PREFERENCE, FACT, EVENT, DECISION, OPINION)

    fun displayName(type: String): String = when (type) {
        PREFERENCE -> "偏好"
        FACT -> "事实"
        EVENT -> "事件"
        DECISION -> "决策"
        OPINION -> "观点"
        else -> type
    }
}

/** 对话消息（与 UI/MessageV2 解耦）。 */
data class ConversationMessage(
    val role: String,
    val content: String
)

@Serializable
data class KnowledgeExtractionEnvelope(
    @SerialName("knowledge_items")
    val knowledgeItems: List<ExtractedKnowledgeItem> = emptyList()
)

@Serializable
data class ExtractedKnowledgeItem(
    val content: String = "",
    val type: String = KnowledgeCategory.FACT,
    val importance: Float = 0.5f,
    val tags: List<String> = emptyList()
)

@Serializable
data class AutoKnowledgeMetadata(
    val source: String = VectorSource.AUTO_KNOWLEDGE,
    val tags: List<String> = emptyList(),
    @SerialName("knowledge_type")
    val knowledgeType: String = KnowledgeCategory.FACT,
    @SerialName("session_id")
    val sessionId: String = "",
    @SerialName("importance_01")
    val importance01: Float = 0.5f
)
