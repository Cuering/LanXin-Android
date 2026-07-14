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

package com.lanxin.android.builtin.platform.tools

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Android 应用内文件读写与目录列举。
 *
 * - [read]：支持应用私有路径、`content://` / `file://` URI（ContentResolver）
 * - [write]：仅写到应用私有目录（filesDir / cacheDir 下相对路径）
 * - [list]：列举应用私有目录文件
 *
 * 不做任意外部路径写操作，避免 scoped storage 与安全风险。
 */
@Singleton
class FileOpsTool @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun read(
        path: String? = null,
        uri: String? = null,
        encoding: String = "UTF-8",
        maxBytes: Int = DEFAULT_MAX_BYTES
    ): JsonObject {
        val safeMax = maxBytes.coerceIn(1, ABSOLUTE_MAX_BYTES)
        val charset = runCatching { Charset.forName(encoding) }.getOrElse {
            return err("不支持的 encoding：$encoding")
        }

        return when {
            !uri.isNullOrBlank() -> readUri(uri.trim(), charset, safeMax)
            !path.isNullOrBlank() -> readPath(path.trim(), charset, safeMax)
            else -> err("path 与 uri 至少提供一个")
        }
    }

    fun write(
        path: String,
        content: String,
        encoding: String = "UTF-8",
        append: Boolean = false,
        base: String = "files"
    ): JsonObject {
        val charset = runCatching { Charset.forName(encoding) }.getOrElse {
            return err("不支持的 encoding：$encoding")
        }
        val rel = path.trim().trimStart('/')
        if (rel.isEmpty()) return err("path 不能为空")
        if (rel.contains("..")) return err("path 不允许包含 ..")

        val root = baseRoot(base) ?: return err("base 仅支持 files / cache，收到：$base")
        val target = File(root, rel).canonicalFile
        if (!target.path.startsWith(root.canonicalPath + File.separator) &&
            target.canonicalPath != root.canonicalPath
        ) {
            return err("path 越界，禁止写到应用私有目录之外")
        }

        return try {
            target.parentFile?.mkdirs()
            if (append && target.exists()) {
                target.appendText(content, charset)
            } else {
                target.writeText(content, charset)
            }
            buildJsonObject {
                put("ok", true)
                put("path", target.absolutePath)
                put("relative_path", rel)
                put("base", base)
                put("bytes", target.length())
                put("append", append)
            }
        } catch (e: Exception) {
            err("写入失败：${e.message}")
        }
    }

    fun list(
        path: String = "",
        base: String = "files",
        recursive: Boolean = false,
        limit: Int = 200
    ): JsonObject {
        val safeLimit = limit.coerceIn(1, 2000)
        val root = baseRoot(base) ?: return err("base 仅支持 files / cache，收到：$base")
        val rel = path.trim().trimStart('/')
        if (rel.contains("..")) return err("path 不允许包含 ..")

        val dir = if (rel.isEmpty()) root else File(root, rel)
        val canonical = runCatching { dir.canonicalFile }.getOrElse {
            return err("无效路径：${it.message}")
        }
        if (!canonical.path.startsWith(root.canonicalPath)) {
            return err("path 越界，禁止访问应用私有目录之外")
        }
        if (!canonical.exists()) {
            return buildJsonObject {
                put("ok", true)
                put("exists", false)
                put("path", canonical.absolutePath)
                put("entries", buildJsonArray { })
            }
        }
        if (!canonical.isDirectory) {
            return err("path 不是目录：${canonical.absolutePath}")
        }

        val entries = mutableListOf<File>()
        if (recursive) {
            canonical.walkTopDown()
                .maxDepth(8)
                .filter { it != canonical }
                .forEach {
                    if (entries.size >= safeLimit) return@forEach
                    entries.add(it)
                }
        } else {
            canonical.listFiles()
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?.take(safeLimit)
                ?.let { entries.addAll(it) }
        }

        return buildJsonObject {
            put("ok", true)
            put("exists", true)
            put("path", canonical.absolutePath)
            put("base", base)
            put("relative_path", rel)
            put("returned", entries.size)
            put("truncated", entries.size >= safeLimit)
            put(
                "entries",
                buildJsonArray {
                    entries.forEach { f ->
                        add(
                            buildJsonObject {
                                put("name", f.name)
                                put(
                                    "relative_path",
                                    f.relativeTo(root).path
                                )
                                put("is_dir", f.isDirectory)
                                put("size", if (f.isFile) f.length() else 0L)
                                put("last_modified", f.lastModified())
                            }
                        )
                    }
                }
            )
        }
    }

    private fun readPath(path: String, charset: Charset, maxBytes: Int): JsonObject {
        if (path.contains("..")) return err("path 不允许包含 ..")
        val file = resolveReadableFile(path)
            ?: return err("无法解析 path：$path（仅支持应用私有 files/cache 相对路径或绝对路径）")
        if (!file.exists()) return err("文件不存在：${file.absolutePath}")
        if (!file.isFile) return err("不是文件：${file.absolutePath}")
        if (file.length() > maxBytes) {
            return err("文件过大 ${file.length()} bytes，上限 $maxBytes；可增大 max_bytes")
        }
        return try {
            val text = file.readText(charset)
            buildJsonObject {
                put("ok", true)
                put("source", "path")
                put("path", file.absolutePath)
                put("bytes", file.length())
                put("encoding", charset.name())
                put("content", text)
            }
        } catch (e: Exception) {
            err("读取失败：${e.message}")
        }
    }

    private fun readUri(uriString: String, charset: Charset, maxBytes: Int): JsonObject {
        val uri = runCatching { Uri.parse(uriString) }.getOrElse {
            return err("无效 URI：$uriString")
        }
        return try {
            when (uri.scheme?.lowercase()) {
                "file" -> {
                    val path = uri.path ?: return err("file URI 无 path")
                    readPath(path, charset, maxBytes)
                }
                "content" -> readContentUri(uri, charset, maxBytes)
                else -> err("仅支持 content:// 或 file://，收到 scheme=${uri.scheme}")
            }
        } catch (e: Exception) {
            err("读取 URI 失败：${e.message}")
        }
    }

    private fun readContentUri(uri: Uri, charset: Charset, maxBytes: Int): JsonObject {
        val cr = context.contentResolver
        val meta = queryUriMeta(uri)
        cr.openInputStream(uri)?.use { input ->
            val reader = BufferedReader(InputStreamReader(input, charset))
            val sb = StringBuilder()
            val buf = CharArray(8 * 1024)
            var totalChars = 0
            var n: Int
            while (reader.read(buf).also { n = it } != -1) {
                totalChars += n
                if (totalChars > maxBytes) {
                    return err("内容超过 max_bytes=$maxBytes")
                }
                sb.append(buf, 0, n)
            }
            val text = sb.toString()
            return buildJsonObject {
                put("ok", true)
                put("source", "uri")
                put("uri", uri.toString())
                put("display_name", meta.first)
                put("mime_type", meta.second.orEmpty())
                put("bytes", text.toByteArray(charset).size)
                put("encoding", charset.name())
                put("content", text)
            }
        }
        return err("无法打开 content URI：$uri")
    }

    private fun queryUriMeta(uri: Uri): Pair<String, String?> {
        val cr = context.contentResolver
        val mime = runCatching { cr.getType(uri) }.getOrNull()
        var name = uri.lastPathSegment.orEmpty()
        runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = c.getString(idx).orEmpty()
                }
            }
        }
        return name to mime
    }

    /**
     * 解析可读文件：
     * - 绝对路径且落在 filesDir/cacheDir 内
     * - 相对路径默认落在 filesDir
     * - `files/` / `cache/` 前缀
     */
    private fun resolveReadableFile(path: String): File? {
        val filesRoot = context.filesDir.canonicalFile
        val cacheRoot = context.cacheDir.canonicalFile

        val candidate = when {
            path.startsWith("files/") -> File(filesRoot, path.removePrefix("files/"))
            path.startsWith("cache/") -> File(cacheRoot, path.removePrefix("cache/"))
            path.startsWith("/") -> File(path)
            else -> File(filesRoot, path)
        }.canonicalFile

        val allowed = candidate.path.startsWith(filesRoot.path) ||
            candidate.path.startsWith(cacheRoot.path)
        return if (allowed) candidate else null
    }

    private fun baseRoot(base: String): File? = when (base.lowercase()) {
        "files", "file", "filesdir" -> context.filesDir
        "cache", "cachedir" -> context.cacheDir
        else -> null
    }

    private fun err(message: String): JsonObject = buildJsonObject {
        put("ok", false)
        put("error", message)
    }

    companion object {
        const val DEFAULT_MAX_BYTES = 256 * 1024
        const val ABSOLUTE_MAX_BYTES = 2 * 1024 * 1024
    }
}
