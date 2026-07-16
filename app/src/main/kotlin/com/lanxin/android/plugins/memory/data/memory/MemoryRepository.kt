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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

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

    /** 同步拉取全部记忆（BM25 索引构建用）。 */
    suspend fun getAllMemoriesOnce(): List<MemoryEntity> = withContext(Dispatchers.IO) {
        dao.getAllMemoriesOnce()
    }

    /** 按 type 同步拉取（P3 自动知识列表）。 */
    suspend fun getByType(type: String): List<MemoryEntity> = withContext(Dispatchers.IO) {
        dao.getMemoriesByType(type).first()
    }

    /** 删除指定 type 的全部记忆，返回删除条数。 */
    suspend fun clearByType(type: String): Int = withContext(Dispatchers.IO) {
        val items = dao.getMemoriesByType(type).first()
        items.forEach { dao.deleteMemoryById(it.id) }
        items.size
    }

    /** P3：自动知识条目。metadata 含 source=auto_knowledge，type 为分类。 */
    suspend fun getAutoKnowledge(): List<MemoryEntity> = withContext(Dispatchers.IO) {
        dao.getAllMemoriesOnce().filter { entity ->
            entity.metadata?.contains("auto_knowledge") == true
        }
    }

    suspend fun clearAutoKnowledge(): Int = withContext(Dispatchers.IO) {
        val items = getAutoKnowledge()
        items.forEach { dao.deleteMemoryById(it.id) }
        items.size
    }

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

    /** 聊天注入专用检索（关键词 LIKE）。 */
    suspend fun searchForInject(keyword: String, limit: Int = 5): List<MemoryEntity> {
        if (keyword.isBlank()) return emptyList()
        val results = dao.searchMemoriesForInject(keyword.trim(), limit)
        val now = System.currentTimeMillis()
        results.forEach { dao.touchMemory(it.id, now) }
        return results
    }

    // === Phase 5.7 新增方法 ===

    /** 获取所有 judgment 类型记忆 */
    suspend fun getJudgmentMemories(): List<MemoryEntity> = withContext(Dispatchers.IO) {
        dao.getJudgmentMemories()
    }

    /** 添加 judgment 类型记忆 */
    suspend fun addJudgmentMemory(
        content: String,
        name: String,
        appliesWhen: List<String>? = null,
        doesNotApplyWhen: List<String>? = null,
        rules: String? = null
    ): Long {
        val metadata = JSONObject().apply {
            put("name", name)
            put("applies_when", JSONArray(appliesWhen ?: emptyList<String>()))
            put("does_not_apply_when", JSONArray(doesNotApplyWhen ?: emptyList<String>()))
            if (!rules.isNullOrEmpty()) put("rules", rules)
        }.toString()
        return addMemory(content, MemoryType.JUDGMENT, 8.0f, "permanent", metadata)
    }

    /** 获取即将过期的记忆（按 lastAccessedAt 判断） */
    suspend fun getExpiredMemories(cutoffMillis: Long): List<MemoryEntity> = withContext(Dispatchers.IO) {
        dao.getExpiredMemories(cutoffMillis)
    }

    /** 清理过期记忆 */
    suspend fun cleanupExpiredMemories(): Int = withContext(Dispatchers.IO) {
        val expired = dao.getExpiredMemories(System.currentTimeMillis() - 90 * 86400_000L)
        expired.forEach { dao.markExpired(it.id) }
        dao.deleteExpiredMemories()
        expired.size
    }

    /** 自适应衰减：根据访问频率 + 重要性 + 不活跃天数计算半衰期 */
    suspend fun applyAdaptiveDecay(halfLifeDays: Int = 30): Int {
        val all = getAllMemoriesOnce()
        var decayed = 0
        for (mem in all) {
            if (mem.lifecycle == "permanent") continue
            val daysInactive = if (mem.lastAccessedAt != null) {
                (System.currentTimeMillis() - mem.lastAccessedAt) / 86400_000L
            } else {
                (System.currentTimeMillis() - mem.createdAt) / 86400_000L
            }
            val halfLife = calculateAdaptiveHalfLife(mem.importance, daysInactive)
            if (daysInactive > halfLife * 2) {
                dao.markExpired(mem.id)
                decayed++
            }
        }
        if (decayed > 0) dao.deleteExpiredMemories()
        return decayed
    }

    /** 计算自适应半衰期（基于重要性和不活跃天数） */
    private fun calculateAdaptiveHalfLife(importance: Float, daysInactive: Long): Double {
        val base = 30.0
        val importanceFactor = importance / 10.0 // 0.1 ~ 1.0
        val inactiveFactor = 1.0 / (1.0 + daysInactive / 30.0)
        return base * importanceFactor * inactiveFactor
    }

    /** 用户画像管理 */
    suspend fun getUserProfileSummary(): String? = withContext(Dispatchers.IO) {
        val profile = dao.getUserProfile()
        profile?.summary?.takeIf { it.isNotBlank() }
    }

    suspend fun upsertUserProfile(summary: String) {
        dao.upsertUserProfile(UserEntity(summary = summary, updatedAt = System.currentTimeMillis()))
    }

    /** 进化索引管理 */
    suspend fun getEvolutionEntries(limit: Int = 5): List<EvolutionEntry> = withContext(Dispatchers.IO) {
        dao.getEvolutionEntries(limit)
    }

    suspend fun addEvolutionEntry(date: String, content: String) {
        dao.insertEvolutionEntry(EvolutionEntry(date = date, content = content))
    }

    /** 任务续接管理 */
    suspend fun getPendingTaskResume(): TaskResumeEntity? = withContext(Dispatchers.IO) {
        dao.getPendingTaskResume()
    }

    suspend fun markTaskResumeResolved(id: Long) {
        dao.markTaskResumeResolved(id)
    }

    suspend fun saveTaskResume(description: String, sessionId: String) {
        dao.insertTaskResume(TaskResumeEntity(
            description = description,
            sessionId = sessionId,
            status = "pending"
        ))
    }

    /** 对话归档管理 */
    suspend fun getUnarchivedDialogs(limit: Int = 100): List<DialogEntity> = withContext(Dispatchers.IO) {
        dao.getUnarchivedDialogs(limit)
    }

    suspend fun archiveDialogs() {
        val unarchived = dao.getUnarchivedDialogs(1000)
        unarchived.forEach { dao.markDialogArchived(it.id) }
    }

    /** 将全部记忆导出为 cache 目录下的 JSON 文件。 */
    fun exportToJson(context: Context): File {
        return kotlinx.coroutines.runBlocking {
            exportToJsonSuspend(context)
        }
    }

    suspend fun exportToJsonSuspend(context: Context): File =
        exportToJsonSuspend(context, typeFilter = null)

    suspend fun exportToJsonSuspend(
        context: Context,
        typeFilter: String?
    ): File = withContext(Dispatchers.IO) {
        val items = loadExportItems(typeFilter)
        val payload = MemoryExportPayload(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            memories = items
        )
        val jsonText = json.encodeToString(MemoryExportPayload.serializer(), payload)
        writeCacheFile(context, "json", jsonText)
    }

    suspend fun exportToMarkdownSuspend(context: Context): File =
        exportToMarkdownSuspend(context, typeFilter = null)

    suspend fun exportToMarkdownSuspend(
        context: Context,
        typeFilter: String?
    ): File = withContext(Dispatchers.IO) {
        val exportedAt = System.currentTimeMillis()
        val items = loadExportItems(typeFilter = null)
        val markdown = MemoryMarkdownExporter.build(
            memories = items,
            exportedAt = exportedAt,
            typeFilter = typeFilter
        )
        writeCacheFile(context, "md", markdown)
    }

    suspend fun exportSuspend(
        context: Context,
        format: MemoryExportFormat,
        typeFilter: String? = null
    ): File = when (format) {
        MemoryExportFormat.JSON -> exportToJsonSuspend(context, typeFilter)
        MemoryExportFormat.MARKDOWN -> exportToMarkdownSuspend(context, typeFilter)
    }

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

    private suspend fun loadExportItems(typeFilter: String?): List<MemoryExportItem> {
        val all = dao.getAllMemoriesOnce().map { it.toExportItem() }
        if (typeFilter.isNullOrBlank()) return all
        return all.filter { MemoryMarkdownExporter.matchesTypeFilter(it, typeFilter) }
    }

    private fun writeCacheFile(context: Context, extension: String, text: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(context.cacheDir, "lanxin_memories_$stamp.$extension")
        file.writeText(text, Charsets.UTF_8)
        return file
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

    /** 辅助：JSONObject put 若 key 已存在则跳过 */
    private fun JSONObject.putsIfAbsent(key: String, value: Any) {
        if (!has(key)) put(key, value)
    }
}
