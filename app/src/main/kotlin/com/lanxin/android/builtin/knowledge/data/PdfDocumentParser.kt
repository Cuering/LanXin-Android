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

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.lanxin.android.builtin.knowledge.domain.DocumentParseException
import com.lanxin.android.builtin.knowledge.domain.DocumentParser
import com.lanxin.android.builtin.knowledge.domain.DocumentTypes
import com.lanxin.android.builtin.knowledge.domain.ParsedDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PDF 解析器。
 *
 * 说明：Android 内置 [PdfRenderer] 主要用于渲染页面位图，**不提供文本提取 API**。
 * 本实现采用轻量启发式：扫描 PDF 内容流中的括号字符串字面量 `(...)` / `<hex>`，
 * 适用于大多数文本型 PDF；扫描件 / 加密 PDF 会得到空或残缺文本并抛出明确错误。
 *
 * 后续若效果不足，可替换为 pdfbox-android / iText 等第三方库。
 */
@Singleton
class PdfDocumentParser @Inject constructor(
    @ApplicationContext private val context: Context
) : DocumentParser {

    override fun supports(fileName: String, mimeType: String?): Boolean {
        val ext = DocumentTypes.extensionOf(fileName)
        if (ext == "pdf") return true
        return mimeType?.lowercase() == "application/pdf"
    }

    override fun parse(fileName: String, input: InputStream, mimeType: String?): ParsedDocument {
        val cache = File(context.cacheDir, "kb_import_${System.currentTimeMillis()}.pdf")
        try {
            cache.outputStream().use { out -> input.copyTo(out) }
            if (cache.length() > MAX_BYTES) {
                throw DocumentParseException("PDF 过大（>${MAX_BYTES / 1024 / 1024}MB）：$fileName")
            }
            if (cache.length() < 5) {
                throw DocumentParseException("PDF 文件为空：$fileName")
            }

            // 校验是否可被系统打开（损坏检测）
            val pageCount = openPageCount(cache)

            val text = extractTextHeuristic(cache)
            if (text.isBlank()) {
                throw DocumentParseException(
                    "未能从 PDF 提取文本（可能是扫描件或加密文档）：$fileName，共 $pageCount 页"
                )
            }

            return ParsedDocument(
                fileName = fileName,
                mimeOrExt = mimeType ?: "application/pdf",
                text = text
            )
        } catch (e: DocumentParseException) {
            throw e
        } catch (e: SecurityException) {
            throw DocumentParseException("PDF 受密码保护或无法访问：$fileName", e)
        } catch (e: Exception) {
            throw DocumentParseException("PDF 解析失败：$fileName — ${e.message}", e)
        } finally {
            if (cache.exists()) {
                runCatching { cache.delete() }
            }
        }
    }

    private fun openPageCount(file: File): Int {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val count = renderer.pageCount
                if (count <= 0) throw DocumentParseException("PDF 无页面")
                if (count > MAX_PAGES) {
                    throw DocumentParseException("PDF 页数过多（>$MAX_PAGES）：$count 页")
                }
                return count
            }
        }
    }

    /**
     * 从 PDF 二进制中提取可见字符串。
     * 解析 content stream 中的 `(...)` Tj/TJ 字面量与部分 UTF-16BE 十六进制串。
     */
    private fun extractTextHeuristic(file: File): String {
        val bytes = file.readBytes()
        // 简单加密检测
        val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
        if (head.contains("/Encrypt")) {
            Log.w(TAG, "PDF appears encrypted")
            throw DocumentParseException("PDF 已加密，无法提取文本")
        }

        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                // 字符串字面量 ( ... )
                b == '('.code -> {
                    val (str, next) = readPdfString(bytes, i + 1)
                    if (str.isNotBlank() && isPrintable(str)) {
                        sb.append(str)
                        // 词间空格启发
                        if (sb.isNotEmpty() && sb.last() != '\n') sb.append(' ')
                    }
                    i = next
                }
                // 十六进制字符串 < ... >
                b == '<'.code && i + 1 < bytes.size && isHex(bytes[i + 1]) -> {
                    val (str, next) = readPdfHexString(bytes, i + 1)
                    if (str.isNotBlank() && isPrintable(str)) {
                        sb.append(str)
                        if (sb.isNotEmpty() && sb.last() != '\n') sb.append(' ')
                    }
                    i = next
                }
                // 换行操作符启发：T* / '
                b == 'T'.code && i + 1 < bytes.size && bytes[i + 1].toInt() and 0xFF == '*'.code -> {
                    sb.append('\n')
                    i += 2
                }
                else -> i++
            }
        }

        return sb.toString()
            .replace(Regex("[ \\t\\x0B\\f]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun readPdfString(bytes: ByteArray, start: Int): Pair<String, Int> {
        val out = StringBuilder()
        var i = start
        var depth = 1
        while (i < bytes.size && depth > 0) {
            val c = bytes[i].toInt() and 0xFF
            when (c) {
                '\\'.code -> {
                    if (i + 1 >= bytes.size) break
                    val n = bytes[i + 1].toInt() and 0xFF
                    when (n.toChar()) {
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        '(', ')', '\\' -> out.append(n.toChar())
                        else -> {
                            // 八进制转义 \ddd
                            if (n in '0'.code..'7'.code) {
                                var oct = ""
                                var j = i + 1
                                while (j < bytes.size && j < i + 4) {
                                    val d = bytes[j].toInt() and 0xFF
                                    if (d !in '0'.code..'7'.code) break
                                    oct += d.toChar()
                                    j++
                                }
                                out.append(oct.toInt(8).toChar())
                                i = j - 1
                            } else {
                                out.append(n.toChar())
                            }
                        }
                    }
                    i += 2
                }
                '('.code -> {
                    depth++
                    out.append('(')
                    i++
                }
                ')'.code -> {
                    depth--
                    if (depth > 0) out.append(')')
                    i++
                }
                else -> {
                    // PDF DocEncoding / 直接字节：尽量按 Latin1，中文 PDF 常为 CID 无法还原
                    if (c in 32..126 || c >= 0xA0) {
                        out.append(c.toChar())
                    }
                    i++
                }
            }
        }
        return out.toString() to i
    }

    private fun readPdfHexString(bytes: ByteArray, start: Int): Pair<String, Int> {
        val hex = StringBuilder()
        var i = start
        while (i < bytes.size) {
            val c = bytes[i].toInt() and 0xFF
            if (c == '>'.code) {
                i++
                break
            }
            if (isHex(bytes[i]) || c == ' '.code || c == '\n'.code || c == '\r'.code || c == '\t'.code) {
                if (isHex(bytes[i])) hex.append(c.toChar())
                i++
            } else {
                break
            }
        }
        val clean = hex.toString()
        if (clean.length < 4) return "" to i
        // UTF-16BE BOM FEFF
        return try {
            val data = HexFormat.parse(clean)
            if (data.size >= 2 && data[0] == 0xFE.toByte() && data[1] == 0xFF.toByte()) {
                String(data, 2, data.size - 2, Charsets.UTF_16BE) to i
            } else {
                // 按 Latin1 字节
                String(data, Charsets.ISO_8859_1) to i
            }
        } catch (_: Exception) {
            "" to i
        }
    }

    private fun isHex(b: Byte): Boolean {
        val c = b.toInt() and 0xFF
        return c in '0'.code..'9'.code ||
            c in 'a'.code..'f'.code ||
            c in 'A'.code..'F'.code
    }

    private fun isPrintable(s: String): Boolean {
        if (s.isBlank()) return false
        val printable = s.count { it.code >= 32 || it == '\n' || it == '\t' }
        return printable >= s.length * 0.6
    }

    private object HexFormat {
        fun parse(hex: String): ByteArray {
            val h = if (hex.length % 2 != 0) hex + "0" else hex
            return ByteArray(h.length / 2) { idx ->
                h.substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
            }
        }
    }

    companion object {
        private const val TAG = "PdfDocumentParser"
        const val MAX_BYTES = 30 * 1024 * 1024
        const val MAX_PAGES = 500
    }
}
