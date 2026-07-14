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

import java.io.InputStream

/**
 * 文档解析结果。
 */
data class ParsedDocument(
    val fileName: String,
    val mimeOrExt: String,
    val text: String,
    val charCount: Int = text.length
)

/**
 * 文档解析器接口。
 * 将 txt / md / pdf 转为纯文本。
 */
interface DocumentParser {
    /** 是否支持该扩展名（小写，不含点）或 mime */
    fun supports(fileName: String, mimeType: String?): Boolean

    /**
     * 解析输入流为纯文本。
     * @throws DocumentParseException 解析失败
     */
    fun parse(fileName: String, input: InputStream, mimeType: String? = null): ParsedDocument
}

class DocumentParseException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * 支持的文档类型。
 */
object DocumentTypes {
    val EXTENSIONS = setOf("txt", "md", "markdown", "pdf")
    val MIME_TYPES = arrayOf(
        "text/plain",
        "text/markdown",
        "text/x-markdown",
        "application/pdf",
        "application/octet-stream",
        "*/*"
    )

    fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .trim()
}
