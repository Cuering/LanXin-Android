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
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 纯文本解析器：直接读取 UTF-8（带回退）。
 */
@Singleton
class TxtDocumentParser @Inject constructor() : DocumentParser {

    override fun supports(fileName: String, mimeType: String?): Boolean {
        val ext = DocumentTypes.extensionOf(fileName)
        if (ext == "txt" || ext == "text" || ext == "log") return true
        val mime = mimeType?.lowercase().orEmpty()
        return mime == "text/plain" || mime.startsWith("text/")
    }

    override fun parse(fileName: String, input: InputStream, mimeType: String?): ParsedDocument {
        return try {
            val bytes = input.readBytes()
            if (bytes.size > MAX_BYTES) {
                throw DocumentParseException("文件过大（>${MAX_BYTES / 1024 / 1024}MB）：$fileName")
            }
            val text = decodeText(bytes)
            ParsedDocument(
                fileName = fileName,
                mimeOrExt = mimeType ?: "text/plain",
                text = text
            )
        } catch (e: DocumentParseException) {
            throw e
        } catch (e: Exception) {
            throw DocumentParseException("读取文本失败：$fileName — ${e.message}", e)
        }
    }

    private fun decodeText(bytes: ByteArray): String {
        // BOM 检测
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        return try {
            String(bytes, Charsets.UTF_8).also {
                // 若大量替换字符，尝试 GBK
                val bad = it.count { c -> c == '\uFFFD' }
                if (bad > it.length / 50 && it.isNotEmpty()) {
                    return String(bytes, Charset.forName("GBK"))
                }
            }
        } catch (_: Exception) {
            String(bytes, Charset.forName("GBK"))
        }
    }

    companion object {
        /** 单文件上限 20MB */
        const val MAX_BYTES = 20 * 1024 * 1024
    }
}
