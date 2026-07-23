package com.lanxin.android.builtin.localinference

import com.lanxin.android.builtin.localinference.domain.LocalReplySanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(off!!.contains("输出约束"))
        assertTrue(off.contains("元话术"))
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

    @Test
    fun `strips untagged meta analysis dump like user sample`() {
        val raw = """
            让我分析一下这个问题：

            1. 用户是兰心（AI），用户说"你好"，我应该回应欢迎和友好

            查看可用工具：没有专门处理问候语的 tool

            2. 检查工具可用性：没有 greeting_tool 可以调用，直接回复即可

            3. 生成友好回应

            注意：不能添加隐藏标签，只能用可见内容回复

            ## 回应建议

            欢迎来到美丽的世界！🌟 我是一名智能助手，很高兴能为你提供支持和帮助。如果你有任何问题或需要闲聊，请随时告诉我也就了。

            期待与你度过美好时光！ 💖

            ---

            **分析：**
            - "你好"是问候语，适合简短友好的回答
            - 没有专门的 greeting_tool 可以调用，直接用自然语言回复即可
            - 保持温暖积极的氛围，传递善意

            **理由：** 用户发送的是一个非常普通的问候语，不需要复杂的推理或调用工具。一个真诚亲切的回答既能表达欢迎，又能让使用者感受到友好。
            （系统时间：2026-07-21 13:21）
        """.trimIndent()
        val cleaned = LocalReplySanitizer.clean(raw, showThinking = false)
        assertTrue(
            "应保留回应建议正文，实际=${cleaned.displayText}",
            cleaned.displayText.contains("欢迎") || cleaned.displayText.contains("美好")
        )
        assertFalse(cleaned.displayText.contains("让我分析"))
        assertFalse(cleaned.displayText.contains("greeting_tool"))
        assertFalse(cleaned.displayText.contains("**分析**") || cleaned.displayText.contains("分析："))
        assertFalse(cleaned.displayText.contains("系统时间"))
        assertFalse(cleaned.displayText.contains("查看可用工具"))
    }

    @Test
    fun `orphan think close stripped`() {
        val d = LocalReplySanitizer.forDisplay("你好呀</think>\n多余", showThinking = false)
        assertTrue(d.contains("你好呀"))
        assertFalse(d.contains("think", ignoreCase = true))
    }

    @Test
    fun `pure meta lead without suggested falls to empty or non-meta`() {
        val raw = "让我分析一下这个问题：\n查看可用工具：无"
        val d = LocalReplySanitizer.forDisplay(raw, showThinking = false)
        assertFalse(d.contains("让我分析"))
        assertFalse(d.contains("查看可用工具"))
    }

    @Test
    fun `forSpeech strips emoji and kaomoji but forDisplay keeps them`() {
        val raw = "你好呀～☀️ 今天开心吗？(^^) 让哥哥抱抱～★"
        val display = LocalReplySanitizer.forDisplay(raw, showThinking = false)
        val speech = LocalReplySanitizer.forSpeech(raw, showThinking = false)
        assertTrue(display.contains("☀️") || display.contains("★"))
        assertFalse(speech.contains("☀️"))
        assertFalse(speech.contains("★"))
        assertTrue(speech.contains("你好呀"))
        assertTrue(speech.contains("今天开心吗"))
        assertTrue(speech.contains("让哥哥抱抱"))
    }

    @Test
    fun `forSpeech strips angle bracket meta tags`() {
        val raw = "好的呀 <好感变化:+1> 哥哥最好了"
        val speech = LocalReplySanitizer.forSpeech(raw, showThinking = false)
        assertTrue(speech.contains("好的呀"))
        assertTrue(speech.contains("哥哥最好了"))
        assertFalse(speech.contains("<好感变化"))
    }

    @Test
    fun `dropLeadingBareThinking removes leading thought lines`() {
        val raw = """
            首先分析用户说的是问候语，我应该礼貌回应。
            其次检查是否有可用工具，没有 greeting_tool。
            最后，我应该回复一个温暖的问候。
            哥哥你好呀，今天想聊什么？
        """.trimIndent()
        val d = LocalReplySanitizer.forDisplay(raw, showThinking = false)
        assertFalse(d.contains("首先分析"))
        assertTrue(d.contains("哥哥你好呀"))
    }

    @Test
    fun `forSpeech returns empty speechText for empty input`() {
        val cleaned = LocalReplySanitizer.clean("")
        assertEquals("", cleaned.displayText)
        assertEquals("", cleaned.speechText)
    }

    @Test
    fun `CleanedReply speechText differs from displayText when emoji present`() {
        val raw = "<think>想好了</think>\n真棒！🌟🎉"
        val cleaned = LocalReplySanitizer.clean(raw, showThinking = false)
        assertTrue(cleaned.displayText.contains("🌟") || cleaned.displayText.contains("🎉"))
        assertFalse(cleaned.speechText.contains("🌟"))
        assertFalse(cleaned.speechText.contains("🎉"))
        assertTrue(cleaned.speechText.contains("真棒"))
    }

    @Test
    fun `constraint text leaked into reply is stripped`() {
        val raw = """
            【输出约束】直接对用户说短句，不输出思考过程、分析报告、协议标签或元话术。
            禁止思考外泄。不要先写内部推理。
            哥哥你好呀，今天天气不错呢。
        """.trimIndent()
        val d = LocalReplySanitizer.forDisplay(raw, showThinking = false)
        assertFalse(d.contains("【输出约束】"))
        assertFalse(d.contains("禁止思考外泄"))
        assertFalse(d.contains("不要先写内部推理"))
        assertTrue(d.contains("哥哥你好呀"))
    }

    @Test
    fun `inline constraint marker in stub echo is not wiped`() {
        // 旧 stub 会把 system 前缀 echo 成单行：`(sys=【输出约束】…)`
        // 行中带约束标记时不能整行丢弃，否则 Success 会变空
        val raw = "[local-stub] (sys=yes) echo: hello | maxTokens=128 | model=demo"
        val d = LocalReplySanitizer.forDisplay(raw, showThinking = false)
        assertTrue(d.isNotBlank())
        assertTrue(d.contains("echo:") || d.contains("hello"))
    }

    @Test
    fun `collapseRepeatedPhrase folds chinese question loop`() {
        val loop = "你叫什么名字？".repeat(20)
        val d = LocalReplySanitizer.forDisplay(loop, showThinking = false)
        assertEquals("你叫什么名字？", d)
    }

    @Test
    fun `collapseRepeatedPhrase folds spaced word loop`() {
        val loop = "你好呀 你好呀 你好呀 你好呀"
        val d = LocalReplySanitizer.collapseRepeatedPhrase(loop)
        assertEquals("你好呀", d)
    }

    @Test
    fun `collapseRepeatedPhrase folds identical lines`() {
        val raw = "嗯嗯好的
嗯嗯好的
嗯嗯好的"
        val d = LocalReplySanitizer.collapseRepeatedPhrase(raw)
        assertEquals("嗯嗯好的", d)
    }

    @Test
    fun `collapseRepeatedPhrase folds mixed intro plus question loop`() {
        // 截图样本：自我介绍后跟 phrase loop
        val raw = "我是兰心，温柔的陪伴。你叫什么名字？".let { head ->
            "我是兰心，温柔的陪伴。" + "你叫什么名字？".repeat(15)
        }
        val d = LocalReplySanitizer.forDisplay(raw, showThinking = false)
        assertTrue(d.contains("我是兰心") || d.contains("你叫什么名字"))
        assertFalse(d.contains("你叫什么名字？你叫什么名字？"))
        // 同一问句不应再连刷
        assertEquals(1, Regex("你叫什么名字？").findAll(d).count())
    }

    @Test
    fun `collapseRepeatedPhrase keeps normal short reply`() {
        val raw = "哥哥你好呀，今天想聊什么？"
        assertEquals(raw, LocalReplySanitizer.collapseRepeatedPhrase(raw))
    }

    @Test
    fun `appendOutputConstraint forbids phrase loop`() {
        val off = LocalReplySanitizer.appendOutputConstraint(null, showThinking = false)
        assertTrue(off!!.contains("禁止复读") || off.contains("只说一次"))
    }
}
