package com.lanxin.android.builtin.localinference

import com.lanxin.android.builtin.localinference.domain.LocalContextCompressor
import com.lanxin.android.builtin.localinference.domain.LocalConversationSummarizer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #130 P1 规则摘要。
 */
class LocalConversationSummarizerTest {

    @Test
    fun `empty dropped returns null`() {
        val s = LocalConversationSummarizer.summarizeDropped(emptyList())
        assertTrue(s == null)
    }

    @Test
    fun `summarize includes user intent snippets not full body`() {
        val dropped = listOf(
            LocalContextCompressor.Turn(
                user = "我想养一只橘猫，预算两千左右",
                assistant = "可以考虑英短或中华田园猫"
            ),
            LocalContextCompressor.Turn(
                user = "对了，我对海鲜过敏",
                assistant = "记下了，之后避免海鲜食谱"
            )
        )
        val s = LocalConversationSummarizer.summarizeDropped(dropped)!!
        assertTrue(s.contains("橘猫") || s.contains("海鲜"))
        assertFalse("不应回写超长全文", s.contains("预算两千左右".repeat(2)))
        assertTrue(
            LocalContextCompressor.estimateTokens(s) <=
                LocalConversationSummarizer.DEFAULT_MAX_SUMMARY_TOKENS
        )
    }

    @Test
    fun `respects tight token budget`() {
        val dropped = (1..20).map { i ->
            LocalContextCompressor.Turn(
                user = "问题$i：" + "详".repeat(40),
                assistant = "答$i"
            )
        }
        val s = LocalConversationSummarizer.summarizeDropped(dropped, maxTokens = 64)
        assertNotNull(s)
        assertTrue(
            "budget 64, got ${LocalContextCompressor.estimateTokens(s!!)} for: $s",
            LocalContextCompressor.estimateTokens(s) <= 64
        )
    }

    @Test
    fun `merge prefers external then appends rule`() {
        val merged = LocalConversationSummarizer.mergeSummaries(
            external = "用户喜欢猫咪",
            ruleFromDropped = "更早 3 轮要点：用户：问天气",
            maxTokens = 256
        )!!
        assertTrue(merged.contains("用户喜欢猫咪"))
        assertTrue(merged.contains("天气") || merged.contains("要点"))
    }

    @Test
    fun `compress overflow injects rule summary into prompt`() {
        val users = (1..40).map { i -> "用户问题编号$i：" + "详".repeat(80) }
        val assistants = (1..40).map { i -> if (i < 40) "助手回复$i：" + "答".repeat(80) else "" }
        val r = LocalContextCompressor.compressFromMessages(
            userTexts = users,
            assistantTexts = assistants,
            contextWindowTokens = 4096,
            maxNewTokens = 512
        )
        assertTrue(r.compressed)
        assertTrue(r.droppedTurns > 0)
        assertNotNull(r.summaryHint)
        assertTrue(r.prompt.contains("【对话摘要】"))
        // 摘要可含编号线索，但对话体不应再出现最旧 User 行
        assertFalse(r.prompt.contains("User: 用户问题编号1"))
        assertTrue(r.prompt.contains("用户问题编号40"))
    }
}
