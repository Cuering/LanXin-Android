package com.lanxin.android.builtin.knowledge

import com.lanxin.android.builtin.knowledge.domain.AutoKnowledgeMath
import com.lanxin.android.builtin.knowledge.domain.ConversationMessage
import com.lanxin.android.builtin.knowledge.domain.KnowledgeCategory
import com.lanxin.android.builtin.knowledge.domain.VectorSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoKnowledgeMathTest {

    @Test
    fun `decideDedupAction skip above 0_85`() {
        assertEquals(
            AutoKnowledgeMath.DedupAction.SKIP,
            AutoKnowledgeMath.decideDedupAction(0.86f)
        )
        assertEquals(
            AutoKnowledgeMath.DedupAction.SKIP,
            AutoKnowledgeMath.decideDedupAction(0.99f)
        )
    }

    @Test
    fun `decideDedupAction merge in range`() {
        assertEquals(
            AutoKnowledgeMath.DedupAction.MERGE,
            AutoKnowledgeMath.decideDedupAction(0.85f)
        )
        assertEquals(
            AutoKnowledgeMath.DedupAction.MERGE,
            AutoKnowledgeMath.decideDedupAction(0.70f)
        )
        assertEquals(
            AutoKnowledgeMath.DedupAction.MERGE,
            AutoKnowledgeMath.decideDedupAction(0.75f)
        )
    }

    @Test
    fun `decideDedupAction insert below 0_70`() {
        assertEquals(
            AutoKnowledgeMath.DedupAction.INSERT,
            AutoKnowledgeMath.decideDedupAction(0.69f)
        )
        assertEquals(
            AutoKnowledgeMath.DedupAction.INSERT,
            AutoKnowledgeMath.decideDedupAction(0f)
        )
        assertEquals(
            AutoKnowledgeMath.DedupAction.INSERT,
            AutoKnowledgeMath.decideDedupAction(null)
        )
    }

    @Test
    fun `mergeTags is union preserving order`() {
        val merged = AutoKnowledgeMath.mergeTags(
            existing = listOf("a", "b"),
            incoming = listOf("b", "c", " a ")
        )
        assertEquals(listOf("a", "b", "c"), merged)
    }

    @Test
    fun `mergeImportance takes max and clamps`() {
        assertEquals(0.9f, AutoKnowledgeMath.mergeImportance(0.5f, 0.9f), 1e-5f)
        assertEquals(0.5f, AutoKnowledgeMath.mergeImportance(0.5f, 0.2f), 1e-5f)
        assertEquals(1f, AutoKnowledgeMath.mergeImportance(1.5f, 0.3f), 1e-5f)
        assertEquals(0f, AutoKnowledgeMath.mergeImportance(-1f, -0.2f), 1e-5f)
    }

    @Test
    fun `importance conversion roundtrip`() {
        val original = 0.5f
        val mem = AutoKnowledgeMath.toMemoryImportance(original)
        val back = AutoKnowledgeMath.fromMemoryImportance(mem)
        assertEquals(original, back, 1e-5f)
        assertTrue(mem in 1f..10f)
    }

    @Test
    fun `normalizeType maps aliases`() {
        assertEquals(KnowledgeCategory.PREFERENCE, AutoKnowledgeMath.normalizeType("pref"))
        assertEquals(KnowledgeCategory.FACT, AutoKnowledgeMath.normalizeType("factual"))
        assertEquals(KnowledgeCategory.EVENT, AutoKnowledgeMath.normalizeType("daily"))
        assertEquals(KnowledgeCategory.OPINION, AutoKnowledgeMath.normalizeType("insight"))
        assertEquals(KnowledgeCategory.DECISION, AutoKnowledgeMath.normalizeType("decision"))
        assertEquals(KnowledgeCategory.FACT, AutoKnowledgeMath.normalizeType("unknown_xyz"))
    }

    @Test
    fun `parseExtractionResponse plain json`() {
        val raw = """
            {"knowledge_items":[
              {"content":"喜欢黑咖啡","type":"preference","importance":0.8,"tags":["饮品"]},
              {"content":"","type":"fact","importance":0.5,"tags":[]}
            ]}
        """.trimIndent()
        val items = AutoKnowledgeMath.parseExtractionResponse(raw)
        assertEquals(1, items.size)
        assertEquals("喜欢黑咖啡", items[0].content)
        assertEquals(KnowledgeCategory.PREFERENCE, items[0].type)
        assertEquals(0.8f, items[0].importance, 1e-5f)
        assertEquals(listOf("饮品"), items[0].tags)
    }

    @Test
    fun `parseExtractionResponse strips markdown fence`() {
        val raw = """
            ```json
            {"knowledge_items":[{"content":"明天开会","type":"event","importance":0.6,"tags":[]}]}
            ```
        """.trimIndent()
        val items = AutoKnowledgeMath.parseExtractionResponse(raw)
        assertEquals(1, items.size)
        assertEquals(KnowledgeCategory.EVENT, items[0].type)
    }

    @Test
    fun `parseExtractionResponse empty or garbage`() {
        assertTrue(AutoKnowledgeMath.parseExtractionResponse("").isEmpty())
        assertTrue(AutoKnowledgeMath.parseExtractionResponse("not json").isEmpty())
        assertTrue(
            AutoKnowledgeMath.parseExtractionResponse("{\"knowledge_items\":[]}").isEmpty()
        )
    }

    @Test
    fun `metadata encode decode`() {
        val encoded = AutoKnowledgeMath.encodeMetadata(
            tags = listOf("tag1", "tag2"),
            knowledgeType = KnowledgeCategory.FACT,
            sessionId = "42",
            importance01 = 0.7f
        )
        val decoded = AutoKnowledgeMath.decodeMetadata(encoded)
        assertNotNull(decoded)
        assertEquals(VectorSource.AUTO_KNOWLEDGE, decoded!!.source)
        assertEquals(listOf("tag1", "tag2"), decoded.tags)
        assertEquals(KnowledgeCategory.FACT, decoded.knowledgeType)
        assertEquals("42", decoded.sessionId)
        assertEquals(0.7f, decoded.importance01, 1e-5f)
    }

    @Test
    fun `buildExtractionPrompt includes dialogue`() {
        val prompt = AutoKnowledgeMath.buildExtractionPrompt(
            listOf(
                ConversationMessage("user", "我喜欢喝茶"),
                ConversationMessage("assistant", "记下了")
            )
        )
        assertTrue(prompt.contains("我喜欢喝茶"))
        assertTrue(prompt.contains("knowledge_items"))
        assertTrue(prompt.contains("用户"))
    }

    @Test
    fun `stripMarkdownFence handles bare fences`() {
        val s = AutoKnowledgeMath.stripMarkdownFence("```\n{\"a\":1}\n```")
        assertEquals("{\"a\":1}", s)
    }

    @Test
    fun `sanitizeMessages drops tool noise and errors`() {
        val cleaned = AutoKnowledgeMath.sanitizeMessages(
            listOf(
                ConversationMessage("user", "我喜欢喝美式咖啡"),
                ConversationMessage("assistant", "Error: Software caused connection abort"),
                ConversationMessage("assistant", "工具: {\"stdout\": \"xxx\"}\n好的，已记下"),
                ConversationMessage("user", "你是知识抽取器。从下列对话中提炼...\n堆了很多无关内容"),
                ConversationMessage("assistant", "```\ncode\n```\n记下了你的偏好")
            ),
            maxMsgChars = 400,
            maxTranscriptChars = 2400,
            maxMessages = 6
        )
        val joined = cleaned.joinToString(" | ") { it.content }
        assertTrue(cleaned.any { it.content.contains("美式咖啡") })
        assertTrue(cleaned.none { it.content.startsWith("Error:") })
        assertTrue(cleaned.none { it.content.startsWith("你是知识抽取器") })
        assertTrue(cleaned.none { it.content.contains("工具:") })
        assertTrue(joined.contains("记下了") || cleaned.any { it.role == "assistant" })
    }

    @Test
    fun `sanitizeMessageContent truncates long text`() {
        val long = "偏好" + "啊".repeat(500)
        val out = AutoKnowledgeMath.sanitizeMessageContent(long, maxChars = 50)
        assertTrue(out != null && out.length <= 50)
        assertTrue(out!!.endsWith("…"))
    }

    @Test
    fun `sanitizeMessages empty after greeting filter`() {
        val cleaned = AutoKnowledgeMath.sanitizeMessages(
            listOf(
                ConversationMessage("user", "好的"),
                ConversationMessage("assistant", "嗯"),
                ConversationMessage("user", "ok"),
                ConversationMessage("assistant", "done")
            )
        )
        assertTrue(cleaned.isEmpty())
    }

    @Test
    fun `buildExtractionPrompt sanitizes noise before transcript`() {
        val noisyUser =
            "<system_reminder>hidden</system_reminder>\n我更喜欢简洁回复"
        val prompt = AutoKnowledgeMath.buildExtractionPrompt(
            listOf(
                ConversationMessage("user", "我更喜欢简洁回复"),
                ConversationMessage("assistant", "Error: timeout"),
                ConversationMessage("user", noisyUser)
            )
        )
        assertTrue(prompt.contains("简洁回复"))
        assertTrue(!prompt.contains("Error: timeout"))
        assertTrue(!prompt.contains("hidden"))
        assertTrue(prompt.contains("importance 0.0~1.0"))
    }
}
