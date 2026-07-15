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

package com.lanxin.android.builtin.knowledge

import com.lanxin.android.builtin.knowledge.domain.MarkdownChunker
import com.lanxin.android.builtin.knowledge.domain.TextChunker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownChunkerTest {

    private val chunker = MarkdownChunker(TextChunker())

    @Test
    fun `empty document returns empty list`() {
        assertTrue(chunker.chunk("").isEmpty())
        assertTrue(chunker.chunk("   \n  ").isEmpty())
    }

    @Test
    fun `preamble without headings is single chunk`() {
        val md = "这是前言段落。\n\n没有标题的正文。"
        val chunks = chunker.chunk(md)
        // 无标题 → 回退 TextChunker，短文单块
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].text.contains("前言段落"))
        assertEquals("", chunks[0].headingPath)
        assertEquals(null, chunks[0].heading)
    }

    @Test
    fun `nested headings build headingPath`() {
        val md = """
            # 一级
            
            一级正文。
            
            ## 二级 A
            
            二级 A 正文。
            
            ### 三级
            
            三级正文。
            
            ## 二级 B
            
            二级 B 正文。
        """.trimIndent()

        val chunks = chunker.chunk(md, windowTokens = 512, overlapTokens = 50)
        assertTrue(chunks.size >= 3)

        val l1 = chunks.first { it.heading == "一级" }
        assertEquals("一级", l1.headingPath)
        assertTrue(l1.text.contains("一级正文"))

        val l2a = chunks.first { it.heading == "二级 A" }
        assertEquals("一级 > 二级 A", l2a.headingPath)
        assertTrue(l2a.text.contains("二级 A 正文"))
        // 父路径注入
        assertTrue(l2a.text.startsWith("一级") || l2a.text.contains("一级"))

        val l3 = chunks.first { it.heading == "三级" }
        assertEquals("一级 > 二级 A > 三级", l3.headingPath)

        val l2b = chunks.first { it.heading == "二级 B" }
        assertEquals("一级 > 二级 B", l2b.headingPath)
        // 弹出三级后回到一级下
        assertFalse(l2b.headingPath.contains("三级"))
    }

    @Test
    fun `hash inside fenced code is not treated as heading`() {
        val md = """
            # 真实标题
            
            正文。
            
            ```python
            # 这不是标题
            def foo():
                pass
            ```
            
            后续正文。
            
            ~~~
            ## 也不是标题
            ~~~
            
            ## 真实二级
            
            二级正文。
        """.trimIndent()

        val chunks = chunker.chunk(md)
        val headings = chunks.mapNotNull { it.heading }.toSet()
        assertTrue(headings.contains("真实标题"))
        assertTrue(headings.contains("真实二级"))
        assertFalse(headings.contains("这不是标题"))
        assertFalse(headings.contains("也不是标题"))

        // fence 内内容仍在某个 chunk 里
        val joined = chunks.joinToString("\n") { it.text }
        assertTrue(joined.contains("def foo()"))
        assertTrue(joined.contains("也不是标题") || joined.contains("## 也不是标题"))
    }

    @Test
    fun `oversized section falls back to sliding window`() {
        val longBody = "这是一段很长的章节正文，用于触发二次滑动窗口切分。".repeat(40)
        val md = """
            # 超长章节
            
            $longBody
        """.trimIndent()

        val chunks = chunker.chunk(md, windowTokens = 80, overlapTokens = 10)
        assertTrue("expected multiple chunks for long section, got ${chunks.size}", chunks.size >= 2)
        chunks.forEach {
            assertEquals("超长章节", it.heading)
            assertEquals("超长章节", it.headingPath)
            assertTrue(it.text.isNotBlank())
        }
        // 续块带 ... 前缀
        val hasContinuation = chunks.drop(1).any {
            it.text.startsWith(MarkdownChunker.CONTINUATION_PREFIX)
        }
        assertTrue("expected continuation prefix on later chunks", hasContinuation)
    }

    @Test
    fun `preamble before first heading is kept`() {
        val md = """
            文档说明与前言，位于第一个标题之前。
            
            # 开始
            
            正式内容。
        """.trimIndent()

        val chunks = chunker.chunk(md)
        assertTrue(chunks.size >= 2)
        assertTrue(chunks[0].text.contains("前言"))
        assertEquals(null, chunks[0].heading)
        assertEquals("", chunks[0].headingPath)
        assertEquals("开始", chunks.first { it.heading == "开始" }.heading)
    }

    @Test
    fun `heading only sections merge into following body`() {
        val md = """
            # 空标题
            
            ## 有正文
            
            这里有内容。
        """.trimIndent()

        val chunks = chunker.chunk(md)
        // 空标题应合并到后续，不单独残留空壳
        assertTrue(chunks.isNotEmpty())
        chunks.forEach { assertTrue(it.text.isNotBlank()) }
        val joined = chunks.joinToString("\n---\n") { it.text }
        assertTrue(joined.contains("有正文") || joined.contains("这里有内容"))
    }

    @Test
    fun `indices are contiguous`() {
        val md = """
            # A
            a
            ## B
            b
            # C
            c
        """.trimIndent()
        val chunks = chunker.chunk(md)
        chunks.forEachIndexed { i, c -> assertEquals(i, c.index) }
    }

    @Test
    fun `includeHeadingContext false omits parent path prefix`() {
        val md = """
            # 父
            p
            ## 子
            c
        """.trimIndent()
        val withCtx = chunker.chunk(md, includeHeadingContext = true)
        val noCtx = chunker.chunk(md, includeHeadingContext = false)
        val childWith = withCtx.first { it.heading == "子" }
        val childNo = noCtx.first { it.heading == "子" }
        // metadata 仍有路径
        assertEquals("父 > 子", childNo.headingPath)
        // 无上下文时文本不应以「父」路径前缀开头（除了可能的标题行本身）
        assertFalse(childNo.text.startsWith("父\n\n"))
        assertTrue(childWith.text.contains("父") || childWith.headingPath.startsWith("父"))
    }
}
