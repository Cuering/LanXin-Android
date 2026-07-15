package com.lanxin.android.plugins.unifiedinbox.domain

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 跨工作区文件浏览（纯本地）。
 *
 * 默认根目录：`filesDir/workspaces/`
 * 也可浏览任意可读目录（由 ViewModel 传入绝对路径）。
 */
@Singleton
class UnifiedFileBrowser @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    data class FileItem(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val sizeBytes: Long,
        val lastModified: Long,
        val extension: String
    )

    data class FilePreview(
        val path: String,
        val name: String,
        val kind: PreviewKind,
        val textContent: String? = null,
        val error: String? = null
    )

    enum class PreviewKind {
        TEXT,
        IMAGE,
        BINARY,
        DIRECTORY,
        MISSING
    }

    fun defaultRoot(): File {
        val dir = File(context.filesDir, DEFAULT_WORKSPACES_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun listDirectory(path: String? = null): List<FileItem> = withContext(Dispatchers.IO) {
        val root = if (path.isNullOrBlank()) defaultRoot() else File(path)
        if (!root.exists()) {
            Log.w(TAG, "path not found: ${root.absolutePath}")
            return@withContext emptyList()
        }
        if (!root.isDirectory) {
            return@withContext emptyList()
        }
        val children = root.listFiles() ?: emptyArray()
        children
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .map { it.toItem() }
    }

    suspend fun listWorkspaces(): List<FileItem> = withContext(Dispatchers.IO) {
        val root = defaultRoot()
        val children = root.listFiles() ?: emptyArray()
        children
            .filter { it.isDirectory }
            .sortedBy { it.name.lowercase() }
            .map { it.toItem() }
    }

    suspend fun preview(path: String, maxTextBytes: Int = MAX_TEXT_PREVIEW_BYTES): FilePreview =
        withContext(Dispatchers.IO) {
            val file = File(path)
            if (!file.exists()) {
                return@withContext FilePreview(
                    path = path,
                    name = file.name,
                    kind = PreviewKind.MISSING,
                    error = "文件不存在"
                )
            }
            if (file.isDirectory) {
                return@withContext FilePreview(
                    path = path,
                    name = file.name,
                    kind = PreviewKind.DIRECTORY
                )
            }
            val ext = file.extension.lowercase()
            when {
                ext in IMAGE_EXTS -> FilePreview(
                    path = path,
                    name = file.name,
                    kind = PreviewKind.IMAGE
                )
                ext in TEXT_EXTS || looksLikeText(file, maxTextBytes) -> {
                    try {
                        val bytes = file.readBytes()
                        val limited = if (bytes.size > maxTextBytes) {
                            bytes.copyOf(maxTextBytes)
                        } else {
                            bytes
                        }
                        val text = limited.toString(Charsets.UTF_8)
                        val suffix = if (bytes.size > maxTextBytes) {
                            "\n\n…（已截断，原文件 ${bytes.size} bytes）"
                        } else {
                            ""
                        }
                        FilePreview(
                            path = path,
                            name = file.name,
                            kind = PreviewKind.TEXT,
                            textContent = text + suffix
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "preview failed: ${e.message}")
                        FilePreview(
                            path = path,
                            name = file.name,
                            kind = PreviewKind.BINARY,
                            error = e.message ?: "读取失败"
                        )
                    }
                }
                else -> FilePreview(
                    path = path,
                    name = file.name,
                    kind = PreviewKind.BINARY,
                    error = "二进制文件，暂不预览"
                )
            }
        }

    suspend fun parentPath(path: String): String? = withContext(Dispatchers.IO) {
        val file = File(path)
        val parent = file.parentFile ?: return@withContext null
        val root = defaultRoot()
        // 不允许越过默认 workspaces 根（除非当前路径本就不在其下）
        if (path.startsWith(root.absolutePath) && parent.absolutePath.length < root.absolutePath.length) {
            return@withContext null
        }
        parent.absolutePath
    }

    private fun File.toItem(): FileItem {
        return FileItem(
            name = name,
            path = absolutePath,
            isDirectory = isDirectory,
            sizeBytes = if (isDirectory) 0L else length(),
            lastModified = lastModified(),
            extension = extension.lowercase()
        )
    }

    private fun looksLikeText(file: File, sampleBytes: Int): Boolean {
        if (file.length() == 0L) return true
        return try {
            val sampleSize = minOf(file.length(), sampleBytes.toLong(), 512L).toInt()
            val buffer = ByteArray(sampleSize)
            file.inputStream().use { stream ->
                val read = stream.read(buffer)
                if (read <= 0) return true
                var nullCount = 0
                for (i in 0 until read) {
                    if (buffer[i] == 0.toByte()) nullCount++
                }
                nullCount == 0
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "UnifiedFileBrowser"
        const val DEFAULT_WORKSPACES_DIR = "workspaces"
        const val MAX_TEXT_PREVIEW_BYTES = 64 * 1024

        val TEXT_EXTS = setOf(
            "txt", "md", "markdown", "json", "xml", "yml", "yaml",
            "kt", "kts", "java", "py", "js", "ts", "tsx", "css", "html",
            "csv", "log", "ini", "conf", "properties", "toml", "sh", "gradle"
        )
        val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
    }
}
