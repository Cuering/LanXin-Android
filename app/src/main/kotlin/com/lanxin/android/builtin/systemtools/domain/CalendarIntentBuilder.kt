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

package com.lanxin.android.builtin.systemtools.domain

/**
 * 系统日历「创建事件」Intent 规格（纯逻辑，不依赖 Android Runtime）。
 *
 * 对应 `Intent(Intent.ACTION_INSERT).setData(Events.CONTENT_URI)` +
 * `CalendarContract.EXTRA_EVENT_BEGIN_TIME` 等 extras。
 * **不**申请 WRITE_CALENDAR，交给系统日历 App 完成写入。
 *
 * @see android.provider.CalendarContract.Events
 * @see android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME
 */
object CalendarIntentBuilder {

    /** android.content.Intent.ACTION_INSERT */
    const val ACTION_INSERT = "android.intent.action.INSERT"

    /** CalendarContract.Events.CONTENT_URI */
    const val EVENTS_CONTENT_URI = "content://com.android.calendar/events"

    /** CalendarContract.Events.TITLE */
    const val EXTRA_TITLE = "title"

    /** CalendarContract.EXTRA_EVENT_BEGIN_TIME */
    const val EXTRA_BEGIN_TIME = "beginTime"

    /** CalendarContract.EXTRA_EVENT_END_TIME */
    const val EXTRA_END_TIME = "endTime"

    /** CalendarContract.Events.EVENT_LOCATION */
    const val EXTRA_EVENT_LOCATION = "eventLocation"

    /** CalendarContract.Events.DESCRIPTION */
    const val EXTRA_DESCRIPTION = "description"

    /** CalendarContract.EXTRA_EVENT_ALL_DAY */
    const val EXTRA_ALL_DAY = "allDay"

    /**
     * 构建「插入日历事件」Intent 规格。
     *
     * @param title 标题（可空，系统日历 UI 可再编辑）
     * @param startEpochMs 开始时间 epoch ms
     * @param endEpochMs 结束时间；null 则默认 start + 1h
     * @param location 地点
     * @param description 描述
     * @param allDay 是否全天
     */
    fun createEvent(
        title: String?,
        startEpochMs: Long,
        endEpochMs: Long? = null,
        location: String? = null,
        description: String? = null,
        allDay: Boolean = false
    ): IntentLaunchSpec {
        require(startEpochMs > 0L) { "start_epoch_ms 必须 > 0" }
        val end = endEpochMs ?: (startEpochMs + 3_600_000L)
        require(end >= startEpochMs) { "end 必须 ≥ start" }
        val extras = buildMap<String, Any?> {
            put(EXTRA_BEGIN_TIME, startEpochMs)
            put(EXTRA_END_TIME, end)
            put(EXTRA_ALL_DAY, allDay)
            if (!title.isNullOrBlank()) put(EXTRA_TITLE, title.trim())
            if (!location.isNullOrBlank()) put(EXTRA_EVENT_LOCATION, location.trim())
            if (!description.isNullOrBlank()) put(EXTRA_DESCRIPTION, description.trim())
        }
        val label = title?.takeIf { it.isNotBlank() } ?: "(无标题)"
        return IntentLaunchSpec(
            action = ACTION_INSERT,
            dataUri = EVENTS_CONTENT_URI,
            extras = extras,
            description = "打开系统日历创建：$label"
        )
    }

    /** 从 tool args / [CreateCalendarEventRequest] 构建。 */
    fun fromCreateRequest(request: CreateCalendarEventRequest): IntentLaunchSpec =
        createEvent(
            title = request.title,
            startEpochMs = request.startEpochMs,
            endEpochMs = request.endEpochMs,
            location = request.location,
            description = request.description
        )
}
