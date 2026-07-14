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

package com.lanxin.android.builtin.scheduler

import com.lanxin.android.builtin.scheduler.domain.SchedulerEngine
import com.lanxin.android.builtin.scheduler.domain.SchedulerRepository
import com.lanxin.android.builtin.scheduler.domain.SchedulerTaskType
import com.lanxin.android.builtin.scheduler.domain.TaskStatus
import com.lanxin.android.builtin.scheduler.registry.TaskActionRegistry
import com.lanxin.android.plugin.LanXinPlugin
import com.lanxin.android.plugin.PluginContext
import com.lanxin.android.plugin.ToolDef
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 定时任务插件。MCP 工具：
 * task_create / task_list / task_update / task_delete /
 * task_run_now / task_pause / task_resume
 */
@Singleton
class SchedulerPlugin @Inject constructor(
    private val repository: SchedulerRepository,
    private val engine: SchedulerEngine,
    private val actionRegistry: TaskActionRegistry
) : LanXinPlugin {

    override val id = "lanxin.scheduler"
    override val name = "定时任务"
    override val version = "1.0.0"
    override val description = "定时任务自动执行：BASIC 回调与 ACTIVE_AGENT 对话提醒"

    private val timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    override suspend fun onLoad(context: PluginContext) {
        // 冷启动时恢复调度
        runCatching { engine.rescheduleAllEnabled() }

        context.registerTool(
            ToolDef(
                name = "task_create",
                description = "创建定时任务。BASIC 需 payload.action 已注册；ACTIVE_AGENT 用于通知栏提醒并唤起对话",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("name", stringProp("任务名称"))
                            put("type", stringProp("BASIC 或 ACTIVE_AGENT，默认 BASIC"))
                            put("cron", stringProp("标准 5 字段 cron，与 run_at 二选一"))
                            put("run_at", longProp("一次性执行时间 epoch ms，与 cron 二选一"))
                            put("payload", objectProp("任务参数。BASIC 必须含 action"))
                            put("auto_start", boolProp("ACTIVE_AGENT 点击通知是否自动发送，默认 true"))
                            put("enabled", boolProp("是否立即启用，默认 true"))
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("name")) })
                },
                handler = { args ->
                    runCatching {
                        val name = args.string("name") ?: error("name 必填")
                        val type = when (args.string("type")?.uppercase()) {
                            "ACTIVE_AGENT" -> SchedulerTaskType.ACTIVE_AGENT
                            else -> SchedulerTaskType.BASIC
                        }
                        val cron = args.string("cron")
                        val runAt = args.long("run_at")
                        require(!cron.isNullOrBlank() || runAt != null) {
                            "必须提供 cron 或 run_at"
                        }
                        val payload = args.obj("payload")?.mapValues { it.value.jsonPrimitive.content }
                            ?: emptyMap()
                        if (type == SchedulerTaskType.BASIC) {
                            val action = payload["action"]
                            require(!action.isNullOrBlank()) { "BASIC 任务 payload.action 必填" }
                            require(actionRegistry.getHandler(action) != null) {
                                "action 未注册：$action，可用：${actionRegistry.listActions()}"
                            }
                        }
                        val task = repository.create(
                            name = name,
                            type = type,
                            cronExpression = cron,
                            runAt = runAt,
                            payload = payload,
                            autoStartConversation = args.bool("auto_start") ?: true,
                            enabled = args.bool("enabled") ?: true
                        )
                        if (task.enabled) {
                            engine.scheduleTask(task.id)
                        }
                        task.toJson()
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "task_list",
                description = "列出定时任务，可按 type / status 过滤",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("type", stringProp("BASIC / ACTIVE_AGENT"))
                            put("status", stringProp("IDLE / SCHEDULED / RUNNING / COMPLETED / FAILED / CANCELLED"))
                        }
                    )
                },
                handler = { args ->
                    runCatching {
                        val type = args.string("type")?.let { SchedulerTaskType.valueOf(it.uppercase()) }
                        val status = args.string("status")?.let { TaskStatus.valueOf(it.uppercase()) }
                        val tasks = repository.filter(type, status)
                        buildJsonObject {
                            put("count", tasks.size)
                            put(
                                "tasks",
                                buildJsonArray {
                                    tasks.forEach { add(it.toJson()) }
                                }
                            )
                            put("actions", buildJsonArray {
                                actionRegistry.listActions().forEach { add(JsonPrimitive(it)) }
                            })
                        }
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "task_update",
                description = "更新任务字段，并重新调度",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("id", stringProp("任务 ID"))
                            put("name", stringProp("新名称"))
                            put("cron", stringProp("新 cron；传空字符串可清除"))
                            put("run_at", longProp("新 run_at"))
                            put("payload", objectProp("新 payload（整体替换）"))
                            put("auto_start", boolProp("是否自动对话"))
                            put("enabled", boolProp("启用状态"))
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("id")) })
                },
                handler = { args ->
                    runCatching {
                        val id = args.string("id") ?: error("id 必填")
                        engine.cancelTask(id)
                        val cronArg = if (args.containsKey("cron")) args.string("cron") else null
                        val task = repository.updateFields(
                            id = id,
                            name = args.string("name"),
                            cronExpression = cronArg?.takeIf { it.isNotBlank() },
                            clearCron = cronArg != null && cronArg.isBlank(),
                            runAt = args.long("run_at"),
                            payload = args.obj("payload")?.mapValues { it.value.jsonPrimitive.content },
                            autoStartConversation = args.bool("auto_start"),
                            enabled = args.bool("enabled")
                        )
                        if (task.enabled) engine.scheduleTask(task.id)
                        task.toJson()
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "task_delete",
                description = "删除任务并取消调度",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("id", stringProp("任务 ID"))
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("id")) })
                },
                handler = { args ->
                    runCatching {
                        val id = args.string("id") ?: error("id 必填")
                        engine.cancelTask(id)
                        repository.delete(id)
                        buildJsonObject {
                            put("ok", true)
                            put("id", id)
                        }
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "task_run_now",
                description = "立即执行任务（不改变周期调度）",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("id", stringProp("任务 ID"))
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("id")) })
                },
                handler = { args ->
                    runCatching {
                        val id = args.string("id") ?: error("id 必填")
                        engine.runNow(id)
                        buildJsonObject {
                            put("ok", true)
                            put("id", id)
                            put("message", "已 enqueue 立即执行")
                        }
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "task_pause",
                description = "暂停任务（enabled=false，取消调度）",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("id", stringProp("任务 ID"))
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("id")) })
                },
                handler = { args ->
                    runCatching {
                        val id = args.string("id") ?: error("id 必填")
                        engine.cancelTask(id)
                        val task = repository.setEnabled(id, false)
                        task.toJson()
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "task_resume",
                description = "恢复任务（enabled=true，重新计算 nextRunAt 并调度）",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("id", stringProp("任务 ID"))
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("id")) })
                },
                handler = { args ->
                    runCatching {
                        val id = args.string("id") ?: error("id 必填")
                        val task = repository.setEnabled(id, true)
                        engine.scheduleTask(task.id)
                        task.toJson()
                    }.toToolResult()
                }
            )
        )
    }

    private fun com.lanxin.android.builtin.scheduler.domain.SchedulerTask.toJson(): JsonObject {
        val task = this
        return buildJsonObject {
            put("id", task.id)
            put("name", task.name)
            put("type", task.type.name)
            put("cron", task.cronExpression ?: "")
            put("run_once", task.runOnce)
            put("run_at", task.runAt ?: 0L)
            put("enabled", task.enabled)
            put("auto_start_conversation", task.autoStartConversation)
            put("status", task.status.name)
            put("next_run_at", task.nextRunAt ?: 0L)
            put("next_run_at_text", task.nextRunAt?.let { timeFmt.format(Instant.ofEpochMilli(it)) } ?: "")
            put("last_run_at", task.lastRunAt ?: 0L)
            put("last_error", task.lastError ?: "")
            put(
                "payload",
                buildJsonObject {
                    task.payload.forEach { (k, v) -> put(k, v) }
                }
            )
            put(
                "cron_human",
                task.cronExpression?.let { repository.humanReadable(it) } ?: "一次性"
            )
            put("created_at", task.createdAt)
        }
    }

    private fun Result<JsonObject>.toToolResult(): JsonObject =
        fold(
            onSuccess = { it },
            onFailure = { e ->
                buildJsonObject {
                    put("error", e.message ?: e.toString())
                }
            }
        )

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.long(key: String): Long? =
        this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

    private fun JsonObject.bool(key: String): Boolean? =
        this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()

    private fun JsonObject.obj(key: String): JsonObject? =
        this[key]?.jsonObject

    private fun stringProp(desc: String) = buildJsonObject {
        put("type", "string")
        put("description", desc)
    }

    private fun longProp(desc: String) = buildJsonObject {
        put("type", "integer")
        put("description", desc)
    }

    private fun boolProp(desc: String) = buildJsonObject {
        put("type", "boolean")
        put("description", desc)
    }

    private fun objectProp(desc: String) = buildJsonObject {
        put("type", "object")
        put("description", desc)
    }
}
