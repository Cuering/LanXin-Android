package com.lanxin.android.plugin

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallEngineTest {

    private fun engineWithTools(vararg tools: ToolDef): ToolCallEngine {
        val manager = PluginManager(appContext = FakeContext())
        // 直接通过反射/内部路径不可用，这里用自定义 stub 引擎测纯解析逻辑
        return ToolCallEngine(manager).also {
            // 无工具也能测解析；执行路由另测
        }
    }

    @Test
    fun `parseToolCalls extracts name and arguments`() {
        val engine = ToolCallEngine(PluginManager(FakeContext()))
        val text = """
            我来查一下记忆。
            <tool_call>
            {"name":"memory_recall","arguments":{"keyword":"咖啡","limit":3}}
            </tool_call>
            稍等。
        """.trimIndent()

        val calls = engine.parseToolCalls(text)
        assertEquals(1, calls.size)
        assertEquals("memory_recall", calls[0].name)
        assertEquals("咖啡", calls[0].arguments["keyword"].toString().trim('"'))
        assertEquals("3", calls[0].arguments["limit"].toString().trim('"'))
    }

    @Test
    fun `parseToolCalls supports multiple calls and parameters alias`() {
        val engine = ToolCallEngine(PluginManager(FakeContext()))
        val text = """
            <tool_call>{"name":"memory_store","parameters":{"content":"喜欢绿茶"}}</tool_call>
            <tool_call>{"name":"memory_recall","arguments":{"keyword":"茶"}}</tool_call>
        """.trimIndent()

        val calls = engine.parseToolCalls(text)
        assertEquals(2, calls.size)
        assertEquals("memory_store", calls[0].name)
        assertEquals("喜欢绿茶", calls[0].arguments["content"].toString().trim('"'))
        assertEquals("memory_recall", calls[1].name)
    }

    @Test
    fun `stripToolCalls removes tags and keeps prose`() {
        val engine = ToolCallEngine(PluginManager(FakeContext()))
        val text = "先查一下。<tool_call>{\"name\":\"memory_recall\",\"arguments\":{\"keyword\":\"x\"}}</tool_call>再回复。"
        val cleaned = engine.stripToolCalls(text)
        assertFalse(cleaned.contains("tool_call"))
        assertTrue(cleaned.contains("先查一下"))
        assertTrue(cleaned.contains("再回复"))
    }

    @Test
    fun `hasToolCalls returns false for plain text`() {
        val engine = ToolCallEngine(PluginManager(FakeContext()))
        assertFalse(engine.hasToolCalls("你好，今天天气不错"))
        assertTrue(engine.hasToolCalls("<tool_call>{\"name\":\"a\",\"arguments\":{}}</tool_call>"))
    }

    @Test
    fun `buildToolsSystemPrompt lists tools`() {
        val tools = listOf(
            ToolDef(
                name = "memory_recall",
                description = "检索记忆",
                parameters = buildJsonObject { put("type", "object") },
                handler = { buildJsonObject { } }
            )
        )
        val engine = ToolCallEngine(PluginManager(FakeContext()))
        val prompt = engine.buildToolsSystemPrompt(tools)
        assertTrue(prompt.contains("memory_recall"))
        assertTrue(prompt.contains("检索记忆"))
        assertTrue(prompt.contains("<tool_call>"))
    }

    @Test
    fun `buildToolsSystemPrompt empty when no tools`() {
        val engine = ToolCallEngine(PluginManager(FakeContext()))
        assertEquals("", engine.buildToolsSystemPrompt(emptyList()))
    }

    @Test
    fun `mergeSystemPrompt concatenates base and tools`() {
        val engine = ToolCallEngine(PluginManager(FakeContext()))
        val merged = engine.mergeSystemPrompt("你是兰心", "[可用工具]\nx")
        assertEquals("你是兰心\n\n[可用工具]\nx", merged)
        assertNull(engine.mergeSystemPrompt(null, ""))
        assertEquals("only tools", engine.mergeSystemPrompt("  ", "only tools"))
    }

    @Test
    fun `executeToolCalls routes to PluginManager`() = runBlocking {
        val manager = PluginManager(FakeContext())
        val plugin = object : LanXinPlugin {
            override val id = "test"
            override val name = "test"
            override val version = "1"
            override val description = ""
            override suspend fun onLoad(context: PluginContext) {
                context.registerTool(
                    ToolDef(
                        name = "echo",
                        description = "echo args",
                        handler = { args ->
                            buildJsonObject {
                                put("echo", args["msg"].toString().trim('"'))
                            }
                        }
                    )
                )
            }
        }
        manager.register(plugin)
        manager.loadAll()

        val engine = ToolCallEngine(manager)
        val results = engine.executeToolCalls(
            listOf(
                ToolCallRequest(
                    name = "echo",
                    arguments = buildJsonObject { put("msg", "hi") }
                )
            )
        )
        assertEquals(1, results.size)
        assertEquals("echo", results[0].name)
        assertEquals("hi", results[0].result["echo"].toString().trim('"'))
    }

    @Test
    fun `processAssistantReply returns null without tool calls`() = runBlocking {
        val engine = ToolCallEngine(PluginManager(FakeContext()))
        assertNull(engine.processAssistantReply("普通回复，没有工具"))
    }

    @Test
    fun `processAssistantReply executes and formats follow-up`() = runBlocking {
        val manager = PluginManager(FakeContext())
        val plugin = object : LanXinPlugin {
            override val id = "test"
            override val name = "test"
            override val version = "1"
            override val description = ""
            override suspend fun onLoad(context: PluginContext) {
                context.registerTool(
                    ToolDef(
                        name = "memory_recall",
                        description = "recall",
                        handler = {
                            buildJsonObject { put("count", 0) }
                        }
                    )
                )
            }
        }
        manager.register(plugin)
        manager.loadAll()

        val engine = ToolCallEngine(manager)
        val round = engine.processAssistantReply(
            "查询中 <tool_call>{\"name\":\"memory_recall\",\"arguments\":{\"keyword\":\"a\"}}</tool_call>"
        )
        assertTrue(round != null)
        assertEquals(1, round!!.calls.size)
        assertEquals(1, round.results.size)
        assertEquals("0", round.results[0].result["count"].toString())
        assertTrue(round.followUpUserMessage.contains("[工具结果]"))
        assertFalse(round.cleanedAssistantText.contains("tool_call"))
    }

    @Test
    fun `callTool unknown tool returns error`() = runBlocking {
        val manager = PluginManager(FakeContext())
        val result = manager.callTool("missing", buildJsonObject { })
        assertTrue(result["error"].toString().contains("未找到"))
    }
}

/**
 * 最小 Context stub，仅满足 PluginManager 构造；测试中不触碰 filesDir。
 */
private class FakeContext : android.content.ContextWrapper(null) {
    override fun getApplicationContext(): android.content.Context = this

    override fun getFilesDir(): java.io.File = java.io.File(System.getProperty("java.io.tmpdir"), "lanxin-test-files").also { it.mkdirs() }
}
