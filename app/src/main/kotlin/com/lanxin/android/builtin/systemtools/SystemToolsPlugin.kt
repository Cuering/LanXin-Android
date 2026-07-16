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

package com.lanxin.android.builtin.systemtools

import com.lanxin.android.builtin.systemtools.data.DeviceToolRegistry
import com.lanxin.android.builtin.systemtools.domain.DeviceToolGate
import com.lanxin.android.builtin.systemtools.domain.DeviceToolIds
import com.lanxin.android.builtin.systemtools.domain.DeviceToolOutcome
import com.lanxin.android.builtin.systemtools.domain.SystemToolsSettings
import com.lanxin.android.plugin.LanXinPlugin
import com.lanxin.android.plugin.PluginContext
import com.lanxin.android.plugin.ToolDef
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Phase 7 系统能力插件：日历 / 闹钟 / 笔记（用户文件 M2）。
 *
 * 与 ChatRouter needsTools、桌宠 VoiceSession tool 钩子共用 [DeviceTool] 契约。
 */
@Singleton
class SystemToolsPlugin @Inject constructor(
    private val registry: DeviceToolRegistry,
    private val settings: SystemToolsSettings
) : LanXinPlugin {

    override val id = "lanxin.systemtools"
    override val name = "系统能力"
    override val version = "0.2.0"
    override val description =
        "日历读+创建 Intent / setAlarmClock+AlarmClock Intent / 内置笔记（Phase 7.2，默认关，写操作需确认）"

    private val gate = DeviceToolGate { settings.getConfig() }

    override suspend fun onLoad(context: PluginContext) {
        register(
            context,
            name = DeviceToolIds.ALARM_SET,
            description = "设置闹钟：默认 setAlarmClock；mode=intent 时 startActivity SET_ALARM",
            properties = buildJsonObject {
                put("hour", intProp("小时 0-23（与 trigger_at_epoch_ms 二选一）"))
                put("minutes", intProp("分钟 0-59"))
                put("trigger_at_epoch_ms", intProp("绝对触发时刻 epoch ms"))
                put("message", stringProp("闹钟标签"))
                put("mode", stringProp("set_alarm_clock（默认）| intent"))
                put("skip_ui", boolProp("intent 模式：尽量跳过系统 UI"))
                put("vibrate", boolProp("intent 模式：振动，默认 true"))
                put("days", stringProp("intent 模式：重复日 1-7 逗号分隔"))
                put("confirmed", boolProp("写操作用户已确认，默认 false"))
            }
        )
        register(
            context,
            name = DeviceToolIds.ALARM_SHOW,
            description = "打开系统闹钟列表（startActivity SHOW_ALARMS）",
            properties = buildJsonObject { }
        )
        register(
            context,
            name = DeviceToolIds.CALENDAR_LIST_UPCOMING,
            description = "列出接下来 N 天内的日历事件（CalendarContract；无权限返回提示）",
            properties = buildJsonObject {
                put("limit", intProp("最多条数，默认 10"))
                put("days", intProp("未来天数 1-30，默认 7"))
                put("after_epoch_ms", intProp("起始时间 epoch ms，默认现在"))
            }
        )
        register(
            context,
            name = DeviceToolIds.CALENDAR_CREATE_EVENT,
            description = "创建日历事件：默认系统日历 INSERT Intent；mode=stub 内存；需 confirmed",
            properties = buildJsonObject {
                put("title", stringProp("标题"))
                put("start_epoch_ms", intProp("开始 epoch ms"))
                put("end_epoch_ms", intProp("结束 epoch ms"))
                put("location", stringProp("地点"))
                put("description", stringProp("描述"))
                put("mode", stringProp("intent（默认）| stub"))
                put("confirmed", boolProp("用户已确认"))
            },
            required = listOf("title", "start_epoch_ms")
        )
        register(
            context,
            name = DeviceToolIds.NOTE_CREATE,
            description = "创建内置笔记（需 confirmed）",
            properties = buildJsonObject {
                put("title", stringProp("标题"))
                put("body", stringProp("正文"))
                put("confirmed", boolProp("用户已确认"))
            }
        )
        register(
            context,
            name = DeviceToolIds.NOTE_LIST,
            description = "列出内置笔记",
            properties = buildJsonObject {
                put("limit", intProp("最多条数，默认 50"))
            }
        )
        register(
            context,
            name = DeviceToolIds.NOTE_APPEND,
            description = "追加笔记正文（需 confirmed）",
            properties = buildJsonObject {
                put("id", stringProp("笔记 id"))
                put("text", stringProp("追加文本"))
                put("confirmed", boolProp("用户已确认"))
            },
            required = listOf("id", "text")
        )
    }

    private fun register(
        context: PluginContext,
        name: String,
        description: String,
        properties: JsonObject,
        required: List<String> = emptyList()
    ) {
        context.registerTool(
            ToolDef(
                name = name,
                description = description,
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", properties)
                    if (required.isNotEmpty()) {
                        put(
                            "required",
                            buildJsonArray { required.forEach { add(JsonPrimitive(it)) } }
                        )
                    }
                },
                handler = { args ->
                    val tool = registry.get(name)
                        ?: return@ToolDef outcomeToJson(
                            DeviceToolOutcome.Error("unknown tool $name", "unknown_tool")
                        )
                    val map = args.toArgMap()
                    val confirmed = map["confirmed"] as? Boolean
                        ?: args.bool("confirmed")
                        ?: false
                    val outcome = gate.invoke(tool, map, confirmed = confirmed)
                    outcomeToJson(outcome)
                }
            )
        )
    }

    private fun JsonObject.toArgMap(): Map<String, Any?> {
        val out = mutableMapOf<String, Any?>()
        for ((k, v) in this) {
            out[k] = v.toAny()
        }
        return out
    }

    private fun JsonElement.toAny(): Any? = when (this) {
        is JsonNull -> null
        is JsonPrimitive -> {
            if (isString) content
            else content.toBooleanStrictOrNull()
                ?: content.toLongOrNull()
                ?: content.toDoubleOrNull()
                ?: content
        }
        is JsonArray -> map { it.toAny() }
        is JsonObject -> entries.associate { it.key to it.value.toAny() }
        else -> toString()
    }

    private fun JsonObject.bool(key: String): Boolean? =
        this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()

    private fun outcomeToJson(outcome: DeviceToolOutcome): JsonObject = when (outcome) {
        is DeviceToolOutcome.Ok -> buildJsonObject {
            put("ok", true)
            outcome.message?.let { put("message", it) }
            for ((k, v) in outcome.data) {
                put(k, anyToJson(v))
            }
        }
        is DeviceToolOutcome.Denied -> buildJsonObject {
            put("ok", false)
            put("denied", true)
            put("code", outcome.code)
            put("error", outcome.reason)
        }
        is DeviceToolOutcome.NeedsConfirmation -> buildJsonObject {
            put("ok", false)
            put("needs_confirmation", true)
            put("tool", outcome.toolName)
            put("side_effect", outcome.sideEffect.name)
            put("summary", outcome.summary)
            put("error", "需要用户确认后重试（confirmed=true）")
        }
        is DeviceToolOutcome.Error -> buildJsonObject {
            put("ok", false)
            put("code", outcome.code)
            put("error", outcome.message)
        }
    }

    private fun anyToJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is Map<*, *> -> buildJsonObject {
            for ((k, v) in value) {
                if (k != null) put(k.toString(), anyToJson(v))
            }
        }
        is List<*> -> buildJsonArray {
            for (item in value) add(anyToJson(item))
        }
        else -> JsonPrimitive(value.toString())
    }

    private fun stringProp(desc: String) = buildJsonObject {
        put("type", "string")
        put("description", desc)
    }

    private fun boolProp(desc: String) = buildJsonObject {
        put("type", "boolean")
        put("description", desc)
    }

    private fun intProp(desc: String) = buildJsonObject {
        put("type", "integer")
        put("description", desc)
    }
}
