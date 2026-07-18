package com.lanxin.android.plugins.memory

import com.lanxin.android.plugin.PluginContext
import com.lanxin.android.plugin.ToolDef
import com.lanxin.android.plugins.memory.data.memory.MemoryDao
import com.lanxin.android.plugins.memory.data.memory.MemoryEntity
import com.lanxin.android.plugins.memory.data.memory.MemoryExportItem
import com.lanxin.android.plugins.memory.data.memory.MemoryExportPayload
import com.lanxin.android.plugins.memory.data.memory.MemoryRepository
import com.lanxin.android.plugins.memory.data.memory.MemoryType
import com.lanxin.android.plugins.memory.data.memory.TypeCount
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPluginTest {

    @Test
    fun `memory_export defaults to json`() = runBlocking {
        val plugin = memoryPlugin()
        val tools = registerTools(plugin)

        val result = tools.getValue("memory_export").handler(buildJsonObject {})

        assertEquals("json", result["format"]?.jsonPrimitive?.contentOrNull)
        assertEquals("all", result["type_filter"]?.jsonPrimitive?.contentOrNull)
        val payload = result["payload"]?.jsonPrimitive?.contentOrNull.orEmpty()
        assertTrue(payload.contains("\"version\""))
        assertTrue(payload.contains("喜欢草莓"))
    }

    @Test
    fun `memory_export supports markdown with type filter`() = runBlocking {
        val plugin = memoryPlugin()
        val tools = registerTools(plugin)

        val result = tools.getValue("memory_export").handler(
            buildJsonObject {
                put("format", "markdown")
                put("type_filter", "preference")
            }
        )

        assertEquals("markdown", result["format"]?.jsonPrimitive?.contentOrNull)
        assertEquals("preference", result["type_filter"]?.jsonPrimitive?.contentOrNull)
        val payload = result["payload"]?.jsonPrimitive?.contentOrNull.orEmpty()
        assertTrue(payload.contains("# LanXin Memory Export"))
        assertTrue(payload.contains("- filter: type=preference"))
        assertTrue(payload.contains("喜欢草莓"))
        assertTrue(!payload.contains("普通聊天"))
    }

    @Test
    fun `memory_export accepts md alias`() = runBlocking {
        val plugin = memoryPlugin()
        val tools = registerTools(plugin)

        val result = tools.getValue("memory_export").handler(
            buildJsonObject {
                put("format", "md")
            }
        )

        assertEquals("markdown", result["format"]?.jsonPrimitive?.contentOrNull)
        assertTrue(result["payload"]?.jsonPrimitive?.contentOrNull.orEmpty().contains("# LanXin Memory Export"))
    }


    @Test
    fun `memory_export filters by status`() = runBlocking {
        val plugin = memoryPlugin()
        val tools = registerTools(plugin)

        val result = tools.getValue("memory_export").handler(
            buildJsonObject {
                put("format", "json")
                put("status_filter", "archived")
            }
        )

        assertEquals("archived", result["status_filter"]?.jsonPrimitive?.contentOrNull)
        val payload = result["payload"]?.jsonPrimitive?.contentOrNull.orEmpty()
        assertTrue(payload.contains("已归档偏好"))
        assertTrue(!payload.contains("喜欢草莓"))
        assertTrue(!payload.contains("普通聊天"))
        assertTrue(!payload.contains("过期日常"))
    }

    @Test
    fun `memory_export filters by date range`() = runBlocking {
        val plugin = memoryPlugin()
        val tools = registerTools(plugin)

        // epoch ms bounds: exclude expired (1.6e12), keep 1.7e12 cluster
        val result = tools.getValue("memory_export").handler(
            buildJsonObject {
                put("format", "markdown")
                put("created_after", "1650000000000")
                put("created_before", "1800000000000")
            }
        )

        val payload = result["payload"]?.jsonPrimitive?.contentOrNull.orEmpty()
        assertTrue(payload.contains("喜欢草莓"))
        assertTrue(payload.contains("普通聊天"))
        assertTrue(payload.contains("已归档偏好"))
        assertTrue(!payload.contains("过期日常"))
        assertTrue(result["filter"]?.jsonPrimitive?.contentOrNull.orEmpty().contains("after="))
    }

    @Test
    fun `memory_export rejects invalid created_after`() = runBlocking {
        val plugin = memoryPlugin()
        val tools = registerTools(plugin)
        val result = tools.getValue("memory_export").handler(
            buildJsonObject {
                put("created_after", "not-a-day")
            }
        )
        assertTrue(result["error"]?.jsonPrimitive?.contentOrNull.orEmpty().contains("created_after"))
    }

    private fun memoryPlugin(): MemoryPlugin {
        val dao = FakeMemoryDao()
        val repository = MemoryRepository(dao)
        return MemoryPlugin(repository)
    }

    private suspend fun registerTools(plugin: MemoryPlugin): Map<String, ToolDef> {
        val tools = linkedMapOf<String, ToolDef>()
        plugin.onLoad(object : PluginContext {
            override fun registerTool(tool: ToolDef) {
                tools[tool.name] = tool
            }

            override val filesDir: File =
                File(System.getProperty("java.io.tmpdir"), "memory-plugin-test").also { it.mkdirs() }

            override suspend fun sendMessage(message: String) = Unit
        })
        return tools
    }
}

/** Minimal fake DAO that returns sample memories for export tests. */
private class FakeMemoryDao : MemoryDao {

    private val samples = listOf(
        MemoryEntity(
            id = 1,
            content = "喜欢草莓",
            type = MemoryType.PREFERENCE,
            importance = 8f,
            createdAt = 1_700_000_000_000L,
            lastAccessedAt = 1_700_000_000_000L,
            status = "active",
            lifecycle = "permanent",
            metadata = """{"tags":["food"]}"""
        ),
        MemoryEntity(
            id = 2,
            content = "普通聊天",
            type = MemoryType.CHAT,
            importance = 3f,
            createdAt = 1_700_000_100_000L,
            status = "active",
            lifecycle = "normal"
        ),
        MemoryEntity(
            id = 3,
            content = "已归档偏好",
            type = MemoryType.PREFERENCE,
            importance = 5f,
            createdAt = 1_700_000_200_000L,
            status = "archived",
            lifecycle = "permanent"
        ),
        MemoryEntity(
            id = 4,
            content = "过期日常",
            type = MemoryType.DAILY,
            importance = 2f,
            createdAt = 1_600_000_000_000L,
            status = "expired",
            lifecycle = "ephemeral"
        )
    )

    override suspend fun getAllMemoriesOnce(): List<MemoryEntity> = samples

    // ---- stubs (not called by the memory_export tool path) ----

    override fun getAllMemories(): Flow<List<MemoryEntity>> = flowOf(samples)
    override fun getMemoriesByType(type: String): Flow<List<MemoryEntity>> = error("not mocked")
    override suspend fun searchMemories(keyword: String): List<MemoryEntity> = error("not mocked")
    override suspend fun getMemoryById(id: Long): MemoryEntity? = error("not mocked")
    override suspend fun insertMemory(memory: MemoryEntity): Long = error("not mocked")
    override suspend fun updateMemory(memory: MemoryEntity) = error("not mocked")
    override suspend fun deleteMemory(memory: MemoryEntity) = error("not mocked")
    override suspend fun deleteMemoryById(id: Long) = error("not mocked")
    override fun getMemoryCount(): Flow<Int> = error("not mocked")
    override fun getActiveMemoryCount(): Flow<Int> = error("not mocked")
    override suspend fun searchMemoriesForInject(keyword: String, limit: Int): List<MemoryEntity> = error("not mocked")
    override suspend fun touchMemory(id: Long, timestamp: Long) = error("not mocked")
    override suspend fun getTypeCounts(): List<TypeCount> = error("not mocked")
    override suspend fun getActiveCountOnce(): Int = error("not mocked")
    override suspend fun getTotalCountOnce(): Int = error("not mocked")
    override suspend fun getAllIds(): List<Long> = error("not mocked")
    override suspend fun existsByContentAndType(content: String, type: String): Boolean = error("not mocked")
    override suspend fun insertMemoryIgnore(memory: MemoryEntity): Long = error("not mocked")
    override suspend fun insertMemories(memories: List<MemoryEntity>) = error("not mocked")
    override suspend fun deleteAll() = error("not mocked")
    override suspend fun getJudgmentMemories(): List<MemoryEntity> = error("not mocked")
    override suspend fun getExpiredMemories(cutoff: Long): List<MemoryEntity> = error("not mocked")
    override suspend fun touchMemoryById(id: Long, timestamp: Long) = error("not mocked")
    override suspend fun markExpired(id: Long) = error("not mocked")
    override suspend fun deleteExpiredMemories() = error("not mocked")
    override suspend fun getUserProfile(): com.lanxin.android.plugins.memory.data.memory.UserEntity? = error("not mocked")
    override suspend fun upsertUserProfile(entity: com.lanxin.android.plugins.memory.data.memory.UserEntity) = error("not mocked")
    override suspend fun getEvolutionEntries(limit: Int): List<com.lanxin.android.plugins.memory.data.memory.EvolutionEntry> = error("not mocked")
    override suspend fun insertEvolutionEntry(entry: com.lanxin.android.plugins.memory.data.memory.EvolutionEntry) = error("not mocked")
    override suspend fun getPendingTaskResume(): com.lanxin.android.plugins.memory.data.memory.TaskResumeEntity? = error("not mocked")
    override suspend fun markTaskResumeResolved(id: Long) = error("not mocked")
    override suspend fun insertTaskResume(resume: com.lanxin.android.plugins.memory.data.memory.TaskResumeEntity) = error("not mocked")
    override suspend fun getUnarchivedDialogs(limit: Int): List<com.lanxin.android.plugins.memory.data.memory.DialogEntity> = error("not mocked")
    override suspend fun markDialogArchived(id: Long) = error("not mocked")
}
