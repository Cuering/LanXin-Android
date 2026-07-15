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
 * Markdown 结构感知分段器。
 *
 * 对标 AstrBot `chunking/markdown.py`：
 * - 按 ATX 标题（`#`～`######`）切 section，跳过 fence（``` / ~~~）内的 `#`
 * - 维护 heading stack → 构建章节路径
 * - section ≤ window → 整段一块；否则回退 [TextChunker] 滑动窗口
 * - 可选注入标题路径（`父级 > 子级`）到 chunk 文本，利于向量检索
 */
@Singleton
class MarkdownChunker @Inject constructor(
    private val textChunker: TextChunker
) {

    data class Chunk(
        val text: String,
        val index: Int,
        /** 当前章节标题；preamble 为 null */
        val heading: String?,
        /** 完整标题路径，如 `父级 > 子级`；无标题时为空串 */
        val headingPath: String,
        val startOffset: Int = 0,
        val endOffset: Int = 0
    )

    private data class Section(
        val heading: String?,
        /** 完整路径（含当前标题） */
        val fullPath: List<String>,
        /** 父级路径（不含当前，用于 context 前缀，与 AstrBot 一致） */
        val parentPath: List<String>,
        val text: String,
        val hasBody: Boolean,
        val startOffset: Int,
        val endOffset: Int
    )

    private data class Heading(
        val level: Int,
        val title: String,
        val start: Int,
        val end: Int
    )

    /**
     * @param text Markdown 原文（保留标题语法）
     * @param windowTokens 窗口（估算 token），透传 TextChunker
     * @param overlapTokens 重叠
     * @param includeHeadingContext 是否把路径注入 chunk 文本
     * @param maxHeadingDepth 识别的最大标题深度 1–6
     */
    fun chunk(
        text: String,
        windowTokens: Int = TextChunker.DEFAULT_WINDOW,
        overlapTokens: Int = TextChunker.DEFAULT_OVERLAP,
        includeHeadingContext: Boolean = true,
        maxHeadingDepth: Int = 6
    ): List<Chunk> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        if (normalized.isBlank()) return emptyList()

        val depth = maxHeadingDepth.coerceIn(1, 6)
        val sections = parseSections(normalized, depth)

        if (sections.isEmpty()) {
            // 无标题结构 → 整篇回退滑动窗口
            return textChunker.chunk(normalized, windowTokens, overlapTokens).map { c ->
                Chunk(
                    text = c.text,
                    index = c.index,
                    heading = null,
                    headingPath = "",
                    startOffset = c.startOffset,
                    endOffset = c.endOffset
                )
            }
        }

        val raw = sectionsToChunks(
            sections = sections,
            windowTokens = windowTokens,
            overlapTokens = overlapTokens,
            includeHeadingContext = includeHeadingContext
        )

        val merged = mergeHeadingOnlyChunks(raw, windowTokens)

        return merged.mapIndexed { i, c -> c.copy(index = i) }
    }

    private data class RawChunk(
        val text: String,
        val hasBody: Boolean,
        val heading: String?,
        val headingPath: String,
        val startOffset: Int,
        val endOffset: Int
    )

    private fun sectionsToChunks(
        sections: List<Section>,
        windowTokens: Int,
        overlapTokens: Int,
        includeHeadingContext: Boolean
    ): List<RawChunk> {
        val out = mutableListOf<RawChunk>()

        for (section in sections) {
            val pathStr = section.fullPath.joinToString(" > ")
            val parentStr = section.parentPath.joinToString(" > ")
            val contextPrefix = if (includeHeadingContext && parentStr.isNotEmpty()) {
                "$parentStr\n\n"
            } else {
                ""
            }
            val fullText = (contextPrefix + section.text).trim()

            // 先用 TextChunker 探测是否超窗：单块则整段保留，多块则二次切分
            val probe = textChunker.chunk(fullText, windowTokens, overlapTokens)
            if (probe.size <= 1) {
                if (fullText.isNotBlank()) {
                    out.add(
                        RawChunk(
                            text = fullText,
                            hasBody = section.hasBody,
                            heading = section.heading,
                            headingPath = pathStr,
                            startOffset = section.startOffset,
                            endOffset = section.endOffset
                        )
                    )
                }
                continue
            }

            // 章节过长：对 section 原文切分，再分别注入路径
            val subChunks = textChunker.chunk(section.text, windowTokens, overlapTokens)
            for ((i, sub) in subChunks.withIndex()) {
                val chunkText = applyHeadingContext(
                    fullPath = pathStr,
                    parentPath = parentStr,
                    content = sub.text,
                    isContinuation = i > 0,
                    includeHeadingContext = includeHeadingContext
                )
                if (chunkText.isNotBlank()) {
                    out.add(
                        RawChunk(
                            text = chunkText,
                            hasBody = true,
                            heading = section.heading,
                            headingPath = pathStr,
                            startOffset = section.startOffset + sub.startOffset,
                            endOffset = section.startOffset + sub.endOffset
                        )
                    )
                }
            }
        }

        return out
    }

    private fun applyHeadingContext(
        fullPath: String,
        parentPath: String,
        content: String,
        isContinuation: Boolean,
        includeHeadingContext: Boolean
    ): String {
        if (!includeHeadingContext) return content.trim()
        // 首块：父路径前缀（当前标题已在 section 文本里）；续块：完整路径 + "..."
        val title = when {
            isContinuation && fullPath.isNotEmpty() -> fullPath
            parentPath.isNotEmpty() -> parentPath
            fullPath.isNotEmpty() && isContinuation -> fullPath
            else -> ""
        }
        if (title.isEmpty()) return content.trim()
        return if (isContinuation) {
            "$CONTINUATION_PREFIX $title\n\n$content".trim()
        } else if (parentPath.isNotEmpty()) {
            "$parentPath\n\n$content".trim()
        } else {
            content.trim()
        }
    }

    /**
     * 合并纯标题节到下一个有正文的 chunk（对齐 AstrBot `_merge_heading_only_chunks`）。
     */
    private fun mergeHeadingOnlyChunks(
        raw: List<RawChunk>,
        windowTokens: Int
    ): List<Chunk> {
        // windowTokens 近似字符上限（CJK ≈ 1 token）；合并时避免无限膨胀
        val maxChars = (windowTokens * 2).coerceAtLeast(256)
        val merged = mutableListOf<Chunk>()
        var pending: RawChunk? = null

        fun flushPending() {
            val p = pending ?: return
            if (p.text.isNotBlank()) {
                merged.add(
                    Chunk(
                        text = p.text.trim(),
                        index = merged.size,
                        heading = p.heading,
                        headingPath = p.headingPath,
                        startOffset = p.startOffset,
                        endOffset = p.endOffset
                    )
                )
            }
            pending = null
        }

        for (chunk in raw) {
            if (chunk.text.isBlank()) continue
            if (!chunk.hasBody) {
                val p = pending
                if (p != null && p.text.length + chunk.text.length + 2 > maxChars) {
                    flushPending()
                }
                pending = if (pending == null) {
                    chunk
                } else {
                    val p0 = pending!!
                    p0.copy(
                        text = p0.text.trimEnd() + "\n\n" + chunk.text.trim(),
                        endOffset = chunk.endOffset
                    )
                }
            } else {
                val p = pending
                if (p != null) {
                    val combined = p.text.trimEnd() + "\n\n" + chunk.text.trim()
                    if (combined.length <= maxChars) {
                        merged.add(
                            Chunk(
                                text = combined.trim(),
                                index = merged.size,
                                heading = chunk.heading ?: p.heading,
                                headingPath = chunk.headingPath.ifEmpty { p.headingPath },
                                startOffset = p.startOffset,
                                endOffset = chunk.endOffset
                            )
                        )
                    } else {
                        flushPending()
                        merged.add(
                            Chunk(
                                text = chunk.text.trim(),
                                index = merged.size,
                                heading = chunk.heading,
                                headingPath = chunk.headingPath,
                                startOffset = chunk.startOffset,
                                endOffset = chunk.endOffset
                            )
                        )
                    }
                    pending = null
                } else {
                    merged.add(
                        Chunk(
                            text = chunk.text.trim(),
                            index = merged.size,
                            heading = chunk.heading,
                            headingPath = chunk.headingPath,
                            startOffset = chunk.startOffset,
                            endOffset = chunk.endOffset
                        )
                    )
                }
            }
        }
        flushPending()
        return merged.filter { it.text.isNotBlank() }
    }

    private fun parseSections(text: String, maxDepth: Int): List<Section> {
        val fenced = findFencedCodeRanges(text)
        val headings = findHeadings(text, maxDepth, fenced)
        if (headings.isEmpty()) return emptyList()

        val sections = mutableListOf<Section>()

        // preamble
        val preamble = text.substring(0, headings[0].start).trim()
        if (preamble.isNotEmpty()) {
            sections.add(
                Section(
                    heading = null,
                    fullPath = emptyList(),
                    parentPath = emptyList(),
                    text = preamble,
                    hasBody = true,
                    startOffset = 0,
                    endOffset = headings[0].start
                )
            )
        }

        val stack = mutableListOf<Heading>()
        for (i in headings.indices) {
            val h = headings[i]
            while (stack.isNotEmpty() && stack.last().level >= h.level) {
                stack.removeAt(stack.lastIndex)
            }
            stack.add(h)

            val contentStart = h.end
            val contentEnd = if (i + 1 < headings.size) headings[i + 1].start else text.length
            val headingLine = text.substring(h.start, h.end)
            val body = text.substring(contentStart, contentEnd).trim()
            val sectionText = if (body.isNotEmpty()) {
                headingLine + "\n" + body
            } else {
                headingLine
            }

            val fullPath = stack.map { it.title }
            val parentPath = fullPath.dropLast(1)

            sections.add(
                Section(
                    heading = h.title,
                    fullPath = fullPath,
                    parentPath = parentPath,
                    text = sectionText,
                    hasBody = body.isNotEmpty(),
                    startOffset = h.start,
                    endOffset = contentEnd
                )
            )
        }

        return sections
    }

    private fun findHeadings(
        text: String,
        maxDepth: Int,
        fenced: List<IntRange>
    ): List<Heading> {
        val result = mutableListOf<Heading>()
        // ATX：行首 1–N 个 #，后可有空格，再接标题；不要求闭合 #
        val pattern = Regex("^(#{1,$maxDepth})\\s*(.+)$", RegexOption.MULTILINE)
        for (m in pattern.findAll(text)) {
            val start = m.range.first
            if (isInFencedBlock(start, fenced)) continue
            val level = m.groupValues[1].length
            val title = m.groupValues[2].trim().trimEnd('#').trim()
            if (title.isEmpty()) continue
            result.add(
                Heading(
                    level = level,
                    title = title,
                    start = start,
                    end = m.range.last + 1
                )
            )
        }
        return result
    }

    companion object {
        const val CONTINUATION_PREFIX = "..."

        /**
         * 找围栏代码块范围（``` / ~~~），支持开闭 fence 长度匹配。
         */
        fun findFencedCodeRanges(text: String): List<IntRange> {
            val fencePattern = Regex("^(`{3,}|~{3,})", RegexOption.MULTILINE)
            val matches = fencePattern.findAll(text).toList()
            val ranges = mutableListOf<IntRange>()
            var i = 0
            while (i < matches.size) {
                val open = matches[i]
                val openFence = open.groupValues[1]
                val fenceChar = openFence[0]
                val fenceLen = openFence.length
                var closed = false
                for (j in i + 1 until matches.size) {
                    val close = matches[j]
                    val closeFence = close.groupValues[1]
                    if (closeFence[0] == fenceChar && closeFence.length >= fenceLen) {
                        ranges.add(open.range.first until (close.range.last + 1))
                        i = j + 1
                        closed = true
                        break
                    }
                }
                if (!closed) {
                    // 未闭合：到文末
                    ranges.add(open.range.first until text.length)
                    break
                }
            }
            return ranges
        }

        fun isInFencedBlock(pos: Int, ranges: List<IntRange>): Boolean =
            ranges.any { pos in it }
    }
}
