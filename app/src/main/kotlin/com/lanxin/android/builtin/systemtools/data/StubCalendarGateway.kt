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

import com.lanxin.android.builtin.systemtools.domain.CalendarEvent
import com.lanxin.android.builtin.systemtools.domain.CreateCalendarEventRequest
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 日历 Gateway stub：内存事件列表，不访问 CalendarContract。
 * M2 可替换为 `CalendarContractGateway`（READ/WRITE_CALENDAR 或 Intent）。
 */
@Singleton
class StubCalendarGateway @Inject constructor() {

    private val events = CopyOnWriteArrayList<CalendarEvent>()

    init {
        // 演示用样例，便于 list 非空
        val now = System.currentTimeMillis()
        events.add(
            CalendarEvent(
                id = "stub-sample-1",
                title = "（stub）示例会议",
                startEpochMs = now + 3_600_000L,
                endEpochMs = now + 7_200_000L,
                location = "stub-room"
            )
        )
    }

    fun listUpcoming(limit: Int = 10, afterEpochMs: Long = System.currentTimeMillis()): List<CalendarEvent> {
        val n = limit.coerceIn(1, 100)
        return events
            .filter { it.endEpochMs >= afterEpochMs }
            .sortedBy { it.startEpochMs }
            .take(n)
    }

    fun create(request: CreateCalendarEventRequest): CalendarEvent {
        require(request.title.isNotBlank()) { "title 不能为空" }
        require(request.endEpochMs >= request.startEpochMs) { "end 必须 ≥ start" }
        val event = CalendarEvent(
            id = "stub-${UUID.randomUUID()}",
            title = request.title.trim(),
            startEpochMs = request.startEpochMs,
            endEpochMs = request.endEpochMs,
            location = request.location
        )
        events.add(event)
        return event
    }

    /** 单测用：清空并重置样例。 */
    fun resetForTest() {
        events.clear()
        val now = System.currentTimeMillis()
        events.add(
            CalendarEvent(
                id = "stub-sample-1",
                title = "（stub）示例会议",
                startEpochMs = now + 3_600_000L,
                endEpochMs = now + 7_200_000L,
                location = "stub-room"
            )
        )
    }
}
