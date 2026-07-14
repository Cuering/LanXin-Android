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
 * 按扩展名 / MIME 路由到具体解析器。
 */
@Singleton
class CompositeDocumentParser @Inject constructor(
    private val txt: TxtDocumentParser,
    private val md: MarkdownDocumentParser,
    private val pdf: PdfDocumentParser
) : DocumentParser {

    private val parsers: List<DocumentParser> = listOf(md, pdf, txt)

    override fun supports(fileName: String, mimeType: String?): Boolean {
        val ext = DocumentTypes.extensionOf(fileName)
        if (ext in DocumentTypes.EXTENSIONS) return true
        return parsers.any { it.supports(fileName, mimeType) }
    }

    override fun parse(fileName: String, input: InputStream, mimeType: String?): ParsedDocument {
        val parser = parsers.firstOrNull { it.supports(fileName, mimeType) }
            ?: throw DocumentParseException(
                "不支持的文件格式：${DocumentTypes.extensionOf(fileName).ifEmpty { mimeType ?: "unknown" }}，" +
                    "请选择 .txt / .md / .pdf"
            )
        return parser.parse(fileName, input, mimeType)
    }
}
