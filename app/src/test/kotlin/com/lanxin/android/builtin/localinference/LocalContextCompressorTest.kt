package com.lanxin.android.builtin.localinference

import com.lanxin.android.builtin.localinference.domain.LocalContextCompressor
import com.lanxin.android.builtin.localinference.domain.LocalInferenceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #123 上下文窗口 + 滑动压缩。
 */
class LocalContextCompressorTest {

    @Test
    fun `estimateTokens treats CJK denser than ascii`() {
        val cjk = LocalContextCompressor.estimateTokens("你好世界测试文本")
        val ascii = LocalContextCompressor.estimateTokens("hello world test text")
        assertTrue(cjk > 0)
        assertTrue(ascii > 0)
        // 同长度中文 token 应明显高于纯 ASCII
        assertTrue(cjk > ascii)
    }

    @Test
    fun `short dialog not compressed`() {
        val r = LocalContextCompressor.compressFromMessages(
            userTexts = listOf("hi", "how are you"),
            assistantTexts = listOf("hello", ""),
            contextWindowTokens = 8192,
            maxNewTokens = 512
        )
        assertFalse(r.compressed)
        assertEquals(0, r.droppedTurns)
        assertTrue(r.prompt.contains("hi"))
        assertTrue(r.prompt.contains("how are you"))
        assertTrue(r.prompt.endsWith("Assistant:") || r.prompt.contains("Assistant:"))
    }

    @Test
    fun `overflow drops oldest turns keeps recent user`() {
        val users = (1..40).map { i -> "用户问题编号$i：" + "详".repeat(80) }
        val assistants = (1..40).map { i -> if (i < 40) "助手回复$i：" + "答".repeat(80) else "" }
        val r = LocalContextCompressor.compressFromMessages(
            userTexts = users,
            assistantTexts = assistants,
            contextWindowTokens = 4096,
            maxNewTokens = 512
        )
        assertTrue("应触发压缩", r.compressed)
        assertTrue(r.droppedTurns > 0)
        assertTrue(r.keptTurns < users.size)
        assertTrue("必须保留最近用户", r.prompt.contains("用户问题编号40"))
        assertFalse("最旧轮应被丢掉", r.prompt.contains("用户问题编号1："))
        assertTrue(r.summaryHint != null)
    }

    @Test
    fun `single huge user is truncated not empty`() {
        val huge = "问".repeat(50_000)
        val r = LocalContextCompressor.compressFromMessages(
            userTexts = listOf(huge),
            contextWindowTokens = 2048,
            maxNewTokens = 512
        )
        assertTrue(r.prompt.isNotBlank())
        assertTrue(LocalContextCompressor.estimateTokens(r.prompt) < 3000)
    }

    @Test
    fun `context window defaults ordered`() {
        assertTrue(
            LocalInferenceConfig.MIN_CONTEXT_WINDOW_TOKENS
                < LocalInferenceConfig.DEFAULT_CONTEXT_WINDOW_TOKENS
        )
        assertTrue(
            LocalInferenceConfig.DEFAULT_CONTEXT_WINDOW_TOKENS
                < LocalInferenceConfig.MAX_CONTEXT_WINDOW_TOKENS
        )
        assertEquals(8192, LocalInferenceConfig.DEFAULT_CONTEXT_WINDOW_TOKENS)
        assertEquals(2048, LocalInferenceConfig.MAX_MAX_TOKENS)
    }

    @Test
    fun `conversation summary prepended when provided`() {
        val r = LocalContextCompressor.compressFromMessages(
            userTexts = listOf("继续"),
            conversationSummary = "用户喜欢猫咪",
            contextWindowTokens = 8192
        )
        assertTrue(r.prompt.contains("用户喜欢猫咪"))
        assertTrue(r.prompt.contains("继续"))
    }
}
