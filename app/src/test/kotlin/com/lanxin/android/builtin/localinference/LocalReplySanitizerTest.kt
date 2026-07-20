package com.lanxin.android.builtin.localinference

import com.lanxin.android.builtin.localinference.domain.LocalReplySanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalReplySanitizerTest {

    @Test
    fun `default strips closed think and mood tags`() {
        val raw = """
            <think>
            用户发来你好，我应该温柔回应。
            </think>

            你好呀～☀️
            [[mood=joy]]
            [[listen]]
        """.trimIndent()
        val cleaned = LocalReplySanitizer.clean(raw, showThinking = false)
        assertNull(cleaned.thinkingText)
        assertTrue(cleaned.displayText.contains("你好呀"))
        assertTrue(!cleaned.displayText.contains("think", ignoreCase = true))
        assertTrue(!cleaned.displayText.contains("[["))
        assertTrue(!cleaned.displayText.contains("用户发来你好"))
    }

    @Test
    fun `showThinking keeps thinking text but display has no tags`() {
        val raw = "<think>plan A</think>\n正文[[mood=smile]]"
        val cleaned = LocalReplySanitizer.clean(raw, showThinking = true)
        assertEquals("plan A", cleaned.thinkingText)
        assertEquals("正文", cleaned.displayText)
    }

    @Test
    fun `unclosed think is stripped`() {
        val raw = "可见前缀\n<think>\n还在想\n没有闭合"
        val cleaned = LocalReplySanitizer.clean(raw, showThinking = false)
        assertEquals("可见前缀", cleaned.displayText)
    }

    @Test
    fun `appendOutputConstraint only when thinking off`() {
        val off = LocalReplySanitizer.appendOutputConstraint(null, showThinking = false)
        assertTrue(off!!.contains("不要输出"))
        val on = LocalReplySanitizer.appendOutputConstraint("sys", showThinking = true)
        assertEquals("sys", on)
    }

    @Test
    fun `action tag listen stripped`() {
        val d = LocalReplySanitizer.forDisplay("嗯[[listen]]好", showThinking = false)
        assertTrue(d.contains("嗯"))
        assertTrue(d.contains("好"))
        assertTrue(!d.contains("listen"))
        assertTrue(!d.contains("[["))
    }
}
