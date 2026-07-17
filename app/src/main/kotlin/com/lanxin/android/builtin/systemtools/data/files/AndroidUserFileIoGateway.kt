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

package com.lanxin.android.builtin.systemtools.data.files

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.lanxin.android.builtin.systemtools.domain.UserFileEntry
import com.lanxin.android.builtin.systemtools.domain.UserFileIoGateway
import com.lanxin.android.builtin.systemtools.domain.UserFileIoResult
import com.lanxin.android.builtin.systemtools.domain.UserFileProbe
import com.lanxin.android.builtin.systemtools.domain.guessMimeFromName
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SAF content Uri + 应用私有 imports 目录 IO。
 *
 * 不申请 MANAGE_EXTERNAL_STORAGE；禁止系统分区路径。
 * 对 content:// 尽力 takePersistableUriPermission（OpenDocument / CreateDocument）。
 */
@Singleton
class AndroidUserFileIoGateway @Inject constructor(
    @ApplicationContext private val context: Context
) : UserFileIoGateway {

    /** 尽力持久化 content Uri 读/写权限（失败静默，仍可用当次授权）。 */
    override fun takePersistableIfPossible(uriString: String): Boolean {
        if (!uriString.startsWith("content://")) return false
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.takePersistableUriPermission(
                uri,
                FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION
            )
            true
        } catch (_: SecurityException) {
            try {
                val uri = Uri.parse(uriString)
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    FLAG_GRANT_READ_URI_PERMISSION
                )
                true
            } catch (_: Exception) {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /** 删除 content Uri 文档（DocumentsContract）；应用私有见 [deleteAppPrivate]。 */
    override fun deleteDocument(uriString: String): UserFileIoResult {
        return try {
            if (!uriString.startsWith("content://")) {
                return deleteAppPrivate(uriString)
            }
            val uri = Uri.parse(uriString)
            rejectDangerousPath(uri)
            val ok = DocumentsContract.deleteDocument(context.contentResolver, uri)
            if (ok) {
                UserFileIoResult.Ok(message = "已删除文档", uri = uriString)
            } else {
                UserFileIoResult.Error("DocumentsContract 删除失败", "delete_failed")
            }
        } catch (e: SecurityException) {
            UserFileIoResult.Error("无权限删除：${e.message}", "security")
        } catch (e: Exception) {
            UserFileIoResult.Error(e.message ?: e.toString(), "delete_failed")
        }
    }

    override fun readText(uriOrPath: String, maxChars: Int): UserFileIoResult {
        return try {
            val limit = maxChars.coerceIn(256, 500_000)
            val text = when {
                uriOrPath.startsWith("content://") || uriOrPath.startsWith("file://") -> {
                    val uri = Uri.parse(uriOrPath)
                    rejectDangerousPath(uri)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        readLimited(BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)), limit)
                    } ?: return UserFileIoResult.Error("无法打开 Uri 读取: $uriOrPath", "uri_open_failed")
                }
                else -> {
                    val file = resolvePrivateFile(uriOrPath)
                        ?: return UserFileIoResult.Error("文件不存在: $uriOrPath", "not_found")
                    rejectDangerousFile(file)
                    file.bufferedReader(StandardCharsets.UTF_8).use { readLimited(it, limit) }
                }
            }
            val fullLen = text.first
            val body = text.second
            UserFileIoResult.Ok(
                message = body,
                bytes = body.toByteArray(StandardCharsets.UTF_8).size,
                uri = uriOrPath,
                preview = body.take(500),
                truncated = fullLen > limit
            )
        } catch (e: SecurityException) {
            UserFileIoResult.Error("无权限读取：${e.message}", "security")
        } catch (e: Exception) {
            UserFileIoResult.Error(e.message ?: e.toString(), "read_failed")
        }
    }

    override fun writeText(uriOrPath: String, text: String, mimeType: String): UserFileIoResult {
        return try {
            when {
                uriOrPath.startsWith("content://") || uriOrPath.startsWith("file://") -> {
                    val uri = Uri.parse(uriOrPath)
                    rejectDangerousPath(uri)
                    val bytes = text.toByteArray(StandardCharsets.UTF_8)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(bytes)
                        out.flush()
                    } ?: return UserFileIoResult.Error("无法打开 Uri 写入: $uriOrPath", "uri_open_failed")
                    UserFileIoResult.Ok(
                        message = "已写入 ${bytes.size} 字节",
                        bytes = bytes.size,
                        uri = uriOrPath
                    )
                }
                else -> {
                    val file = File(uriOrPath)
                    rejectDangerousFile(file)
                    file.parentFile?.mkdirs()
                    val bytes = text.toByteArray(StandardCharsets.UTF_8)
                    file.writeBytes(bytes)
                    UserFileIoResult.Ok(
                        message = "已写入 ${bytes.size} 字节",
                        bytes = bytes.size,
                        uri = file.absolutePath,
                        name = file.name
                    )
                }
            }
        } catch (e: SecurityException) {
            UserFileIoResult.Error("无权限写入：${e.message}", "security")
        } catch (e: Exception) {
            UserFileIoResult.Error(e.message ?: e.toString(), "write_failed")
        }
    }

    override fun copyToAppPrivate(uriString: String, preferredName: String?): UserFileIoResult {
        return try {
            val uri = Uri.parse(uriString)
            rejectDangerousPath(uri)
            val probe = probe(uriString)
            val rawName = preferredName?.trim()?.takeIf { it.isNotEmpty() }
                ?: probe?.name
                ?: "import_${System.currentTimeMillis()}"
            val safeName = sanitizeFileName(rawName)
            val dir = importsDir()
            dir.mkdirs()
            var target = File(dir, safeName)
            if (target.exists()) {
                val base = safeName.substringBeforeLast('.', safeName)
                val ext = if (safeName.contains('.')) ".${safeName.substringAfterLast('.')}" else ""
                target = File(dir, "${base}_${System.currentTimeMillis()}$ext")
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            } ?: return UserFileIoResult.Error("无法打开 Uri 复制: $uriString", "uri_open_failed")
            UserFileIoResult.Ok(
                message = "已导入到应用目录",
                bytes = target.length().toInt().coerceAtLeast(0),
                uri = target.absolutePath,
                name = target.name
            )
        } catch (e: SecurityException) {
            UserFileIoResult.Error("无权限复制：${e.message}", "security")
        } catch (e: Exception) {
            UserFileIoResult.Error(e.message ?: e.toString(), "copy_failed")
        }
    }

    override fun writeAppPrivateText(name: String, text: String, mimeType: String): UserFileIoResult {
        return try {
            val safeName = sanitizeFileName(name.ifBlank { "note_${System.currentTimeMillis()}.txt" })
            val dir = importsDir()
            dir.mkdirs()
            val target = File(dir, safeName)
            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            target.writeBytes(bytes)
            UserFileIoResult.Ok(
                message = "已写入应用目录 ${target.name}",
                bytes = bytes.size,
                uri = target.absolutePath,
                name = target.name
            )
        } catch (e: Exception) {
            UserFileIoResult.Error(e.message ?: e.toString(), "write_failed")
        }
    }

    override fun listAppPrivateFiles(): List<UserFileEntry> {
        val dir = importsDir()
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile }
            ?.map { f ->
                UserFileEntry(
                    id = "app:${f.absolutePath}",
                    uriOrPath = f.absolutePath,
                    name = f.name,
                    isDirectory = false,
                    sizeBytes = f.length(),
                    mimeType = guessMimeFromName(f.name),
                    modifiedAtEpochMs = f.lastModified(),
                    source = "app_private"
                )
            }
            .orEmpty()
    }

    override fun deleteAppPrivate(pathOrName: String): UserFileIoResult {
        return try {
            val file = resolvePrivateFile(pathOrName)
                ?: return UserFileIoResult.Error("文件不存在: $pathOrName", "not_found")
            rejectDangerousFile(file)
            if (!file.absolutePath.startsWith(importsDir().absolutePath)) {
                return UserFileIoResult.Error("只能删除应用 imports 目录内文件", "forbidden_path")
            }
            val ok = file.delete()
            if (!ok) {
                UserFileIoResult.Error("删除失败: ${file.name}", "delete_failed")
            } else {
                UserFileIoResult.Ok(message = "已删除 ${file.name}", uri = file.absolutePath, name = file.name)
            }
        } catch (e: Exception) {
            UserFileIoResult.Error(e.message ?: e.toString(), "delete_failed")
        }
    }

    override fun shareUri(uriOrPath: String, mimeType: String?, chooserTitle: String): UserFileIoResult {
        return try {
            val uri = when {
                uriOrPath.startsWith("content://") -> Uri.parse(uriOrPath)
                uriOrPath.startsWith("file://") -> {
                    val parsed = Uri.parse(uriOrPath)
                    rejectDangerousPath(parsed)
                    val file = File(parsed.path.orEmpty())
                    rejectDangerousFile(file)
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }
                else -> {
                    val file = resolvePrivateFile(uriOrPath)
                        ?: return UserFileIoResult.Error("文件不存在: $uriOrPath", "not_found")
                    rejectDangerousFile(file)
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }
            }
            val type = mimeType
                ?: context.contentResolver.getType(uri)
                ?: guessMimeFromName(uri.lastPathSegment.orEmpty())
            val send = Intent(Intent.ACTION_SEND).apply {
                this.type = type
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolved = chooser.resolveActivity(context.packageManager)
                ?: send.resolveActivity(context.packageManager)
            if (resolved == null) {
                return UserFileIoResult.Error("没有可处理分享的应用", "activity_not_found")
            }
            context.startActivity(chooser)
            UserFileIoResult.Ok(message = "已打开分享面板", uri = uri.toString())
        } catch (e: Exception) {
            UserFileIoResult.Error(e.message ?: e.toString(), "share_failed")
        }
    }

    override fun shareText(text: String, mimeType: String, chooserTitle: String): UserFileIoResult {
        return try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(send, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolved = chooser.resolveActivity(context.packageManager)
                ?: send.resolveActivity(context.packageManager)
            if (resolved == null) {
                return UserFileIoResult.Error("没有可处理分享的应用", "activity_not_found")
            }
            context.startActivity(chooser)
            UserFileIoResult.Ok(
                message = "已打开分享面板",
                bytes = text.toByteArray(StandardCharsets.UTF_8).size
            )
        } catch (e: Exception) {
            UserFileIoResult.Error(e.message ?: e.toString(), "share_failed")
        }
    }

    override fun probe(uriString: String): UserFileProbe? {
        return try {
            val uri = Uri.parse(uriString)
            rejectDangerousPath(uri)
            var name: String? = uri.lastPathSegment
            var size: Long? = null
            var modified: Long? = null
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
            val mime = context.contentResolver.getType(uri)
            UserFileProbe(
                name = name?.substringAfterLast('/')?.ifBlank { null } ?: "unknown",
                sizeBytes = size,
                mimeType = mime,
                modifiedAtEpochMs = modified
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun importsDir(): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, UserFileIoGateway.APP_IMPORTS_SUBDIR)
    }

    private fun resolvePrivateFile(pathOrName: String): File? {
        val asFile = File(pathOrName)
        if (asFile.isFile) return asFile
        val inImports = File(importsDir(), pathOrName)
        if (inImports.isFile) return inImports
        return null
    }

    private fun readLimited(reader: BufferedReader, maxChars: Int): Pair<Int, String> {
        val sb = StringBuilder()
        val buf = CharArray(4096)
        var total = 0
        while (true) {
            val n = reader.read(buf)
            if (n < 0) break
            total += n
            if (sb.length < maxChars) {
                val need = maxChars - sb.length
                sb.append(buf, 0, minOf(n, need))
            }
        }
        return total to sb.toString()
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(180)
        return cleaned.ifBlank { "file_${System.currentTimeMillis()}" }
    }

    private fun rejectDangerousPath(uri: Uri) {
        val path = uri.path.orEmpty()
        val scheme = uri.scheme.orEmpty().lowercase()
        if (scheme == "file") {
            rejectDangerousAbsolute(path)
        }
    }

    private fun rejectDangerousFile(file: File) {
        rejectDangerousAbsolute(file.canonicalFile.absolutePath)
    }

    private fun rejectDangerousAbsolute(path: String) {
        val forbidden = listOf(
            "/system", "/vendor", "/proc", "/dev", "/sys",
            "/data/system", "/data/misc", "/data/data"
        )
        // 允许本应用 files / externalFiles
        val appFiles = context.filesDir.canonicalFile.absolutePath
        val appExt = context.getExternalFilesDir(null)?.canonicalFile?.absolutePath
        val cache = context.cacheDir.canonicalFile.absolutePath
        if (path.startsWith(appFiles) || (appExt != null && path.startsWith(appExt)) ||
            path.startsWith(cache)
        ) {
            return
        }
        if (forbidden.any { path == it || path.startsWith("$it/") }) {
            throw SecurityException("禁止访问系统路径: $path")
        }
    }

    @Suppress("unused")
    private fun extensionMime(name: String): String? {
        val ext = MimeTypeMap.getFileExtensionFromUrl(name) ?: return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }
}
