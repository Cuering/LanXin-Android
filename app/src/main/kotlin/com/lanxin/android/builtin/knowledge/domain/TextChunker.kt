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

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 滑动窗口文本分段器。
 *
 * 默认 window=512 token、overlap=50 token。
 * 估算：CJK 1 字 ≈ 1 token；连续拉丁/数字词 ≈ 1 token。
 * 切分尽量落在段落 / 句子边界，避免硬切半句。
 */
@Singleton
class TextChunker @Inject constructor() {

    data class Chunk(
        val text: String,
        val index: Int,
        /** 原文中的字符起始偏移 */
        val startOffset: Int,
        val endOffset: Int
    )

    /**
     * @param text 纯文本
     * @param windowTokens 窗口大小（估算 token）
     * @param overlapTokens 重叠（估算 token）
     */
    fun chunk(
        text: String,
        windowTokens: Int = DEFAULT_WINDOW,
        overlapTokens: Int = DEFAULT_OVERLAP
    ): List<Chunk> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isEmpty()) return emptyList()

        val window = windowTokens.coerceAtLeast(32)
        val overlap = overlapTokens.coerceIn(0, window / 2)
        val step = (window - overlap).coerceAtLeast(1)

        val units = tokenizeUnits(normalized)
        if (units.isEmpty()) {
            return listOf(
                Chunk(text = normalized, index = 0, startOffset = 0, endOffset = normalized.length)
            )
        }

        val chunks = mutableListOf<Chunk>()
        var startUnit = 0

        while (startUnit < units.size) {
            var endUnit = (startUnit + window).coerceAtMost(units.size)

            // 非文末时，尽量在句子/段落边界收缩
            if (endUnit < units.size) {
                endUnit = findBoundary(normalized, units, startUnit, endUnit)
            }

            if (endUnit <= startUnit) {
                endUnit = (startUnit + 1).coerceAtMost(units.size)
            }

            val startOffset = units[startUnit].start
            val endOffset = units[endUnit - 1].end
            val chunkText = normalized.substring(startOffset, endOffset).trim()

            if (chunkText.isNotEmpty()) {
                chunks.add(
                    Chunk(
                        text = chunkText,
                        index = chunks.size,
                        startOffset = startOffset,
                        endOffset = endOffset
                    )
                )
            }

            if (endUnit >= units.size) break

            val nextStart = (startUnit + step).coerceAtLeast(startUnit + 1)

            // 尾部过短：并入最后一块
            if (units.size - nextStart < MIN_TAIL_TOKENS && chunks.isNotEmpty()) {
                val last = chunks.removeAt(chunks.lastIndex)
                val mergedEnd = units.last().end
                val merged = normalized.substring(last.startOffset, mergedEnd).trim()
                if (merged.isNotEmpty()) {
                    chunks.add(last.copy(text = merged, endOffset = mergedEnd))
                }
                break
            }

            startUnit = nextStart
        }

        if (chunks.isEmpty() && normalized.isNotBlank()) {
            chunks.add(
                Chunk(
                    text = normalized,
                    index = 0,
                    startOffset = 0,
                    endOffset = normalized.length
                )
            )
        }

        return chunks.mapIndexed { i, c -> c.copy(index = i) }
    }

    private data class Unit(
        val start: Int,
        val end: Int
    )

    /**
     * 粗粒度 token 单元：
     * - CJK 字符各算 1 token
     * - 连续拉丁/数字词算 1 token
     * - 空白跳过
     * - 其他标点算 1 token
     */
    private fun tokenizeUnits(text: String): List<Unit> {
        val result = mutableListOf<Unit>()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch.isWhitespace() -> i++
                isCjk(ch) -> {
                    result.add(Unit(i, i + 1))
                    i++
                }
                ch.isLetterOrDigit() -> {
                    val start = i
                    i++
                    while (i < text.length && text[i].isLetterOrDigit()) i++
                    result.add(Unit(start, i))
                }
                else -> {
                    result.add(Unit(i, i + 1))
                    i++
                }
            }
        }
        return result
    }

    /**
     * 在 [startUnit, endUnit) 内从终点往回找句子/段落边界。
     */
    private fun findBoundary(
        text: String,
        units: List<Unit>,
        startUnit: Int,
        endUnit: Int
    ): Int {
        if (endUnit <= startUnit + 1) return endUnit
        val lookbackStart = (endUnit - BOUNDARY_LOOKBACK).coerceAtLeast(startUnit + 1)

        // 优先：段落边界（换行后）
        for (i in endUnit downTo lookbackStart + 1) {
            val prevEnd = units[i - 1].end
            val nextStart = if (i < units.size) units[i].start else text.length
            if (nextStart > prevEnd) {
                val gap = text.substring(prevEnd, nextStart)
                if (gap.contains('\n')) return i
            }
        }

        // 次选：句末标点后
        for (i in endUnit downTo lookbackStart + 1) {
            val lastCharIdx = units[i - 1].end - 1
            if (lastCharIdx in text.indices && text[lastCharIdx] in SENTENCE_END) {
                return i
            }
        }

        return endUnit
    }

    private fun isCjk(ch: Char): Boolean {
        val code = ch.code
        return code in 0x4E00..0x9FFF ||
            code in 0x3400..0x4DBF ||
            code in 0x20000..0x2A6DF ||
            code in 0xF900..0xFAFF ||
            code in 0x3000..0x303F
    }

    companion object {
        const val DEFAULT_WINDOW = 512
        const val DEFAULT_OVERLAP = 50
        private const val MIN_TAIL_TOKENS = 32
        private const val BOUNDARY_LOOKBACK = 40
        private val SENTENCE_END = setOf(
            '。', '！', '？', '；',
            '.', '!', '?', ';',
            '…', '\n'
        )
    }
}
