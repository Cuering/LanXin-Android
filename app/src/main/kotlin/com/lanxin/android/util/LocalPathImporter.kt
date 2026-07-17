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

package com.lanxin.android.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 将 SAF 选取的 Uri 导入应用私有目录，返回引擎可用的绝对路径。
 *
 * - 文件：OpenDocument → [importFile]
 * - 目录：OpenDocumentTree → [importTree]（整树拷贝）
 * - Live2D 目录：拷贝后解析 `*.model3.json` 路径
 */
@Singleton
class LocalPathImporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class ImportResult(
        val absolutePath: String,
        val displayName: String
    )

    /**
     * 尽力持久化读权限（文件 / 树）。
     */
    fun takePersistable(uriString: String, isTree: Boolean): Boolean {
        if (!uriString.startsWith("content://")) return false
        return try {
            val uri = Uri.parse(uriString)
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (isTree) {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } else {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 导入单个文件到 `user-picked/<kind>/import_<stamp>/<name>`。
     */
    suspend fun importFile(
        uriString: String,
        kind: PathImportHelper.Kind,
        preferredName: String? = null
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            takePersistable(uriString, isTree = false)
            val uri = Uri.parse(uriString)
            val name = preferredName?.let { PathImportHelper.sanitizeFileName(it) }
                ?: queryDisplayName(uri)
                ?: "file_${System.currentTimeMillis()}"
            val destDir = PathImportHelper.newImportDir(context.filesDir, kind)
            destDir.mkdirs()
            val dest = File(destDir, PathImportHelper.sanitizeFileName(name))
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            } ?: error("无法打开文件: $uriString")
            if (!dest.isFile || dest.length() <= 0L) {
                error("导入后文件无效: ${dest.absolutePath}")
            }
            ImportResult(absolutePath = dest.absolutePath, displayName = dest.name)
        }
    }

    /**
     * 导入目录树。
     *
     * @return 目录绝对路径；若 [kind] 为 LIVE2D，则返回找到的 `*.model3.json` 路径
     */
    suspend fun importTree(
        treeUriString: String,
        kind: PathImportHelper.Kind
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            takePersistable(treeUriString, isTree = true)
            val treeUri = Uri.parse(treeUriString)
            val destRoot = PathImportHelper.newImportDir(context.filesDir, kind)
            if (destRoot.exists()) destRoot.deleteRecursively()
            destRoot.mkdirs()
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            copyDocumentNode(treeUri, docId, destRoot)
            if (kind == PathImportHelper.Kind.LIVE2D) {
                val model3 = PathImportHelper.findModel3Json(destRoot)
                    ?: error("所选文件夹中未找到 *.model3.json")
                ImportResult(absolutePath = model3.absolutePath, displayName = model3.name)
            } else {
                val nonEmpty = destRoot.exists() && (
                    destRoot.isFile ||
                        (destRoot.listFiles()?.isNotEmpty() == true)
                    )
                if (!nonEmpty) error("所选文件夹为空或无法读取")
                ImportResult(absolutePath = destRoot.absolutePath, displayName = destRoot.name)
            }
        }
    }

    /**
     * Live2D：优先按文件导入 `*.model3.json`；
     * 若扩展名不像 model3，仍拷贝并返回路径（由 readiness 判定）。
     */
    suspend fun importLive2dModel3(uriString: String): Result<ImportResult> =
        importFile(uriString, PathImportHelper.Kind.LIVE2D)

    /**
     * Live2D：选文件夹，自动定位 model3.json。
     */
    suspend fun importLive2dTree(treeUriString: String): Result<ImportResult> =
        importTree(treeUriString, PathImportHelper.Kind.LIVE2D)

    private fun copyDocumentNode(treeUri: Uri, documentId: String, destDir: File) {
        destDir.mkdirs()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val childId = cursor.getString(idIdx) ?: continue
                val name = PathImportHelper.sanitizeFileName(
                    cursor.getString(nameIdx) ?: "item_${System.currentTimeMillis()}"
                )
                val mime = cursor.getString(mimeIdx).orEmpty()
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    copyDocumentNode(treeUri, childId, File(destDir, name))
                } else {
                    val src = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    val outFile = File(destDir, name)
                    context.contentResolver.openInputStream(src)?.use { input ->
                        outFile.outputStream().use { out -> input.copyTo(out) }
                    }
                }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) c.getString(idx) else null
                    } else {
                        null
                    }
                }
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }
}
