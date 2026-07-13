package com.lanxin.android.plugin

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * MCP 工具定义。
 *
 * 插件通过 [PluginContext.registerTool] 注册工具，
 * AI 核心根据名称和描述动态调用。
 *
 * @param name 工具名称，唯一标识
 * @param description 工具描述，AI 理解其用途
 * @param parameters JSON Schema 描述参数结构
 * @param handler 工具执行逻辑
 */
data class ToolDef(
    val name: String,
    val description: String,
    val parameters: JsonObject = buildJsonObject { },
    val handler: suspend (JsonObject) -> JsonObject
)
