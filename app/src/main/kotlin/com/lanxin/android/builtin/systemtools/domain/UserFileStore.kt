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
 * 用户文件目录 / IO 契约（Phase 7.4）。
 *
 * 范围：SAF 用户授权文件 + 应用私有目录（imports）。
 * 禁止：root、系统分区、全盘静默扫描。
 */

/** 列表排序。 */
enum class UserFileSort {
    NAME,
    DATE_DESC,
    DATE_ASC,
    TYPE,
    SIZE
}

/** 文件来源。 */
enum class UserFileSource {
    /** 用户通过 SAF 选取并登记的 content Uri */
    SAF,

    /** 应用 getExternalFilesDir / filesDir 下 imports 子目录 */
    APP_PRIVATE
}

/**
 * 应用内「用户文件目录」：登记 SAF 选取结果 + 应用私有副本元数据。
 * 真机与单测共用；不扫描系统分区。
 */
interface UserFileCatalog {
    suspend fun list(
        sort: UserFileSort = UserFileSort.DATE_DESC,
        limit: Int = 100,
        source: UserFileSource? = null
    ): List<UserFileEntry>

    suspend fun get(id: String): UserFileEntry?

    /** 登记或更新条目（按 id）。 */
    suspend fun upsert(entry: UserFileEntry): UserFileEntry

    suspend fun remove(id: String): Boolean

    suspend fun count(): Int

    suspend fun clearAll()
}

/**
 * 用户文件 IO：SAF content Uri + 应用私有目录。
 * Uri 由设置页 OpenDocument / CreateDocument 或工具参数提供。
 */
interface UserFileIoGateway {
    /** 读取文本（限制 maxChars，避免大文件灌入模型上下文）。 */
    fun readText(uriOrPath: String, maxChars: Int = DEFAULT_MAX_CHARS): UserFileIoResult

    /** 写入文本到 content Uri 或 app 私有绝对路径。 */
    fun writeText(
        uriOrPath: String,
        text: String,
        mimeType: String = "text/plain"
    ): UserFileIoResult

    /**
     * 将 SAF Uri 复制到应用私有 imports 目录。
     * @return Ok 时 [UserFileIoResult.Ok.uri] 为私有文件绝对路径，message 为显示名。
     */
    fun copyToAppPrivate(uriString: String, preferredName: String? = null): UserFileIoResult

    /** 在应用私有 imports 下创建文本文件。 */
    fun writeAppPrivateText(
        name: String,
        text: String,
        mimeType: String = "text/plain"
    ): UserFileIoResult

    /** 列出应用私有 imports 目录文件。 */
    fun listAppPrivateFiles(): List<UserFileEntry>

    /** 删除应用私有文件（绝对路径或文件名）。 */
    fun deleteAppPrivate(pathOrName: String): UserFileIoResult

    /** 分享 content Uri 或 file Uri。 */
    fun shareUri(
        uriOrPath: String,
        mimeType: String? = null,
        chooserTitle: String = "分享文件"
    ): UserFileIoResult

    /** 分享纯文本。 */
    fun shareText(
        text: String,
        mimeType: String = "text/plain",
        chooserTitle: String = "分享文本"
    ): UserFileIoResult

    /** 查询 content Uri 显示名 / 大小 / mime（尽力而为）。 */
    fun probe(uriString: String): UserFileProbe?

    /**
     * 尽力 takePersistableUriPermission（OpenDocument / CreateDocument）。
     * 默认 no-op；Android 实现覆盖。
     */
    fun takePersistableIfPossible(uriString: String): Boolean = false

    /**
     * 删除 content 文档（DocumentsContract）或应用私有文件。
     * 默认走 [deleteAppPrivate]；Android 实现覆盖 SAF 删除。
     */
    fun deleteDocument(uriString: String): UserFileIoResult = deleteAppPrivate(uriString)

    companion object {
        const val DEFAULT_MAX_CHARS = 50_000
        const val APP_IMPORTS_SUBDIR = "imports"
    }
}

/** Uri 元数据探测结果。 */
data class UserFileProbe(
    val name: String,
    val sizeBytes: Long? = null,
    val mimeType: String? = null,
    val modifiedAtEpochMs: Long? = null
)

sealed class UserFileIoResult {
    data class Ok(
        val message: String,
        val bytes: Int = 0,
        val uri: String? = null,
        val name: String? = null,
        val preview: String? = null,
        val truncated: Boolean = false
    ) : UserFileIoResult()

    data class Error(
        val message: String,
        val code: String = "user_file_io_error"
    ) : UserFileIoResult()
}

/** 解析 sort 字符串。 */
fun parseUserFileSort(raw: String?): UserFileSort {
    return when (raw?.lowercase()?.trim().orEmpty()) {
        "", "date", "date_desc", "updated", "modified" -> UserFileSort.DATE_DESC
        "date_asc", "oldest" -> UserFileSort.DATE_ASC
        "name", "alpha" -> UserFileSort.NAME
        "type", "mime" -> UserFileSort.TYPE
        "size", "size_desc" -> UserFileSort.SIZE
        else -> UserFileSort.DATE_DESC
    }
}

/** 对列表排序（纯函数，可单测）。 */
fun sortUserFiles(files: List<UserFileEntry>, sort: UserFileSort): List<UserFileEntry> {
    return when (sort) {
        UserFileSort.NAME -> files.sortedBy { it.name.lowercase() }
        UserFileSort.DATE_DESC -> files.sortedByDescending {
            it.modifiedAtEpochMs.takeIf { t -> t > 0 } ?: 0L
        }
        UserFileSort.DATE_ASC -> files.sortedBy {
            it.modifiedAtEpochMs.takeIf { t -> t > 0 } ?: 0L
        }
        UserFileSort.TYPE -> files.sortedWith(
            compareBy<UserFileEntry> { it.mimeType.orEmpty() }
                .thenBy { it.name.lowercase() }
        )
        UserFileSort.SIZE -> files.sortedByDescending { it.sizeBytes ?: -1L }
    }
}

/** 推断简单 mime。 */
fun guessMimeFromName(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".json") -> "application/json"
        lower.endsWith(".md") || lower.endsWith(".markdown") -> "text/markdown"
        lower.endsWith(".txt") || lower.endsWith(".log") -> "text/plain"
        lower.endsWith(".csv") -> "text/csv"
        lower.endsWith(".html") || lower.endsWith(".htm") -> "text/html"
        lower.endsWith(".xml") -> "text/xml"
        lower.endsWith(".pdf") -> "application/pdf"
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
        lower.endsWith(".webp") -> "image/webp"
        lower.endsWith(".zip") -> "application/zip"
        else -> "application/octet-stream"
    }
}
