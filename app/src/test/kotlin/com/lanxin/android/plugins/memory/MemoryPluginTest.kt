package com.lanxin.android.plugins.memory

import com.lanxin.android.plugin.PluginContext
import com.lanxin.android.plugin.ToolDef
import com.lanxin.android.plugins.memory.data.memory.MemoryExportItem
import com.lanxin.android.plugins.memory.data.memory.MemoryExportPayload
import com.lanxin.android.plugins.memory.data.memory.MemoryType
import java.io.File
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
        assertTrue(payload.contains("\"version\":1"))
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
        assertTrue(payload.contains("- filter: preference"))
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

    private fun memoryPlugin(): MemoryPlugin {
        val repository = org.mockito.kotlin.mock<com.lanxin.android.plugins.memory.data.memory.MemoryRepository>()
        val fake = FakeMemoryRepository()
        org.mockito.kotlin.whenever(repository.exportToJsonText(org.mockito.kotlin.anyOrNull())).thenAnswer {
            fake.exportToJsonText(it.arguments[0] as String?)
        }
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

private class FakeMemoryRepository {
    private val json = Json { encodeDefaults = true }

    suspend fun exportToJsonText(typeFilter: String?): String {
        val all = sampleItems()
        val filtered = if (typeFilter.isNullOrBlank()) {
            all
        } else {
            all.filter { it.type.equals(typeFilter, ignoreCase = true) }
        }
        return json.encodeToString(
            MemoryExportPayload.serializer(),
            MemoryExportPayload(
                version = 1,
                exportedAt = 1_700_100_000_000L,
                memories = filtered
            )
        )
    }

    private fun sampleItems() = listOf(
        MemoryExportItem(
            id = 1,
            content = "喜欢草莓",
            type = MemoryType.PREFERENCE,
            importance = 8f,
            createdAt = 1_700_000_000_000L,
            lifecycle = "permanent",
            metadata = "{\"tags\":[\"food\"]}"
        ),
        MemoryExportItem(
            id = 2,
            content = "普通聊天",
            type = MemoryType.CHAT,
            importance = 3f,
            createdAt = 1_700_000_100_000L,
            lifecycle = "normal"
        )
    )
}
