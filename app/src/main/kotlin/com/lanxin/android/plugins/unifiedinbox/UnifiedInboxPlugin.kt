package com.lanxin.android.plugins.unifiedinbox

import android.content.Context
import com.lanxin.android.core.engine.BackupContributor
import com.lanxin.android.plugin.LanXinPlugin
import com.lanxin.android.plugin.PluginContext
import com.lanxin.android.plugin.ToolDef
import com.lanxin.android.plugins.unifiedinbox.data.CrossSessionRepository
import com.lanxin.android.plugins.unifiedinbox.domain.CrossSessionIndexer
import com.lanxin.android.plugins.unifiedinbox.domain.UnifiedFileBrowser
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Singleton
class UnifiedInboxPlugin @Inject constructor(
    private val repository: CrossSessionRepository,
    private val indexer: CrossSessionIndexer,
    private val fileBrowser: UnifiedFileBrowser
) : LanXinPlugin, BackupContributor {

    override val id = "lanxin.unified_inbox"
    override val name = "统一收件箱"
    override val version = "1.0.0"
    override val description = "跨 session 对话历史聚合与跨工作区文件浏览"

    override suspend fun onLoad(context: PluginContext) {
        context.registerTool(
            ToolDef(
                name = "inbox_search",
                description = "跨会话搜索本地归档对话",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "keyword",
                                buildJsonObject {
                                    put("type", "string")
                                    put("description", "搜索关键词")
                                }
                            )
                            put(
                                "platform",
                                buildJsonObject {
                                    put("type", "string")
                                    put("description", "平台过滤，可空")
                                }
                            )
                            put(
                                "limit",
                                buildJsonObject {
                                    put("type", "integer")
                                    put("description", "最多返回条数，默认 10")
                                }
                            )
                        }
                    )
                    put(
                        "required",
                        buildJsonArray {
                            add(JsonPrimitive("keyword"))
                        }
                    )
                },
                handler = { args ->
                    val keyword = args["keyword"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    val platform = args["platform"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    val limit = args["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.coerceIn(1, 50) ?: 10
                    if (keyword.isBlank()) {
                        return@ToolDef buildJsonObject {
                            put("error", "keyword 不能为空")
                        }
                    }
                    val hits = repository.search(query = keyword, platform = platform, limit = limit)
                    buildJsonObject {
                        put("count", hits.size)
                        put(
                            "messages",
                            buildJsonArray {
                                hits.forEach { msg ->
                                    add(
                                        buildJsonObject {
                                            put("id", msg.id)
                                            put("platform", msg.platform)
                                            put("session_id", msg.sessionId)
                                            put("session_title", msg.sessionTitle)
                                            put("role", msg.role)
                                            put("content", msg.content.take(500))
                                            put("time", msg.time)
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "inbox_reindex",
                description = "从本地 chat 会话重建跨会话索引",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {})
                },
                handler = {
                    val result = indexer.reindexAll()
                    buildJsonObject {
                        put("ok", true)
                        put("sessions", result.sessions)
                        put("messages", result.messages)
                        put("duration_ms", result.durationMs)
                    }
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "workspace_list",
                description = "列出工作区目录下的文件/文件夹",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "path",
                                buildJsonObject {
                                    put("type", "string")
                                    put("description", "绝对路径，空则默认 filesDir/workspaces")
                                }
                            )
                        }
                    )
                },
                handler = { args ->
                    val path = args["path"]?.jsonPrimitive?.contentOrNull?.trim()
                    val items = fileBrowser.listDirectory(path)
                    buildJsonObject {
                        put("count", items.size)
                        put(
                            "items",
                            buildJsonArray {
                                items.forEach { item ->
                                    add(
                                        buildJsonObject {
                                            put("name", item.name)
                                            put("path", item.path)
                                            put("is_directory", item.isDirectory)
                                            put("size", item.sizeBytes)
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            )
        )
    }

    override fun getBackupFiles(context: Context): List<File> {
        val dbDir = context.getDatabasePath(CrossSessionDbName.NAME).parentFile ?: return emptyList()
        return listOf(
            File(dbDir, CrossSessionDbName.NAME),
            File(dbDir, "${CrossSessionDbName.NAME}-shm"),
            File(dbDir, "${CrossSessionDbName.NAME}-wal")
        ).filter { it.exists() }
    }
}

object CrossSessionDbName {
    const val NAME = "lanxin_unified_inbox.db"
}
