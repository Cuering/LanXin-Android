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

package com.lanxin.android.builtin.knowledge.data

import com.lanxin.android.builtin.knowledge.domain.DocumentParseException
import com.lanxin.android.builtin.knowledge.domain.DocumentParser
import com.lanxin.android.builtin.knowledge.domain.DocumentTypes
import com.lanxin.android.builtin.knowledge.domain.ParsedDocument
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Markdown 解析器。
 *
 * 导入路径保留原始 Markdown（含 ATX 标题），供 [com.lanxin.android.builtin.knowledge.domain.MarkdownChunker]
 * 做结构感知分段。纯文本化逻辑见 [stripMarkdown]。
 */
@Singleton
class MarkdownDocumentParser @Inject constructor(
    private val txtParser: TxtDocumentParser
) : DocumentParser {

    override fun supports(fileName: String, mimeType: String?): Boolean {
        val ext = DocumentTypes.extensionOf(fileName)
        if (ext == "md" || ext == "markdown" || ext == "mdown") return true
        val mime = mimeType?.lowercase().orEmpty()
        return mime == "text/markdown" || mime == "text/x-markdown"
    }

    override fun parse(fileName: String, input: InputStream, mimeType: String?): ParsedDocument {
        // 保留 Markdown 结构（标题 / fence），由 MarkdownChunker 负责切分
        val raw = try {
            txtParser.parse(fileName, input, mimeType ?: "text/markdown")
        } catch (e: DocumentParseException) {
            throw e
        }
        return ParsedDocument(
            fileName = fileName,
            mimeOrExt = mimeType ?: "text/markdown",
            text = raw.text
        )
    }

    /**
     * 轻量 Markdown → 纯文本。
     * - 保留代码块内文本
     * - 去掉标题标记、加粗/斜体、链接/图片语法、引用符、列表符
     * - 保留水平结构（换行）
     */
    fun stripMarkdown(md: String): String {
        if (md.isBlank()) return ""
        val lines = md.replace("\r\n", "\n").replace('\r', '\n').lines()
        val out = StringBuilder()
        var inCodeFence = false

        for (rawLine in lines) {
            var line = rawLine

            // fenced code
            if (line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~")) {
                inCodeFence = !inCodeFence
                // 代码围栏本身不输出
                continue
            }
            if (inCodeFence) {
                out.appendLine(line)
                continue
            }

            // 标题
            line = line.replace(Regex("^#{1,6}\\s+"), "")
            // 引用
            line = line.replace(Regex("^>\\s?"), "")
            // 无序/有序列表
            line = line.replace(Regex("^\\s*[-*+]\\s+"), "")
            line = line.replace(Regex("^\\s*\\d+\\.\\s+"), "")
            // 水平线
            if (line.trim().matches(Regex("^(-{3,}|\\*{3,}|_{3,})$"))) {
                out.appendLine()
                continue
            }
            // 图片 ![alt](url) → alt
            line = line.replace(Regex("!\\[([^\\]]*)]\\([^)]*\\)"), "$1")
            // 链接 [text](url) → text
            line = line.replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
            // 行内代码
            line = line.replace(Regex("`([^`]+)`"), "$1")
            // 加粗/斜体
            line = line.replace(Regex("(\\*\\*|__)(.+?)\\1"), "$2")
            line = line.replace(Regex("(\\*|_)(.+?)\\1"), "$2")
            // 删除线
            line = line.replace(Regex("~~(.+?)~~"), "$1")
            // HTML 标签（简单）
            line = line.replace(Regex("<[^>]+>"), "")

            out.appendLine(line)
        }

        return out.toString()
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
