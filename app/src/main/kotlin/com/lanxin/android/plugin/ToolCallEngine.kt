package com.lanxin.android.plugin

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * 工具调用请求（从 AI 回复中解析出）。
 */
data class ToolCallRequest(
    val name: String,
    val arguments: JsonObject = buildJsonObject { },
    /** 匹配到的原始标签文本，用于从回复中剥离 */
    val raw: String = ""
)

/**
 * 单次工具调用结果。
 */
data class ToolCallResult(
    val name: String,
    val result: JsonObject
)

/**
 * AI 核心与 PluginManager MCP 工具的对接引擎。
 *
 * 职责：
 * 1. 将 [PluginManager.getTools] 格式化为系统提示词片段
 * 2. 从 AI 回复中解析工具调用请求
 * 3. 路由到 [PluginManager.callTool] 并汇总结果
 * 4. 生成回填给 AI 的后续消息
 */
@Singleton
class ToolCallEngine @Inject constructor(
    private val pluginManager: PluginManager
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 返回当前已注册的全部 MCP 工具（只读快照）。
     */
    fun getRegisteredTools(): List<ToolDef> = pluginManager.getTools()

    /**
     * 根据当前已注册工具构建系统提示词片段。
     * 无工具时返回空字符串。
     *
     * @param tools 若指定则仅使用该列表；默认取 [PluginManager.getTools]
     */
    fun buildToolsSystemPrompt(tools: List<ToolDef> = pluginManager.getTools()): String {
        if (tools.isEmpty()) return ""

        return buildString {
            appendLine("[可用工具]")
            appendLine("你可以通过输出以下 XML 标签调用工具：")
            appendLine("<tool_call>{\"name\":\"工具名\",\"arguments\":{...}}</tool_call>")
            appendLine("规则：")
            appendLine("1. 仅在确实需要外部能力时调用工具")
            appendLine("2. arguments 必须是合法 JSON 对象")
            appendLine("3. 一次可输出多个 tool_call 标签")
            appendLine("4. 工具结果会回填给你，之后再给出最终回复")
            appendLine()
            appendLine("工具列表：")
            tools.forEach { tool ->
                appendLine("- ${tool.name}: ${tool.description}")
                if (tool.parameters.isNotEmpty()) {
                    appendLine("  参数 schema: ${tool.parameters}")
                }
            }
            append("[工具结束]")
        }
    }

    /**
     * 将工具提示拼接到既有 system prompt 上。
     *
     * @param tools 可选；传入时按该列表构建工具提示（用于人格 tools/skills 过滤）
     */
    fun mergeSystemPrompt(
        existing: String?,
        toolsPrompt: String? = null,
        tools: List<ToolDef>? = null
    ): String? {
        val resolvedToolsPrompt = when {
            toolsPrompt != null -> toolsPrompt
            tools != null -> buildToolsSystemPrompt(tools)
            else -> buildToolsSystemPrompt()
        }
        val base = existing?.trim().orEmpty()
        val toolsText = resolvedToolsPrompt.trim()
        return when {
            toolsText.isEmpty() && base.isEmpty() -> null
            toolsText.isEmpty() -> base
            base.isEmpty() -> toolsText
            else -> "$base\n\n$toolsText"
        }
    }

    /**
     * 从 AI 回复文本中解析全部工具调用。
     *
     * 支持格式：
     * - `<tool_call>{"name":"x","arguments":{...}}</tool_call>`
     * - 兼容 `parameters` 作为 arguments 别名
     */
    fun parseToolCalls(text: String): List<ToolCallRequest> {
        if (text.isBlank()) return emptyList()

        val results = mutableListOf<ToolCallRequest>()
        val matcher = TOOL_CALL_REGEX.findAll(text)
        for (match in matcher) {
            val raw = match.value
            val body = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (body.isEmpty()) continue

            val request = parseToolCallBody(body, raw) ?: continue
            results.add(request)
        }
        return results
    }

    /**
     * 文本中是否包含工具调用。
     */
    fun hasToolCalls(text: String): Boolean = parseToolCalls(text).isNotEmpty()

    /**
     * 剥离工具调用标签，保留纯文本回复部分。
     */
    fun stripToolCalls(text: String): String =
        TOOL_CALL_REGEX.replace(text, "").trim()

    /**
     * 执行一组工具调用，路由到 PluginManager。
     *
     * @param allowedToolNames 若非 null，仅执行列表中的工具名；其余返回 error
     */
    suspend fun executeToolCalls(
        calls: List<ToolCallRequest>,
        allowedToolNames: Set<String>? = null
    ): List<ToolCallResult> {
        if (calls.isEmpty()) return emptyList()
        return calls.map { call ->
            if (allowedToolNames != null && call.name !in allowedToolNames) {
                ToolCallResult(
                    name = call.name,
                    result = buildJsonObject {
                        put("error", "工具 ${call.name} 当前人格不允许使用")
                    }
                )
            } else {
                val result = pluginManager.callTool(call.name, call.arguments)
                ToolCallResult(name = call.name, result = result)
            }
        }
    }

    /**
     * 将工具结果格式化为回填给 AI 的消息内容。
     */
    fun formatToolResultsMessage(results: List<ToolCallResult>): String {
        if (results.isEmpty()) return ""
        return buildString {
            appendLine("[工具结果]")
            results.forEach { item ->
                appendLine("- ${item.name}: ${item.result}")
            }
            append("[结果结束]")
            appendLine()
            append("请基于以上工具结果继续回复用户。不要再次输出相同的 tool_call，除非确实需要新的工具调用。")
        }
    }

    /**
     * 完整一轮：解析 → 执行 → 生成回填消息。
     * 若无工具调用返回 null。
     *
     * @param allowedToolNames 人格 tools/skills 过滤后的允许工具名；null 表示不限制
     */
    suspend fun processAssistantReply(
        assistantText: String,
        allowedToolNames: Set<String>? = null
    ): ToolRoundResult? {
        val calls = parseToolCalls(assistantText)
        if (calls.isEmpty()) return null

        val results = executeToolCalls(calls, allowedToolNames)
        return ToolRoundResult(
            calls = calls,
            results = results,
            cleanedAssistantText = stripToolCalls(assistantText),
            followUpUserMessage = formatToolResultsMessage(results)
        )
    }

    private fun parseToolCallBody(body: String, raw: String): ToolCallRequest? {
        return try {
            val element = json.parseToJsonElement(body)
            val obj = element.jsonObject
            val name = when (val nameEl = obj["name"]) {
                is kotlinx.serialization.json.JsonPrimitive -> nameEl.content.trim()
                else -> nameEl?.toString()?.trim('"')?.trim().orEmpty()
            }
            if (name.isBlank()) return null

            val argumentsElement = obj["arguments"] ?: obj["parameters"]
            val arguments = when (argumentsElement) {
                is JsonObject -> argumentsElement
                is kotlinx.serialization.json.JsonPrimitive -> {
                    val text = argumentsElement.content.trim()
                    runCatching {
                        json.parseToJsonElement(text).jsonObject
                    }.getOrElse { buildJsonObject { } }
                }
                null -> buildJsonObject { }
                else -> buildJsonObject { }
            }

            ToolCallRequest(name = name, arguments = arguments, raw = raw)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /** 匹配 <tool_call>...</tool_call>，支持跨行 */
        private val TOOL_CALL_REGEX = Regex(
            """<tool_call>\s*([\s\S]*?)\s*</tool_call>""",
            setOf(RegexOption.IGNORE_CASE)
        )
    }
}

/**
 * 一轮工具调用处理结果。
 */
data class ToolRoundResult(
    val calls: List<ToolCallRequest>,
    val results: List<ToolCallResult>,
    /** 剥离 tool_call 标签后的助手文本 */
    val cleanedAssistantText: String,
    /** 回填给 AI 的用户侧消息（工具结果） */
    val followUpUserMessage: String
)
