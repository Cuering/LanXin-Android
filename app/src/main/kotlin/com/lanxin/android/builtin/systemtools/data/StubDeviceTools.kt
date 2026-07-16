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

package com.lanxin.android.builtin.systemtools.data

import com.lanxin.android.builtin.systemtools.domain.AlarmClockGateway
import com.lanxin.android.builtin.systemtools.domain.AlarmClockResult
import com.lanxin.android.builtin.systemtools.domain.AlarmClockTimeResolver
import com.lanxin.android.builtin.systemtools.domain.AlarmIntentBuilder
import com.lanxin.android.builtin.systemtools.domain.CalendarCreateResult
import com.lanxin.android.builtin.systemtools.domain.CalendarEvent
import com.lanxin.android.builtin.systemtools.domain.CalendarGateway
import com.lanxin.android.builtin.systemtools.domain.CalendarListResult
import com.lanxin.android.builtin.systemtools.domain.CalendarQueryParams
import com.lanxin.android.builtin.systemtools.domain.ConfirmationLevel
import com.lanxin.android.builtin.systemtools.domain.CreateCalendarEventRequest
import com.lanxin.android.builtin.systemtools.domain.DeviceCapability
import com.lanxin.android.builtin.systemtools.domain.DevicePermission
import com.lanxin.android.builtin.systemtools.domain.DeviceTool
import com.lanxin.android.builtin.systemtools.domain.DeviceToolIds
import com.lanxin.android.builtin.systemtools.domain.DeviceToolOutcome
import com.lanxin.android.builtin.systemtools.domain.DeviceToolSideEffect
import com.lanxin.android.builtin.systemtools.domain.IntentLaunchResult
import com.lanxin.android.builtin.systemtools.domain.NoteEntry
import com.lanxin.android.builtin.systemtools.domain.NotesCodec
import com.lanxin.android.builtin.systemtools.domain.NotesExportFormat
import com.lanxin.android.builtin.systemtools.domain.NotesImportStrategy
import com.lanxin.android.builtin.systemtools.domain.NotesIoResult
import com.lanxin.android.builtin.systemtools.domain.NotesSafGateway
import com.lanxin.android.builtin.systemtools.domain.NotesStore
import com.lanxin.android.builtin.systemtools.domain.SetAlarmClockRequest
import com.lanxin.android.builtin.systemtools.domain.SystemToolsIntentLauncher
import com.lanxin.android.builtin.systemtools.domain.intArg
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 7.2–7.3 设备工具实现。
 *
 * - [AlarmSetDeviceTool]：默认 setAlarmClock；`mode=intent` 时 startActivity SET_ALARM
 * - [AlarmShowDeviceTool]：startActivity SHOW_ALARMS
 * - [CalendarListUpcomingDeviceTool]：CalendarContract / stub Gateway
 * - [CalendarCreateEventDeviceTool]：优先 INSERT Intent；`mode=stub` 内存写入
 * - Notes：Room 持久化 CRUD + SAF 导出/导入/分享
 */
@Singleton
class AlarmSetDeviceTool @Inject constructor(
    private val alarmClock: AlarmClockGateway,
    private val intentLauncher: SystemToolsIntentLauncher
) : DeviceTool {
    override val name = DeviceToolIds.ALARM_SET
    override val description =
        "设置闹钟：默认 AlarmManager.setAlarmClock；mode=intent 时 startActivity SET_ALARM"
    override val capability = DeviceCapability.ALARM
    override val permissions = listOf(DevicePermission.NONE)
    override val sideEffect = DeviceToolSideEffect.LAUNCH_INTENT
    override val confirmationLevel = ConfirmationLevel.CONFIRM

    override suspend fun invoke(args: Map<String, Any?>, confirmed: Boolean): DeviceToolOutcome {
        val mode = args["mode"]?.toString()?.lowercase()?.trim().orEmpty()
        return if (mode == "intent" || mode == "alarm_clock_intent") {
            invokeIntentMode(args)
        } else {
            invokeSetAlarmClock(args)
        }
    }

    private fun invokeIntentMode(args: Map<String, Any?>): DeviceToolOutcome {
        return try {
            val spec = AlarmIntentBuilder.fromSetAlarmArgs(args)
            when (val result = intentLauncher.launch(spec.toLaunchSpec())) {
                is IntentLaunchResult.Ok -> DeviceToolOutcome.Ok(
                    data = mapOf(
                        "ok" to true,
                        "mode" to "intent",
                        "action" to result.action,
                        "extras" to spec.extras,
                        "description" to result.description.ifBlank { spec.description },
                        "launched" to result.launched,
                        "resolved_activity" to result.resolvedActivity
                    ),
                    message = result.description.ifBlank { spec.description }
                )
                is IntentLaunchResult.ActivityNotFound -> DeviceToolOutcome.Error(
                    message = result.message,
                    code = "activity_not_found"
                )
                is IntentLaunchResult.Error -> DeviceToolOutcome.Error(
                    message = result.message,
                    code = result.code
                )
            }
        } catch (e: IllegalArgumentException) {
            DeviceToolOutcome.Error(message = e.message ?: "invalid args", code = "invalid_args")
        }
    }

    private fun invokeSetAlarmClock(args: Map<String, Any?>): DeviceToolOutcome {
        return try {
            val triggerExplicit = (args["trigger_at_epoch_ms"] as? Number)?.toLong()
                ?: args["trigger_at_epoch_ms"]?.toString()?.toLongOrNull()
            val trigger = if (triggerExplicit != null) {
                triggerExplicit
            } else {
                val hour = args.intArg("hour")
                    ?: return DeviceToolOutcome.Error("hour 或 trigger_at_epoch_ms 必填", "invalid_args")
                val minutes = args.intArg("minutes")
                    ?: args.intArg("minute")
                    ?: return DeviceToolOutcome.Error("minutes 必填 (0-59)", "invalid_args")
                AlarmClockTimeResolver.nextTriggerEpochMs(hour, minutes)
            }
            val message = args["message"]?.toString()
            val result = alarmClock.setAlarmClock(
                SetAlarmClockRequest(
                    triggerAtEpochMs = trigger,
                    message = message
                )
            )
            when (result) {
                is AlarmClockResult.Ok -> DeviceToolOutcome.Ok(
                    data = mapOf(
                        "ok" to true,
                        "mode" to "set_alarm_clock",
                        "method" to result.method,
                        "trigger_at_epoch_ms" to result.triggerAtEpochMs,
                        "request_code" to result.requestCode,
                        "message" to result.message,
                        "scheduled" to true
                    ),
                    message = "已设置精确闹钟 @ ${result.triggerAtEpochMs}"
                )
                is AlarmClockResult.NeedsExactAlarmPermission -> DeviceToolOutcome.Denied(
                    reason = result.message,
                    code = "needs_exact_alarm_permission"
                )
                is AlarmClockResult.Error -> DeviceToolOutcome.Error(
                    message = result.message,
                    code = result.code
                )
            }
        } catch (e: IllegalArgumentException) {
            DeviceToolOutcome.Error(message = e.message ?: "invalid args", code = "invalid_args")
        }
    }
}

@Singleton
class AlarmShowDeviceTool @Inject constructor(
    private val intentLauncher: SystemToolsIntentLauncher
) : DeviceTool {
    override val name = DeviceToolIds.ALARM_SHOW
    override val description = "打开系统闹钟列表（AlarmClock.ACTION_SHOW_ALARMS + startActivity）"
    override val capability = DeviceCapability.ALARM
    override val permissions = listOf(DevicePermission.NONE)
    override val sideEffect = DeviceToolSideEffect.LAUNCH_INTENT
    override val confirmationLevel = ConfirmationLevel.NONE

    override suspend fun invoke(args: Map<String, Any?>, confirmed: Boolean): DeviceToolOutcome {
        val spec = AlarmIntentBuilder.showAlarms()
        return when (val result = intentLauncher.launch(spec.toLaunchSpec())) {
            is IntentLaunchResult.Ok -> DeviceToolOutcome.Ok(
                data = mapOf(
                    "ok" to true,
                    "action" to result.action,
                    "launched" to result.launched,
                    "resolved_activity" to result.resolvedActivity,
                    "description" to result.description.ifBlank { spec.description }
                ),
                message = result.description.ifBlank { spec.description }
            )
            is IntentLaunchResult.ActivityNotFound -> DeviceToolOutcome.Error(
                message = result.message,
                code = "activity_not_found"
            )
            is IntentLaunchResult.Error -> DeviceToolOutcome.Error(
                message = result.message,
                code = result.code
            )
        }
    }
}

@Singleton
class CalendarListUpcomingDeviceTool @Inject constructor(
    private val gateway: CalendarGateway
) : DeviceTool {
    override val name = DeviceToolIds.CALENDAR_LIST_UPCOMING
    override val description =
        "列出接下来 N 天内的日历事件（CalendarContract.Instances；无权限返回清晰提示）"
    override val capability = DeviceCapability.CALENDAR
    override val permissions = listOf(DevicePermission.READ_CALENDAR)
    override val sideEffect = DeviceToolSideEffect.READ
    override val confirmationLevel = ConfirmationLevel.NONE

    override suspend fun invoke(args: Map<String, Any?>, confirmed: Boolean): DeviceToolOutcome {
        val limit = args.intArg("limit")
        val days = args.intArg("days")
        val after = (args["after_epoch_ms"] as? Number)?.toLong()
            ?: args["after_epoch_ms"]?.toString()?.toLongOrNull()
            ?: System.currentTimeMillis()
        return when (
            val result = gateway.listUpcoming(
                limit = CalendarQueryParams.normalizeLimit(limit),
                afterEpochMs = after,
                days = CalendarQueryParams.normalizeDays(days)
            )
        ) {
            is CalendarListResult.Ok -> DeviceToolOutcome.Ok(
                data = mapOf(
                    "ok" to true,
                    "count" to result.events.size,
                    "days" to CalendarQueryParams.normalizeDays(days),
                    "events" to result.events.map { it.toMap() }
                )
            )
            is CalendarListResult.PermissionDenied -> DeviceToolOutcome.Denied(
                reason = result.message,
                code = "permission_denied_read_calendar"
            )
            is CalendarListResult.Error -> DeviceToolOutcome.Error(
                message = result.message,
                code = "calendar_query_error"
            )
        }
    }
}

@Singleton
class CalendarCreateEventDeviceTool @Inject constructor(
    private val gateway: CalendarGateway,
    private val stubGateway: StubCalendarGateway
) : DeviceTool {
    override val name = DeviceToolIds.CALENDAR_CREATE_EVENT
    override val description =
        "创建日历事件：默认系统日历 INSERT Intent（少权限）；mode=stub 内存写入"
    override val capability = DeviceCapability.CALENDAR
    override val permissions = listOf(DevicePermission.NONE)
    override val sideEffect = DeviceToolSideEffect.WRITE
    override val confirmationLevel = ConfirmationLevel.CONFIRM

    override suspend fun invoke(args: Map<String, Any?>, confirmed: Boolean): DeviceToolOutcome {
        val title = args["title"]?.toString()?.trim().orEmpty()
        val start = (args["start_epoch_ms"] as? Number)?.toLong()
            ?: args["start_epoch_ms"]?.toString()?.toLongOrNull()
            ?: return DeviceToolOutcome.Error("start_epoch_ms 必填", "invalid_args")
        val end = (args["end_epoch_ms"] as? Number)?.toLong()
            ?: args["end_epoch_ms"]?.toString()?.toLongOrNull()
            ?: (start + 3_600_000L)
        val request = CreateCalendarEventRequest(
            title = title.ifBlank { "未命名事件" },
            startEpochMs = start,
            endEpochMs = end,
            location = args["location"]?.toString(),
            description = args["description"]?.toString()
        )
        val mode = args["mode"]?.toString()?.lowercase()?.trim().orEmpty()
        return try {
            val result = if (mode == "stub") {
                stubGateway.create(request)
            } else {
                gateway.create(request)
            }
            mapCreateResult(result)
        } catch (e: IllegalArgumentException) {
            DeviceToolOutcome.Error(e.message ?: "create failed", "invalid_args")
        }
    }

    private fun mapCreateResult(result: CalendarCreateResult): DeviceToolOutcome = when (result) {
        is CalendarCreateResult.Created -> DeviceToolOutcome.Ok(
            data = mapOf(
                "ok" to true,
                "mode" to if (result.stub) "stub" else "provider",
                "stub" to result.stub,
                "event" to result.event.toMap()
            ),
            message = "已创建事件 ${result.event.id}"
        )
        is CalendarCreateResult.IntentLaunched -> DeviceToolOutcome.Ok(
            data = mapOf(
                "ok" to true,
                "mode" to "intent",
                "launched" to true,
                "action" to result.action,
                "resolved_activity" to result.resolvedActivity,
                "title" to result.request.title,
                "start_epoch_ms" to result.request.startEpochMs,
                "end_epoch_ms" to result.request.endEpochMs
            ),
            message = result.description
        )
        is CalendarCreateResult.ActivityNotFound -> DeviceToolOutcome.Error(
            message = result.message,
            code = "activity_not_found"
        )
        is CalendarCreateResult.Error -> DeviceToolOutcome.Error(
            message = result.message,
            code = result.code
        )
    }
}

@Singleton
class NoteCreateDeviceTool @Inject constructor(
    private val store: NotesStore
) : DeviceTool {
    override val name = DeviceToolIds.NOTE_CREATE
    override val description = "创建应用内笔记（Room 私有库）"
    override val capability = DeviceCapability.NOTES
    override val permissions = listOf(DevicePermission.APP_PRIVATE_STORAGE)
    override val sideEffect = DeviceToolSideEffect.WRITE
    override val confirmationLevel = ConfirmationLevel.CONFIRM

    override suspend fun invoke(args: Map<String, Any?>, confirmed: Boolean): DeviceToolOutcome {
        return try {
            val note = store.create(
                title = args["title"]?.toString().orEmpty(),
                body = args["body"]?.toString().orEmpty()
            )
            DeviceToolOutcome.Ok(
                data = mapOf(
                    "ok" to true,
                    "persisted" to true,
                    "id" to note.id,
                    "title" to note.title,
                    "body" to note.body,
                    "updated_at" to note.updatedAtEpochMs
                ),
                message = "已创建笔记 ${note.id}"
            )
        } catch (e: IllegalArgumentException) {
            DeviceToolOutcome.Error(e.message ?: "create failed", "invalid_args")
        }
    }
}

@Singleton
class NoteListDeviceTool @Inject constructor(
    private val store: NotesStore
) : DeviceTool {
    override val name = DeviceToolIds.NOTE_LIST
    override val description = "列出应用内笔记（按更新时间倒序）"
    override val capability = DeviceCapability.NOTES
    override val permissions = listOf(DevicePermission.APP_PRIVATE_STORAGE)
    override val sideEffect = DeviceToolSideEffect.READ
    override val confirmationLevel = ConfirmationLevel.NONE

    override suspend fun invoke(args: Map<String, Any?>, confirmed: Boolean): DeviceToolOutcome {
        val limit = args.intArg("limit") ?: 50
        val list = store.list(limit)
        return DeviceToolOutcome.Ok(
            data = mapOf(
                "ok" to true,
                "persisted" to true,
                "count" to list.size,
                "total" to store.count(),
                "notes" to list.map { it.toMap() }
            )
        )
    }
}

@Singleton
class NoteAppendDeviceTool @Inject constructor(
    private val store: NotesStore
) : DeviceTool {
    override val name = DeviceToolIds.NOTE_APPEND
    override val description = "向已有笔记追加文本"
    override val capability = DeviceCapability.NOTES
    override val permissions = listOf(DevicePermission.APP_PRIVATE_STORAGE)
    override val sideEffect = DeviceToolSideEffect.WRITE
    override val confirmationLevel = ConfirmationLevel.CONFIRM

    override suspend fun invoke(args: Map<String, Any?>, confirmed: Boolean): DeviceToolOutcome {
        val id = args["id"]?.toString()
            ?: return DeviceToolOutcome.Error("id 必填", "invalid_args")
        val text = args["text"]?.toString()
            ?: return DeviceToolOutcome.Error("text 必填", "invalid_args")
        return try {
            val note = store.append(id, text)
            DeviceToolOutcome.Ok(
                data = mapOf(
                    "ok" to true,
                    "persisted" to true,
                    "id" to note.id,
                    "body" to note.body,
                    "updated_at" to note.updatedAtEpochMs
                )
            )
        } catch (e: IllegalArgumentException) {
            DeviceToolOutcome.Error(e.message ?: "append failed", "invalid_args")
        }
    }
}

@Singleton
class NoteUpdateDeviceTool @Inject constructor(
    private val store: NotesStore
) : DeviceTool {
    override val name = DeviceToolIds.NOTE_UPDATE
    override val description = "更新笔记标题和/或正文（覆盖写）"
    override val capability = DeviceCapability.NOTES
    override val permissions = listOf(DevicePermission.APP_PRIVATE_STORAGE)
    override val sideEffect = DeviceToolSideEffect.WRITE
    override val confirmationLevel = ConfirmationLevel.CONFIRM

    override suspend fun invoke(args: Map<String, Any?>, confirmed: Boolean): DeviceToolOutcome {
        val id = args["id"]?.toString()
            ?: return DeviceToolOutcome.Error("id 必填", "invalid_args")
        val title = if (args.containsKey("title")) args["title"]?.toString() else null
        val body = if (args.containsKey("body")) args["body"]?.toString() else null
        return try {
            val note = store.update(id, title = title, body = body)
            DeviceToolOutcome.Ok(
                data = mapOf(
                    "ok" to true,
                    "persisted" to true,
                    "note" to note.toMap()
                ),
                message = "已更新笔记 $id"
            )
        } catch (e: IllegalArgumentException) {
            DeviceToolOutcome.Error(e.message ?: "update failed", "invalid_args")
        }
    }
}

@Singleton
class NoteDeleteDeviceTool @Inject constructor(
    private val store: NotesStore
) : DeviceTool {
    override val name = DeviceToolIds.NOTE_DELETE
    override val description = "删除应用内笔记（高危，需确认）"
    override val capability = DeviceCapability.NOTES
    override val permissions = listOf(DevicePermission.APP_PRIVATE_STORAGE)
    override val sideEffect = DeviceToolSideEffect.DELETE
    override val confirmationLevel = ConfirmationLevel.EXPLICIT_APPROVE

    override suspend fun invoke(args: Map<String, Any?>, confirmed: Boolean): DeviceToolOutcome {
        val id = args["id"]?.toString()
            ?: return DeviceToolOutcome.Error("id 必填", "invalid_args")
        return try {
            val ok = store.delete(id)
            if (!ok) {
                DeviceToolOutcome.Error("笔记不存在: $id", "not_found")
            } else {
                DeviceToolOutcome.Ok(
                    data = mapOf(
                        "ok" to true,
                        "deleted" to true,
                        "id" to id,
                        "remaining" to store.count()
                    ),
                    message = "已删除笔记 $id"
                )
            }
        } catch (e: Exception) {
            DeviceToolOutcome.Error(e.message ?: "delete failed", "delete_error")
        }
    }
}

@Singleton
class NoteExportDeviceTool @Inject constructor(
    private val store: NotesStore,
    private val saf: NotesSafGateway
) : DeviceTool {
    override val name = DeviceToolIds.NOTE_EXPORT
    override val description =
        "导出笔记：format=json|markdown；mode=share 分享，mode=saf 写入 uri（SAF CreateDocument）"
    override val capability = DeviceCapability.NOTES
    override val permissions = listOf(DevicePermission.APP_PRIVATE_STORAGE, DevicePermission.SAF_TREE)
    override val sideEffect = DeviceToolSideEffect.WRITE
    override val confirmationLevel = ConfirmationLevel.CONFIRM

    override suspend fun invoke(args: Map<String, Any?>, confirmed: Boolean): DeviceToolOutcome {
        val formatStr = args["format"]?.toString()?.lowercase()?.trim().orEmpty().ifBlank { "json" }
        val format = when (formatStr) {
            "json" -> NotesExportFormat.JSON
            "md", "markdown", "text/markdown" -> NotesExportFormat.MARKDOWN
            else -> return DeviceToolOutcome.Error("format 仅支持 json / markdown", "invalid_args")
        }
        val mode = args["mode"]?.toString()?.lowercase()?.trim().orEmpty().ifBlank { "share" }
        val limit = args.intArg("limit") ?: 500
        val notes = store.list(limit)
        val payload = when (format) {
            NotesExportFormat.JSON -> NotesCodec.toJsonBundle(notes)
            NotesExportFormat.MARKDOWN -> NotesCodec.toMarkdown(notes)
        }
        val mime = when (format) {
            NotesExportFormat.JSON -> "application/json"
            NotesExportFormat.MARKDOWN -> "text/markdown"
        }
        return when (mode) {
            "share", "intent" -> {
                when (val r = saf.shareText(payload, mime, chooserTitle = "导出兰心笔记")) {
                    is NotesIoResult.Ok -> DeviceToolOutcome.Ok(
                        data = mapOf(
                            "ok" to true,
                            "mode" to "share",
                            "format" to format.name.lowercase(),
                            "count" to notes.size,
                            "bytes" to r.bytes
                        ),
                        message = r.message
                    )
                    is NotesIoResult.Error -> DeviceToolOutcome.Error(r.message, r.code)
                }
            }
            "saf", "uri", "file" -> {
                val uri = args["uri"]?.toString()?.trim().orEmpty()
                if (uri.isEmpty()) {
                    return DeviceToolOutcome.Error(
                        "mode=saf 需要 uri（设置页 CreateDocument 选择后传入）",
                        "uri_required"
                    )
                }
                when (val r = saf.writeText(uri, payload, mime)) {
                    is NotesIoResult.Ok -> DeviceToolOutcome.Ok(
                        data = mapOf(
                            "ok" to true,
                            "mode" to "saf",
                            "format" to format.name.lowercase(),
                            "count" to notes.size,
                            "bytes" to r.bytes,
                            "uri" to uri
                        ),
                        message = r.message
                    )
                    is NotesIoResult.Error -> DeviceToolOutcome.Error(r.message, r.code)
                }
            }
            "preview" -> DeviceToolOutcome.Ok(
                data = mapOf(
                    "ok" to true,
                    "mode" to "preview",
                    "format" to format.name.lowercase(),
                    "count" to notes.size,
                    "preview" to payload.take(2000),
                    "truncated" to (payload.length > 2000)
                ),
                message = "预览前 ${minOf(2000, payload.length)} 字符"
            )
            else -> DeviceToolOutcome.Error("mode 仅支持 share / saf / preview", "invalid_args")
        }
    }
}

@Singleton
class NoteImportDeviceTool @Inject constructor(
    private val store: NotesStore,
    private val saf: NotesSafGateway
) : DeviceTool {
    override val name = DeviceToolIds.NOTE_IMPORT
    override val description =
        "从 SAF uri 或 json_text 导入笔记；strategy=merge|replace；需 confirmed"
    override val capability = DeviceCapability.NOTES
    override val permissions = listOf(DevicePermission.APP_PRIVATE_STORAGE, DevicePermission.SAF_TREE)
    override val sideEffect = DeviceToolSideEffect.WRITE
    override val confirmationLevel = ConfirmationLevel.CONFIRM

    override suspend fun invoke(args: Map<String, Any?>, confirmed: Boolean): DeviceToolOutcome {
        val strategyStr = args["strategy"]?.toString()?.lowercase()?.trim().orEmpty().ifBlank { "merge" }
        val strategy = when (strategyStr) {
            "merge" -> NotesImportStrategy.MERGE
            "replace" -> NotesImportStrategy.REPLACE
            else -> return DeviceToolOutcome.Error("strategy 仅支持 merge / replace", "invalid_args")
        }
        val jsonText = args["json_text"]?.toString()
        val uri = args["uri"]?.toString()?.trim().orEmpty()
        val raw = when {
            !jsonText.isNullOrBlank() -> jsonText
            uri.isNotEmpty() -> {
                when (val r = saf.readText(uri)) {
                    is NotesIoResult.Ok -> r.message
                    is NotesIoResult.Error -> return DeviceToolOutcome.Error(r.message, r.code)
                }
            }
            else -> return DeviceToolOutcome.Error("需要 uri 或 json_text", "invalid_args")
        }
        return try {
            val parsed = NotesCodec.parseJsonBundle(raw)
            if (strategy == NotesImportStrategy.REPLACE) {
                store.clearAll()
            }
            val written = store.upsertAll(parsed)
            DeviceToolOutcome.Ok(
                data = mapOf(
                    "ok" to true,
                    "strategy" to strategy.name.lowercase(),
                    "parsed" to parsed.size,
                    "written" to written,
                    "total" to store.count()
                ),
                message = "已导入 $written 条笔记"
            )
        } catch (e: IllegalArgumentException) {
            DeviceToolOutcome.Error(e.message ?: "import failed", "invalid_args")
        } catch (e: Exception) {
            DeviceToolOutcome.Error(e.message ?: e.toString(), "import_error")
        }
    }
}

private fun NoteEntry.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "title" to title,
    "body" to body,
    "updated_at" to updatedAtEpochMs
)

private fun CalendarEvent.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "title" to title,
    "start_epoch_ms" to startEpochMs,
    "end_epoch_ms" to endEpochMs,
    "location" to location,
    "calendar_id" to calendarId
)

/**
 * 注册表：收集设备工具，供 Plugin / Gate 使用。
 */
@Singleton
class DeviceToolRegistry @Inject constructor(
    alarmSet: AlarmSetDeviceTool,
    alarmShow: AlarmShowDeviceTool,
    calendarList: CalendarListUpcomingDeviceTool,
    calendarCreate: CalendarCreateEventDeviceTool,
    noteCreate: NoteCreateDeviceTool,
    noteList: NoteListDeviceTool,
    noteAppend: NoteAppendDeviceTool,
    noteUpdate: NoteUpdateDeviceTool,
    noteDelete: NoteDeleteDeviceTool,
    noteExport: NoteExportDeviceTool,
    noteImport: NoteImportDeviceTool
) {
    private val tools: Map<String, DeviceTool> = listOf(
        alarmSet,
        alarmShow,
        calendarList,
        calendarCreate,
        noteCreate,
        noteList,
        noteAppend,
        noteUpdate,
        noteDelete,
        noteExport,
        noteImport
    ).associateBy { it.name }

    fun get(name: String): DeviceTool? = tools[name]

    fun all(): List<DeviceTool> = tools.values.toList()

    fun names(): Set<String> = tools.keys
}
