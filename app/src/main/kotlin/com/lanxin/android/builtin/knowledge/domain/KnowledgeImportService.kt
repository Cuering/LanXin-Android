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

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * 知识库文档导入编排：解析 → 分段 → 向量化入库。
 */
@Singleton
class KnowledgeImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentParser: DocumentParser,
    private val textChunker: TextChunker,
    private val markdownChunker: MarkdownChunker,
    private val pipeline: VectorPipeline,
    private val vectorStore: VectorStore
) {

    /**
     * 从 SAF 目录树批量导入支持的文档（递归扫描子目录，深度有上限）。
     * 仅处理扩展名为 .txt / .md / .pdf 的文件；跳过其它类型。
     */
    fun importFolder(treeUri: Uri): Flow<ImportProgress> = flow {
        val startedAt = System.currentTimeMillis()
        emit(
            ImportProgress(
                phase = ImportPhase.READING,
                message = "扫描文件夹…",
                batchMode = true
            )
        )

        val files = try {
            listSupportedDocuments(treeUri)
        } catch (e: Exception) {
            Log.e(TAG, "list folder failed", e)
            emit(
                ImportProgress(
                    phase = ImportPhase.FAILED,
                    message = e.message ?: "无法读取文件夹",
                    error = e.message,
                    batchMode = true,
                    elapsedMs = System.currentTimeMillis() - startedAt
                )
            )
            return@flow
        }

        if (files.isEmpty()) {
            emit(
                ImportProgress(
                    phase = ImportPhase.FAILED,
                    message = "文件夹中没有可导入的 .txt / .md / .pdf",
                    error = "empty_folder",
                    batchMode = true,
                    elapsedMs = System.currentTimeMillis() - startedAt
                )
            )
            return@flow
        }

        var filesOk = 0
        var filesFail = 0
        var totalSuccessChunks = 0
        var totalFailedChunks = 0
        var totalChars = 0
        val names = mutableListOf<String>()

        for ((index, entry) in files.withIndex()) {
            emit(
                ImportProgress(
                    phase = ImportPhase.READING,
                    fileName = entry.displayName,
                    message = "导入 ${index + 1}/${files.size}：${entry.displayName}",
                    batchMode = true,
                    batchTotal = files.size,
                    batchDone = index,
                    batchSuccess = filesOk,
                    batchFailed = filesFail,
                    successCount = totalSuccessChunks,
                    failedCount = totalFailedChunks,
                    charCount = totalChars
                )
            )

            var last: ImportProgress? = null
            try {
                importDocument(entry.uri).collect { p -> last = p }
            } catch (e: Exception) {
                Log.e(TAG, "folder item failed: ${entry.displayName}", e)
                filesFail++
                continue
            }

            val p = last
            if (p == null) {
                filesFail++
                continue
            }
            when (p.phase) {
                ImportPhase.DONE -> {
                    filesOk++
                    totalSuccessChunks += p.successCount
                    totalFailedChunks += p.failedCount
                    totalChars += p.charCount
                    names += entry.displayName
                }
                ImportPhase.FAILED -> {
                    filesFail++
                }
                else -> {
                    // 非终态视为失败，避免卡住
                    filesFail++
                }
            }

            emit(
                ImportProgress(
                    phase = ImportPhase.EMBEDDING,
                    fileName = entry.displayName,
                    message = "已处理 ${index + 1}/${files.size}",
                    batchMode = true,
                    batchTotal = files.size,
                    batchDone = index + 1,
                    batchSuccess = filesOk,
                    batchFailed = filesFail,
                    successCount = totalSuccessChunks,
                    failedCount = totalFailedChunks,
                    charCount = totalChars
                )
            )
        }

        val elapsed = System.currentTimeMillis() - startedAt
        val storeCount = runCatching { vectorStore.count() }.getOrDefault(0L)
        if (filesOk == 0) {
            emit(
                ImportProgress(
                    phase = ImportPhase.FAILED,
                    message = "文件夹导入失败：0/${files.size} 个文件成功" +
                        if (filesFail > 0) "（失败 $filesFail）" else "",
                    error = "all_failed",
                    batchMode = true,
                    batchTotal = files.size,
                    batchDone = files.size,
                    batchSuccess = 0,
                    batchFailed = filesFail,
                    elapsedMs = elapsed,
                    storeCount = storeCount
                )
            )
        } else {
            val sample = names.take(3).joinToString("、")
            val more = if (names.size > 3) " 等" else ""
            emit(
                ImportProgress(
                    phase = ImportPhase.DONE,
                    fileName = sample + more,
                    message = buildString {
                        append("文件夹导入完成：成功 $filesOk 个文件")
                        if (filesFail > 0) append("，失败 $filesFail")
                        append("，共 $totalSuccessChunks 段")
                    },
                    charCount = totalChars,
                    successCount = totalSuccessChunks,
                    failedCount = totalFailedChunks,
                    batchMode = true,
                    batchTotal = files.size,
                    batchDone = files.size,
                    batchSuccess = filesOk,
                    batchFailed = filesFail,
                    elapsedMs = elapsed,
                    storeCount = storeCount
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 从 SAF Uri 导入文档，通过 Flow 推送进度。
     */
    fun importDocument(uri: Uri): Flow<ImportProgress> = flow {
        val startedAt = System.currentTimeMillis()
        emit(ImportProgress(phase = ImportPhase.READING, message = "读取文件…"))

        val meta = resolveMeta(uri)
        val fileName = meta.displayName
        val mime = meta.mimeType

        if (!documentParser.supports(fileName, mime)) {
            emit(
                ImportProgress(
                    phase = ImportPhase.FAILED,
                    fileName = fileName,
                    message = "不支持的格式，请选择 .txt / .md / .pdf",
                    error = "unsupported"
                )
            )
            return@flow
        }

        emit(
            ImportProgress(
                phase = ImportPhase.PARSING,
                fileName = fileName,
                message = "解析 $fileName…"
            )
        )

        val parsed = try {
            openInputStream(uri).use { input ->
                documentParser.parse(fileName, input, mime)
            }
        } catch (e: Exception) {
            Log.e(TAG, "parse failed", e)
            emit(
                ImportProgress(
                    phase = ImportPhase.FAILED,
                    fileName = fileName,
                    message = e.message ?: "解析失败",
                    error = e.message,
                    elapsedMs = System.currentTimeMillis() - startedAt
                )
            )
            return@flow
        }

        if (parsed.text.isBlank()) {
            emit(
                ImportProgress(
                    phase = ImportPhase.FAILED,
                    fileName = fileName,
                    message = "文档无有效文本",
                    error = "empty",
                    elapsedMs = System.currentTimeMillis() - startedAt
                )
            )
            return@flow
        }

        emit(
            ImportProgress(
                phase = ImportPhase.CHUNKING,
                fileName = fileName,
                message = "分段中…",
                charCount = parsed.charCount
            )
        )

        // .md / .markdown → 结构感知分段；其余 → 滑动窗口
        val chunkTexts = chunkDocument(fileName, parsed.text)
        if (chunkTexts.isEmpty()) {
            emit(
                ImportProgress(
                    phase = ImportPhase.FAILED,
                    fileName = fileName,
                    message = "分段结果为空",
                    error = "no_chunks",
                    charCount = parsed.charCount,
                    elapsedMs = System.currentTimeMillis() - startedAt
                )
            )
            return@flow
        }

        // 确保模型就绪
        runCatching { pipeline.warmUp() }

        // externalId 基座：文件内容 hash 的正 long，chunk 用 base + index
        // source 形如 knowledge:文件名，便于按文件统计/覆盖；号段与 VectorSource.KNOWLEDGE 区分
        val baseId = contentHashBase(parsed.text, fileName)
        val sourceLabel = "${VectorSource.KNOWLEDGE}:$fileName"

        emit(
            ImportProgress(
                phase = ImportPhase.EMBEDDING,
                fileName = fileName,
                message = "向量化并入库…",
                charCount = parsed.charCount,
                totalChunks = chunkTexts.size,
                doneChunks = 0
            )
        )

        var success = 0
        var failed = 0
        for ((idx, chunkText) in chunkTexts.withIndex()) {
            val externalId = baseId + idx
            // source 用文件名便于过滤；同一文件重复导入会按 externalId+source upsert 覆盖
            val id = runCatching {
                pipeline.index(
                    externalId = externalId,
                    source = sourceLabel,
                    text = chunkText
                )
            }.getOrDefault(-1L)

            if (id > 0) success++ else failed++

            // 每 N 个或最后一个更新进度
            if ((idx + 1) % PROGRESS_EVERY == 0 || idx + 1 == chunkTexts.size) {
                emit(
                    ImportProgress(
                        phase = ImportPhase.EMBEDDING,
                        fileName = fileName,
                        message = "向量化 ${idx + 1}/${chunkTexts.size}",
                        charCount = parsed.charCount,
                        totalChunks = chunkTexts.size,
                        doneChunks = idx + 1,
                        successCount = success,
                        failedCount = failed
                    )
                )
            }
        }

        val elapsed = System.currentTimeMillis() - startedAt
        val totalInStore = runCatching { vectorStore.count(sourceLabel) }.getOrDefault(0L)

        emit(
            ImportProgress(
                phase = ImportPhase.DONE,
                fileName = fileName,
                message = "导入完成：成功 $success 段" +
                    if (failed > 0) "，失败 $failed" else "",
                charCount = parsed.charCount,
                totalChunks = chunkTexts.size,
                doneChunks = chunkTexts.size,
                successCount = success,
                failedCount = failed,
                elapsedMs = elapsed,
                storeCount = totalInStore
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * 按扩展名选择分段策略。
     * Markdown 走结构感知；其它走滑动窗口。
     */
    private fun chunkDocument(fileName: String, text: String): List<String> {
        val ext = DocumentTypes.extensionOf(fileName)
        return if (ext == "md" || ext == "markdown" || ext == "mdown") {
            markdownChunker.chunk(text).map { it.text }
        } else {
            textChunker.chunk(text).map { it.text }
        }
    }

    suspend fun knowledgeCount(): Long = withContext(Dispatchers.IO) {
        // 统计所有 knowledge:* 与 source=knowledge
        val all = vectorStore.count()
        // 粗略：无法按前缀 count，返回全库；UI 另显 knowledge 相关
        all
    }

    suspend fun clearKnowledge(): Unit = withContext(Dispatchers.IO) {
        // 仅清 source 以 knowledge 开头的——VectorStore 无前缀删除，P2 先 clear 全库风险大
        // 提供按 source 精确清理由 UI 传入；此处暴露 clearAll 给高级操作
        vectorStore.clear()
    }

    /**
     * 递归列举目录树中支持的文档 Uri（深度有上限，避免极端深树）。
     */
    private fun listSupportedDocuments(treeUri: Uri, maxDepth: Int = 6): List<TreeDoc> {
        val out = ArrayList<TreeDoc>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        walkTree(treeUri, rootId, depth = 0, maxDepth = maxDepth, out = out)
        out.sortBy { it.displayName.lowercase() }
        return out
    }

    private fun walkTree(
        treeUri: Uri,
        documentId: String,
        depth: Int,
        maxDepth: Int,
        out: MutableList<TreeDoc>
    ) {
        if (depth > maxDepth) return
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
                val name = cursor.getString(nameIdx).orEmpty().ifBlank { "item" }
                val mime = cursor.getString(mimeIdx).orEmpty()
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walkTree(treeUri, childId, depth + 1, maxDepth, out)
                } else {
                    val ext = DocumentTypes.extensionOf(name)
                    if (ext in DocumentTypes.EXTENSIONS || documentParser.supports(name, mime)) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        out += TreeDoc(uri = docUri, displayName = name, mimeType = mime)
                    }
                }
            }
        }
    }

    private fun openInputStream(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw DocumentParseException("无法打开文件：$uri")

    private fun resolveMeta(uri: Uri): FileMeta {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "document"
        var mime = context.contentResolver.getType(uri)
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIdx >= 0) {
                    val n = cursor.getString(nameIdx)
                    if (!n.isNullOrBlank()) name = n
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "resolve display name failed", e)
        }
        // 若 mime 为空，从扩展名猜
        if (mime.isNullOrBlank()) {
            mime = when (DocumentTypes.extensionOf(name)) {
                "pdf" -> "application/pdf"
                "md", "markdown" -> "text/markdown"
                "txt" -> "text/plain"
                else -> "application/octet-stream"
            }
        }
        return FileMeta(name, mime)
    }

    /**
     * 生成稳定的正 long 基座，避免与 memory 冲突。
     * 取 sha256 前 6 字节 → long，再映射到 knowledge 号段。
     */
    private fun contentHashBase(text: String, fileName: String): Long {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(fileName.toByteArray())
        md.update(0)
        // 大文件只 hash 头尾 + 长度，避免 OOM/过慢
        val bytes = text.toByteArray(Charsets.UTF_8)
        md.update(bytes.size.toString().toByteArray())
        val head = bytes.copyOfRange(0, minOf(bytes.size, 4096))
        md.update(head)
        if (bytes.size > 8192) {
            md.update(bytes, bytes.size - 4096, 4096)
        }
        val dig = md.digest()
        var v = 0L
        for (i in 0 until 6) {
            v = (v shl 8) or (dig[i].toLong() and 0xFF)
        }
        // knowledge 号段：高位置 1，避免与 memory 时间戳冲突
        val base = (v and 0x0000_0FFF_FFFF_FFFFL) or 0x1000_0000_0000_0000L
        return base.absoluteValue.coerceAtLeast(1L)
    }

    private data class FileMeta(val displayName: String, val mimeType: String?)

    private data class TreeDoc(
        val uri: Uri,
        val displayName: String,
        val mimeType: String?
    )

    companion object {
        private const val TAG = "KnowledgeImport"
        private const val PROGRESS_EVERY = 3
    }
}

enum class ImportPhase {
    IDLE,
    READING,
    PARSING,
    CHUNKING,
    EMBEDDING,
    DONE,
    FAILED
}

data class ImportProgress(
    val phase: ImportPhase = ImportPhase.IDLE,
    val fileName: String = "",
    val message: String = "",
    val charCount: Int = 0,
    val totalChunks: Int = 0,
    val doneChunks: Int = 0,
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val elapsedMs: Long = 0,
    val storeCount: Long = 0,
    val error: String? = null,
    /** 文件夹批量导入时为 true。 */
    val batchMode: Boolean = false,
    val batchTotal: Int = 0,
    val batchDone: Int = 0,
    val batchSuccess: Int = 0,
    val batchFailed: Int = 0
) {
    val fraction: Float
        get() = when {
            batchMode && batchTotal > 0 && phase != ImportPhase.FAILED && phase != ImportPhase.IDLE -> {
                (batchDone.toFloat() / batchTotal).coerceIn(0f, 1f)
            }
            else -> when (phase) {
                ImportPhase.IDLE -> 0f
                ImportPhase.READING -> 0.05f
                ImportPhase.PARSING -> 0.15f
                ImportPhase.CHUNKING -> 0.25f
                ImportPhase.EMBEDDING -> {
                    if (totalChunks <= 0) 0.3f
                    else 0.3f + 0.65f * (doneChunks.toFloat() / totalChunks)
                }
                ImportPhase.DONE -> 1f
                ImportPhase.FAILED -> 0f
            }
        }

    val isTerminal: Boolean
        get() = phase == ImportPhase.DONE || phase == ImportPhase.FAILED

    val isRunning: Boolean
        get() = phase != ImportPhase.IDLE && !isTerminal
}
