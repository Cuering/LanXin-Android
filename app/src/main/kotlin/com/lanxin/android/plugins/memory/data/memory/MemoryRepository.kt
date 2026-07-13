package com.lanxin.android.plugins.memory.data.memory

import android.content.Context
import android.net.Uri
import com.lanxin.android.plugins.memory.domain.memory.ImportStrategy
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class MemoryRepository @Inject constructor(
    private val dao: MemoryDao
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun getAllMemories(): Flow<List<MemoryEntity>> = dao.getAllMemories()

    fun getMemoriesByType(type: String): Flow<List<MemoryEntity>> = dao.getMemoriesByType(type)

    suspend fun searchMemories(keyword: String): List<MemoryEntity> {
        if (keyword.isBlank()) return emptyList()
        return dao.searchMemories(keyword.trim())
    }

    suspend fun getMemoryById(id: Long): MemoryEntity? = dao.getMemoryById(id)

    suspend fun addMemory(
        content: String,
        type: String = MemoryType.CHAT,
        importance: Float = 5.0f,
        lifecycle: String = "permanent",
        metadata: String? = null
    ): Long {
        val memory = MemoryEntity(
            content = content.trim(),
            type = type,
            importance = importance.coerceIn(1f, 10f),
            status = "active",
            lifecycle = lifecycle,
            createdAt = System.currentTimeMillis(),
            metadata = metadata
        )
        return dao.insertMemory(memory)
    }

    suspend fun updateMemory(memory: MemoryEntity) {
        dao.updateMemory(memory)
    }

    suspend fun deleteMemory(id: Long) {
        dao.deleteMemoryById(id)
    }

    suspend fun getStats(): Map<String, Int> {
        val total = dao.getTotalCountOnce()
        val active = dao.getActiveCountOnce()
        val typeCounts = dao.getTypeCounts().associate { it.type to it.cnt }
        return buildMap {
            put("total", total)
            put("active", active)
            MemoryType.ALL.forEach { type ->
                put(type, typeCounts[type] ?: 0)
            }
        }
    }

    /**
     * 聊天注入专用检索（关键词 LIKE，Phase 2.1 再换向量检索）。
     */
    suspend fun searchForInject(keyword: String, limit: Int = 5): List<MemoryEntity> {
        if (keyword.isBlank()) return emptyList()
        val results = dao.searchMemoriesForInject(keyword.trim(), limit)
        val now = System.currentTimeMillis()
        results.forEach { dao.touchMemory(it.id, now) }
        return results
    }

    /**
     * 将全部记忆导出为 cache 目录下的 JSON 文件，供分享/保存。
     * 任务签名为 `fun exportToJson`，内部通过 runBlocking 读取 Room。
     * 推荐优先使用 [exportToJsonSuspend]。
     */
    fun exportToJson(context: Context): File {
        return kotlinx.coroutines.runBlocking {
            exportToJsonSuspend(context)
        }
    }

    suspend fun exportToJsonSuspend(context: Context): File = withContext(Dispatchers.IO) {
        val memories = dao.getAllMemoriesOnce()
        val payload = MemoryExportPayload(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            memories = memories.map { it.toExportItem() }
        )
        val jsonText = json.encodeToString(MemoryExportPayload.serializer(), payload)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(context.cacheDir, "lanxin_memories_$stamp.json")
        file.writeText(jsonText, Charsets.UTF_8)
        file
    }

    /**
     * 从 SAF 选择的 Uri 导入记忆。
     */
    suspend fun importFromJson(
        context: Context,
        uri: Uri,
        strategy: ImportStrategy
    ): MemoryImportResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readText()
        } ?: throw IllegalArgumentException("无法读取所选文件")
        importFromJsonText(text, strategy)
    }

    /**
     * 从本地文件导入记忆。
     */
    suspend fun importFromJsonFile(
        context: Context,
        file: File,
        strategy: ImportStrategy
    ): MemoryImportResult = withContext(Dispatchers.IO) {
        if (!file.exists()) throw IllegalArgumentException("文件不存在: ${file.absolutePath}")
        importFromJsonText(file.readText(Charsets.UTF_8), strategy)
    }

    private suspend fun importFromJsonText(
        text: String,
        strategy: ImportStrategy
    ): MemoryImportResult {
        val payload = try {
            json.decodeFromString(MemoryExportPayload.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("JSON 格式无效: ${e.message}", e)
        }

        val items = payload.memories
        if (items.isEmpty()) {
            return MemoryImportResult(imported = 0, skipped = 0, total = 0)
        }

        var imported = 0
        var skipped = 0

        when (strategy) {
            ImportStrategy.REPLACE -> {
                dao.deleteAll()
                items.forEach { item ->
                    dao.insertMemory(item.toEntity(preserveId = false))
                    imported++
                }
            }

            ImportStrategy.MERGE_BY_ID -> {
                val existingIds = dao.getAllIds().toHashSet()
                items.forEach { item ->
                    if (item.id > 0 && item.id in existingIds) {
                        skipped++
                    } else {
                        val preserveId = item.id > 0 && item.id !in existingIds
                        dao.insertMemory(item.toEntity(preserveId = preserveId))
                        imported++
                    }
                }
            }

            ImportStrategy.MERGE_DEDUP -> {
                items.forEach { item ->
                    val content = item.content.trim()
                    if (content.isEmpty()) {
                        skipped++
                        return@forEach
                    }
                    if (dao.existsByContentAndType(content, item.type)) {
                        skipped++
                    } else {
                        dao.insertMemory(item.toEntity(preserveId = false))
                        imported++
                    }
                }
            }
        }

        return MemoryImportResult(
            imported = imported,
            skipped = skipped,
            total = items.size
        )
    }

    private fun MemoryEntity.toExportItem(): MemoryExportItem = MemoryExportItem(
        id = id,
        content = content,
        type = type,
        importance = importance,
        createdAt = createdAt,
        updatedAt = lastAccessedAt ?: createdAt,
        status = status,
        lifecycle = lifecycle,
        metadata = metadata
    )

    private fun MemoryExportItem.toEntity(preserveId: Boolean): MemoryEntity = MemoryEntity(
        id = if (preserveId && id > 0) id else 0,
        content = content.trim(),
        type = type.ifBlank { MemoryType.CHAT },
        importance = importance.coerceIn(1f, 10f),
        status = status.ifBlank { "active" },
        lifecycle = lifecycle.ifBlank { "permanent" },
        createdAt = if (createdAt > 0) createdAt else System.currentTimeMillis(),
        lastAccessedAt = updatedAt.takeIf { it > 0 },
        metadata = metadata
    )
}
