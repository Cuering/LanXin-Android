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

package com.lanxin.android.builtin.pet.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * SAF 公共 `LanXin/` 树：用户 OpenDocumentTree 授权后可写，不申请
 * MANAGE_EXTERNAL_STORAGE。
 *
 * 引擎仍优先消费 [java.io.File] 路径（App externalFiles 或公共 File 直写）；
 * SAF 树用于：
 * 1. 探测/保持用户可见的公共目录可写授权
 * 2. 将本地 File 镜像到树，文件管理器可见
 * 3. 背景等路径用相对 `LanXin/` 键持久化，跨回退根仍可解析
 *
 * @see DebugAssetStorage
 * @see docs/debug-assets.md
 */
object LanXinSafTree {

    const val PREFS_KEY = "lanxin_saf_tree_uri"

    /** 用户提示：在系统文件选择器中创建/选中 LanXin 文件夹。 */
    const val GRANT_HINT =
        "请选择或新建「LanXin」文件夹并允许访问；App 会自动建 live2d/asr/tts/models 等子目录，" +
            "之后下载与相关资源都落在该树下（文件管理器可见）。"

    data class Probe(
        val granted: Boolean,
        val treeUri: String,
        val writable: Boolean,
        val displayLabel: String
    )

    fun isContentUri(uriString: String?): Boolean =
        !uriString.isNullOrBlank() && uriString.trim().startsWith("content://")

    /**
     * 持久化读+写树权限。失败时尝试只读。
     * @return 是否至少拿到读权限
     */
    fun takePersistable(context: Context, treeUriString: String): Boolean {
        if (!isContentUri(treeUriString)) return false
        val uri = Uri.parse(treeUriString.trim())
        val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return try {
            context.contentResolver.takePersistableUriPermission(uri, rw)
            true
        } catch (_: SecurityException) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                true
            } catch (_: Exception) {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun releasePersistable(context: Context, treeUriString: String) {
        if (!isContentUri(treeUriString)) return
        val uri = Uri.parse(treeUriString.trim())
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.releasePersistableUriPermission(uri, flags)
        } catch (_: Exception) {
            // ignore
        }
    }

    /**
     * 探测树是否仍授权且可创建探测文件。
     */
    fun probe(context: Context, treeUriString: String?): Probe {
        val raw = treeUriString?.trim().orEmpty()
        if (!isContentUri(raw)) {
            return Probe(
                granted = false,
                treeUri = "",
                writable = false,
                displayLabel = "未授权"
            )
        }
        val treeUri = Uri.parse(raw)
        val writable = try {
            ensureWritable(context, treeUri)
        } catch (_: Exception) {
            false
        }
        val label = displayLabel(treeUri)
        return Probe(
            granted = true,
            treeUri = raw,
            writable = writable,
            displayLabel = if (writable) label else "$label（不可写）"
        )
    }

    /**
     * 在树根创建 `.lanxin_write_probe` 再删除，验证可写。
     */
    fun ensureWritable(context: Context, treeUri: Uri): Boolean {
        val resolver = context.contentResolver
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        val name = ".lanxin_write_probe"
        // 先清旧探测文件
        findChild(context, treeUri, docId, name)?.let { childId ->
            runCatching {
                val child = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                DocumentsContract.deleteDocument(resolver, child)
            }
        }
        val created = DocumentsContract.createDocument(
            resolver,
            parent,
            "application/octet-stream",
            name
        ) ?: return false
        return try {
            resolver.openOutputStream(created)?.use { out ->
                out.write("ok".toByteArray())
            } ?: return false
            DocumentsContract.deleteDocument(resolver, created)
            true
        } catch (_: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, created) }
            false
        }
    }

    /**
     * 将本地 [file] 镜像到 SAF 树下 [relativeUnderLanXin]（如 `backgrounds/a.jpg`）。
     * 父目录按需创建。成功返回 document content Uri 字符串。
     */
    fun mirrorFile(
        context: Context,
        treeUriString: String,
        file: File,
        relativeUnderLanXin: String
    ): String? {
        if (!file.isFile || file.length() <= 0L) return null
        if (!isContentUri(treeUriString)) return null
        val rel = normalizeRelative(relativeUnderLanXin) ?: return null
        val treeUri = Uri.parse(treeUriString.trim())
        return try {
            val parentDocId = ensureDirPath(
                context,
                treeUri,
                rel.substringBeforeLast('/', missingDelimiterValue = "")
            ) ?: DocumentsContract.getTreeDocumentId(treeUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
            val displayName = rel.substringAfterLast('/')
            val mime = guessMime(displayName)
            // 覆盖同名
            findChild(context, treeUri, parentDocId, displayName)?.let { existingId ->
                val existing = DocumentsContract.buildDocumentUriUsingTree(treeUri, existingId)
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, existing) }
            }
            val created = DocumentsContract.createDocument(
                context.contentResolver,
                parentUri,
                mime,
                displayName
            ) ?: return null
            context.contentResolver.openOutputStream(created)?.use { out: OutputStream ->
                file.inputStream().use { input: InputStream -> input.copyTo(out) }
            } ?: return null
            created.toString()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 在 SAF 树根下创建标准子目录骨架（live2d / asr / tts / models…）。
     * 用户授权后即可在文件管理器看到统一 `LanXin/` 布局；幂等。
     * @return 成功确保的子目录个数
     */
    fun ensureStructure(context: Context, treeUriString: String?): Int {
        if (!isContentUri(treeUriString)) return 0
        val treeUri = Uri.parse(treeUriString!!.trim())
        var count = 0
        for (rel in DebugOpenSourcePaths.STANDARD_SUBDIRS) {
            try {
                val id = ensureDirPath(context, treeUri, rel)
                if (id != null) count++
            } catch (_: Exception) {
                // 单目录失败不阻断
            }
        }
        return count
    }

    /**
     * 递归镜像目录到 SAF 树下 [relativeUnderLanXin]。
     * @return 成功拷贝的文件数；失败/跳过返回 0
     */
    fun mirrorDirectory(
        context: Context,
        treeUriString: String,
        dir: File,
        relativeUnderLanXin: String
    ): Int {
        if (!dir.isDirectory) return 0
        if (!isContentUri(treeUriString)) return 0
        val baseRel = normalizeRelative(relativeUnderLanXin)
            ?: normalizeRelative(dir.name)
            ?: return 0
        var count = 0
        dir.walkTopDown().forEach { child ->
            if (!child.isFile || child.length() <= 0L) return@forEach
            if (child.name.endsWith(".part")) return@forEach
            val childRel = try {
                val rootPath = dir.canonicalFile.path.trimEnd('/') + "/"
                val filePath = child.canonicalFile.path
                if (!filePath.startsWith(rootPath)) return@forEach
                val sub = filePath.removePrefix(rootPath).trimStart('/')
                if (sub.isBlank()) return@forEach
                "$baseRel/$sub"
            } catch (_: Exception) {
                return@forEach
            }
            if (mirrorFile(context, treeUriString, child, childRel) != null) {
                count++
            }
        }
        return count
    }

    /**
     * 在 SAF 树下创建相对路径文件并写入 [bytes]。
     * 用于「主写 SAF」：授权成功后下载可直接落到公共树。
     * @return 创建的 document Uri，失败 null
     */
    fun writeBytes(
        context: Context,
        treeUriString: String,
        relativeUnderLanXin: String,
        bytes: ByteArray,
        mimeType: String? = null
    ): String? {
        if (bytes.isEmpty()) return null
        if (!isContentUri(treeUriString)) return null
        val rel = normalizeRelative(relativeUnderLanXin) ?: return null
        val treeUri = Uri.parse(treeUriString.trim())
        return try {
            val parentDocId = ensureDirPath(
                context,
                treeUri,
                rel.substringBeforeLast('/', missingDelimiterValue = "")
            ) ?: DocumentsContract.getTreeDocumentId(treeUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
            val displayName = rel.substringAfterLast('/')
            val mime = mimeType ?: guessMime(displayName)
            findChild(context, treeUri, parentDocId, displayName)?.let { existingId ->
                val existing = DocumentsContract.buildDocumentUriUsingTree(treeUri, existingId)
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, existing) }
            }
            val created = DocumentsContract.createDocument(
                context.contentResolver,
                parentUri,
                mime,
                displayName
            ) ?: return null
            context.contentResolver.openOutputStream(created)?.use { out ->
                out.write(bytes)
            } ?: return null
            created.toString()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 纯 JVM 路径契约：从本地绝对路径 + lanXinDir 算出 SAF 镜像相对键。
     * 单测 / 无 Context 场景复用。
     */
    fun mirrorRelativeKey(absolutePath: String, lanXinDir: File): String? =
        relativeUnderLanXin(absolutePath, lanXinDir)

    /**
     * 把绝对路径收敛为相对 `LanXin/` 的键；不在 LanXin 下则返回 null。
     *
     * 例：`/…/LanXin/backgrounds/a.jpg` → `backgrounds/a.jpg`
     */
    fun relativeUnderLanXin(absolutePath: String, lanXinDir: File): String? {
        val abs = absolutePath.trim()
        if (abs.isBlank()) return null
        val root = try {
            lanXinDir.canonicalFile
        } catch (_: Exception) {
            lanXinDir.absoluteFile
        }
        val file = try {
            File(abs).canonicalFile
        } catch (_: Exception) {
            File(abs).absoluteFile
        }
        val rootPath = root.path.trimEnd('/') + "/"
        val filePath = file.path
        if (!filePath.startsWith(rootPath) && filePath != root.path) return null
        val rel = filePath.removePrefix(rootPath).trimStart('/')
        return rel.ifBlank { null }
    }

    /**
     * 解析背景等资源路径：
     * - 绝对且存在 → 原样
     * - 相对（`backgrounds/x.jpg` 或 `LanXin/backgrounds/x.jpg`）→ 拼到 [lanXinDir]
     */
    fun resolveUnderLanXin(stored: String, lanXinDir: File): File? {
        val s = stored.trim()
        if (s.isBlank()) return null
        val asAbs = File(s)
        if (asAbs.isAbsolute && asAbs.isFile) return asAbs
        val rel = when {
            s.startsWith("${DebugOpenSourcePaths.ROOT_DIR}/") ->
                s.removePrefix("${DebugOpenSourcePaths.ROOT_DIR}/")
            s.startsWith("/") -> {
                // 绝对但不存在：仍尝试相对名
                asAbs.name
            }
            else -> s.trimStart('/')
        }
        val candidate = File(lanXinDir, rel)
        return if (candidate.isFile) candidate else null
    }

    fun displayLabel(treeUri: Uri): String {
        return try {
            val id = DocumentsContract.getTreeDocumentId(treeUri)
            // primary:LanXin 或 primary:Documents/LanXin
            val after = id.substringAfter(':', missingDelimiterValue = id)
            after.ifBlank { "SAF 目录" }
        } catch (_: Exception) {
            "SAF 目录"
        }
    }

    // ── internals ──────────────────────────────────────────────

    private fun normalizeRelative(rel: String): String? {
        val cleaned = rel.trim().replace('\\', '/').trim('/')
        if (cleaned.isBlank() || cleaned.contains("..")) return null
        return cleaned.removePrefix("${DebugOpenSourcePaths.ROOT_DIR}/")
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "json" -> "application/json"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    /**
     * 确保树下相对目录存在，返回最终目录 documentId。
     * [dirRel] 为空时返回树根 documentId。
     */
    private fun ensureDirPath(context: Context, treeUri: Uri, dirRel: String): String? {
        var currentId = DocumentsContract.getTreeDocumentId(treeUri)
        if (dirRel.isBlank()) return currentId
        val parts = dirRel.split('/').filter { it.isNotBlank() && it != ".." }
        for (part in parts) {
            val existing = findChild(context, treeUri, currentId, part)
            if (existing != null) {
                currentId = existing
                continue
            }
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, currentId)
            val created = DocumentsContract.createDocument(
                context.contentResolver,
                parentUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                part
            ) ?: return null
            currentId = DocumentsContract.getDocumentId(created)
        }
        return currentId
    }

    private fun findChild(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        displayName: String
    ): String? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        context.contentResolver.query(children, projection, null, null, null)?.use { c ->
            val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            if (idIdx < 0 || nameIdx < 0) return null
            while (c.moveToNext()) {
                if (displayName == c.getString(nameIdx)) {
                    return c.getString(idIdx)
                }
            }
        }
        return null
    }
}
