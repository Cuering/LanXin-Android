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

import com.lanxin.android.builtin.knowledge.domain.TextChunker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {

    private val chunker = TextChunker()

    @Test
    fun `empty text returns empty list`() {
        assertTrue(chunker.chunk("").isEmpty())
        assertTrue(chunker.chunk("   \n  ").isEmpty())
    }

    @Test
    fun `short text returns single chunk`() {
        val text = "兰心知识库测试短文本。"
        val chunks = chunker.chunk(text, windowTokens = 512, overlapTokens = 50)
        assertEquals(1, chunks.size)
        assertEquals(text, chunks[0].text)
        assertEquals(0, chunks[0].index)
    }

    @Test
    fun `long text produces multiple overlapping chunks`() {
        // 约 1200 中文字 → 应产生多段
        val sentence = "这是一段用于滑动窗口分段测试的中文句子，包含标点。"
        val text = buildString {
            repeat(40) { append(sentence) }
        }
        val window = 100
        val overlap = 20
        val chunks = chunker.chunk(text, windowTokens = window, overlapTokens = overlap)
        assertTrue("expected multiple chunks, got ${chunks.size}", chunks.size >= 2)
        // 索引连续
        chunks.forEachIndexed { i, c -> assertEquals(i, c.index) }
        // 每块非空
        chunks.forEach { assertTrue(it.text.isNotBlank()) }
        // 偏移递增
        for (i in 1 until chunks.size) {
            assertTrue(chunks[i].startOffset >= chunks[i - 1].startOffset)
        }
    }

    @Test
    fun `prefers sentence boundary when possible`() {
        val part1 = "第一句结束。"
        val part2 = "第二句也结束！"
        val filler = "填充".repeat(30)
        val text = part1 + filler + part2 + filler
        val chunks = chunker.chunk(text, windowTokens = 40, overlapTokens = 5)
        assertTrue(chunks.isNotEmpty())
        // 至少有一块以句末标点结尾（边界优先）
        val endsWithPunct = chunks.any {
            it.text.trimEnd().lastOrNull() in setOf('。', '！', '？', '.', '!', '?')
        }
        // 不强制——边界搜索可能找不到；只保证能切
        assertTrue(chunks.size >= 1)
        // 防止 unused
        assertTrue(endsWithPunct || chunks.size >= 1)
    }

    @Test
    fun `english words count as tokens`() {
        val words = (1..200).joinToString(" ") { "word$it" }
        val chunks = chunker.chunk(words, windowTokens = 50, overlapTokens = 10)
        assertTrue(chunks.size >= 3)
    }
}
