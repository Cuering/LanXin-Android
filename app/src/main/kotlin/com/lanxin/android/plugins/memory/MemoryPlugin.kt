package com.lanxin.android.plugins.memory

import com.lanxin.android.plugin.LanXinPlugin
import com.lanxin.android.plugin.PluginContext
import com.lanxin.android.plugin.ToolDef
import com.lanxin.android.plugins.memory.data.memory.MemoryRepository
import com.lanxin.android.plugins.memory.data.memory.MemoryType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Singleton
class MemoryPlugin @Inject constructor(
    private val memoryRepository: MemoryRepository
) : LanXinPlugin {

    override val id = "lanxin.memory"
    override val name = "记忆系统"
    override val version = "1.0.0"
    override val description = "本地记忆仓库，自动保存与注入聊天上下文"

    override suspend fun onLoad(context: PluginContext) {
        context.registerTool(
            ToolDef(
                name = "memory_recall",
                description = "根据关键词检索本地记忆，返回相关记忆条目列表",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("keyword", buildJsonObject {
                            put("type", "string")
                            put("description", "检索关键词")
                        })
                        put("limit", buildJsonObject {
                            put("type", "integer")
                            put("description", "最多返回条数，默认 5")
                        })
                    })
                    put("required", buildJsonArray {
                        add(JsonPrimitive("keyword"))
                    })
                },
                handler = { args ->
                    val keyword = args["keyword"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    val limit = args["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.coerceIn(1, 20) ?: 5
                    if (keyword.isBlank()) {
                        return@ToolDef buildJsonObject {
                            put("error", "keyword 不能为空")
                        }
                    }

                    val memories = memoryRepository.searchMemories(keyword).take(limit)
                    buildJsonObject {
                        put("count", memories.size)
                        put("memories", buildJsonArray {
                            memories.forEach { memory ->
                                add(buildJsonObject {
                                    put("id", memory.id)
                                    put("content", memory.content)
                                    put("type", memory.type)
                                    put("type_label", MemoryType.displayName(memory.type))
                                    put("importance", memory.importance)
                                })
                            }
                        })
                    }
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "memory_store",
                description = "将一条信息写入本地记忆库",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("content", buildJsonObject {
                            put("type", "string")
                            put("description", "要记住的内容")
                        })
                        put("type", buildJsonObject {
                            put("type", "string")
                            put("description", "记忆类型: preference/factual/daily/chat/insight/instruction，默认 chat")
                        })
                        put("importance", buildJsonObject {
                            put("type", "number")
                            put("description", "重要程度 1-10，默认 5")
                        })
                    })
                    put("required", buildJsonArray {
                        add(JsonPrimitive("content"))
                    })
                },
                handler = { args ->
                    val content = args["content"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (content.isBlank()) {
                        return@ToolDef buildJsonObject {
                            put("error", "content 不能为空")
                        }
                    }

                    val type = args["type"]?.jsonPrimitive?.contentOrNull?.trim()
                        ?.takeIf { it in MemoryType.ALL }
                        ?: MemoryType.CHAT
                    val importance = args["importance"]?.jsonPrimitive?.floatOrNull?.coerceIn(1f, 10f) ?: 5f

                    val id = memoryRepository.addMemory(
                        content = content,
                        type = type,
                        importance = importance
                    )
                    buildJsonObject {
                        put("ok", true)
                        put("id", id)
                        put("type", type)
                        put("importance", importance)
                    }
                }
            )
        )
    }
}
