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

package com.lanxin.android.builtin.systemtools.domain

/**
 * 应用内笔记存储契约（Phase 7.3）。
 *
 * 真机：Room 私有 DB；单测：内存实现。
 * 不绑定厂商笔记 App。
 */
interface NotesStore {
    suspend fun list(limit: Int = 50): List<NoteEntry>

    suspend fun get(id: String): NoteEntry?

    suspend fun create(title: String, body: String): NoteEntry

    suspend fun append(id: String, text: String): NoteEntry

    suspend fun update(id: String, title: String?, body: String?): NoteEntry

    suspend fun delete(id: String): Boolean

    suspend fun count(): Int

    suspend fun clearAll()

    /** 批量 upsert（导入用）。返回写入条数。 */
    suspend fun upsertAll(notes: List<NoteEntry>): Int
}

/** 笔记导出格式。 */
enum class NotesExportFormat {
    JSON,
    MARKDOWN
}

/** 导入策略。 */
enum class NotesImportStrategy {
    /** 按 id 覆盖已有；新 id 追加 */
    MERGE,

    /** 清空后全量写入 */
    REPLACE
}

/**
 * 笔记序列化 / 反序列化（纯逻辑，可单测）。
 * SAF 只负责把文本读写到用户选中的 Uri。
 */
object NotesCodec {
    private const val SCHEMA_VERSION = 1
    private const val KIND = "lanxin.notes"

    fun toJsonBundle(notes: List<NoteEntry>, exportedAtEpochMs: Long = System.currentTimeMillis()): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"kind\": \"").append(KIND).append("\",\n")
        sb.append("  \"schema_version\": ").append(SCHEMA_VERSION).append(",\n")
        sb.append("  \"exported_at_epoch_ms\": ").append(exportedAtEpochMs).append(",\n")
        sb.append("  \"count\": ").append(notes.size).append(",\n")
        sb.append("  \"notes\": [\n")
        notes.forEachIndexed { index, n ->
            sb.append("    {\n")
            sb.append("      \"id\": ").append(jsonString(n.id)).append(",\n")
            sb.append("      \"title\": ").append(jsonString(n.title)).append(",\n")
            sb.append("      \"body\": ").append(jsonString(n.body)).append(",\n")
            sb.append("      \"updated_at_epoch_ms\": ").append(n.updatedAtEpochMs).append("\n")
            sb.append("    }")
            if (index < notes.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n")
        sb.append("}\n")
        return sb.toString()
    }

    fun toMarkdown(notes: List<NoteEntry>): String {
        if (notes.isEmpty()) return "# 兰心笔记\n\n（空）\n"
        val sb = StringBuilder("# 兰心笔记\n\n")
        notes.forEach { n ->
            sb.append("## ").append(n.title.ifBlank { "未命名" }).append("\n\n")
            sb.append("<!-- id: ").append(n.id)
                .append(" | updated: ").append(n.updatedAtEpochMs).append(" -->\n\n")
            sb.append(n.body).append("\n\n---\n\n")
        }
        return sb.toString()
    }

    /**
     * 解析导出 JSON。支持：
     * - 完整 bundle：`{ kind, notes: [...] }`
     * - 裸数组：`[ {id,title,body,...}, ... ]`
     */
    fun parseJsonBundle(text: String): List<NoteEntry> {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "导入内容为空" }
        val notesArray = when {
            trimmed.startsWith("[") -> trimmed
            else -> extractJsonArray(trimmed, "notes")
                ?: throw IllegalArgumentException("无法解析 notes 数组（需要 kind=lanxin.notes 或裸数组）")
        }
        return parseNotesArray(notesArray)
    }

    private fun extractJsonArray(obj: String, key: String): String? {
        val marker = "\"$key\""
        val keyIdx = obj.indexOf(marker)
        if (keyIdx < 0) return null
        val colon = obj.indexOf(':', keyIdx + marker.length)
        if (colon < 0) return null
        var i = colon + 1
        while (i < obj.length && obj[i].isWhitespace()) i++
        if (i >= obj.length || obj[i] != '[') return null
        var depth = 0
        val start = i
        while (i < obj.length) {
            val c = obj[i]
            when (c) {
                '"' -> {
                    i++
                    while (i < obj.length) {
                        if (obj[i] == '\\') {
                            i += 2
                            continue
                        }
                        if (obj[i] == '"') break
                        i++
                    }
                }
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return obj.substring(start, i + 1)
                }
            }
            i++
        }
        return null
    }

    private fun parseNotesArray(arrayText: String): List<NoteEntry> {
        val items = splitTopLevelObjects(arrayText)
        return items.mapNotNull { parseNoteObject(it) }
    }

    private fun splitTopLevelObjects(arrayText: String): List<String> {
        val body = arrayText.trim().removePrefix("[").removeSuffix("]").trim()
        if (body.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        var depth = 0
        var inString = false
        var escape = false
        var start = -1
        for (i in body.indices) {
            val c = body[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        out.add(body.substring(start, i + 1))
                        start = -1
                    }
                }
            }
        }
        return out
    }

    private fun parseNoteObject(obj: String): NoteEntry? {
        val id = readJsonStringField(obj, "id") ?: return null
        val title = readJsonStringField(obj, "title") ?: "未命名"
        val body = readJsonStringField(obj, "body") ?: ""
        val updated = readJsonLongField(obj, "updated_at_epoch_ms")
            ?: readJsonLongField(obj, "updated_at")
            ?: System.currentTimeMillis()
        return NoteEntry(id = id, title = title, body = body, updatedAtEpochMs = updated)
    }

    private fun readJsonStringField(obj: String, key: String): String? {
        val marker = "\"$key\""
        val keyIdx = obj.indexOf(marker)
        if (keyIdx < 0) return null
        val colon = obj.indexOf(':', keyIdx + marker.length)
        if (colon < 0) return null
        var i = colon + 1
        while (i < obj.length && obj[i].isWhitespace()) i++
        if (i >= obj.length) return null
        if (obj[i] == 'n' && obj.startsWith("null", i)) return null
        if (obj[i] != '"') return null
        i++
        val sb = StringBuilder()
        while (i < obj.length) {
            val c = obj[i]
            when {
                c == '\\' && i + 1 < obj.length -> {
                    val n = obj[i + 1]
                    when (n) {
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        'u' -> {
                            if (i + 5 < obj.length) {
                                val hex = obj.substring(i + 2, i + 6)
                                sb.append(hex.toInt(16).toChar())
                                i += 5
                            }
                        }
                        else -> sb.append(n)
                    }
                    i += 2
                }
                c == '"' -> return sb.toString()
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return null
    }

    private fun readJsonLongField(obj: String, key: String): Long? {
        val marker = "\"$key\""
        val keyIdx = obj.indexOf(marker)
        if (keyIdx < 0) return null
        val colon = obj.indexOf(':', keyIdx + marker.length)
        if (colon < 0) return null
        var i = colon + 1
        while (i < obj.length && obj[i].isWhitespace()) i++
        val start = i
        while (i < obj.length && (obj[i].isDigit() || obj[i] == '-')) i++
        if (start == i) return null
        return obj.substring(start, i).toLongOrNull()
    }

    private fun jsonString(value: String): String {
        val sb = StringBuilder("\"")
        for (c in value) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code < 0x20) {
                        sb.append("\\u%04x".format(c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }
}

/** 笔记 SAF 读写（Uri 由设置页或工具参数提供）。 */
interface NotesSafGateway {
    /**
     * 将 [text] 写入用户选择的 [uriString]（content:// 或 file://）。
     */
    fun writeText(uriString: String, text: String, mimeType: String = "application/json"): NotesIoResult

    /** 从 Uri 读取文本。 */
    fun readText(uriString: String): NotesIoResult

    /**
     * 分享文本（ACTION_SEND）；不落盘 SAF。
     */
    fun shareText(text: String, mimeType: String, chooserTitle: String = "导出笔记"): NotesIoResult
}

sealed class NotesIoResult {
    data class Ok(
        val message: String,
        val bytes: Int = 0,
        val uri: String? = null
    ) : NotesIoResult()

    data class Error(
        val message: String,
        val code: String = "notes_io_error"
    ) : NotesIoResult()
}
