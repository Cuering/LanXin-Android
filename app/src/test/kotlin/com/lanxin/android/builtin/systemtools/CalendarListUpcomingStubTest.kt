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

import com.lanxin.android.builtin.systemtools.data.CalendarCreateEventDeviceTool
import com.lanxin.android.builtin.systemtools.data.CalendarListUpcomingDeviceTool
import com.lanxin.android.builtin.systemtools.data.StubCalendarGateway
import com.lanxin.android.builtin.systemtools.domain.CalendarEvent
import com.lanxin.android.builtin.systemtools.domain.CalendarGateway
import com.lanxin.android.builtin.systemtools.domain.CalendarListResult
import com.lanxin.android.builtin.systemtools.domain.CalendarQueryParams
import com.lanxin.android.builtin.systemtools.domain.CreateCalendarEventRequest
import com.lanxin.android.builtin.systemtools.domain.DeviceToolIds
import com.lanxin.android.builtin.systemtools.domain.DeviceToolOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalendarListUpcomingStubTest {

    private lateinit var gateway: StubCalendarGateway
    private lateinit var listTool: CalendarListUpcomingDeviceTool
    private lateinit var createTool: CalendarCreateEventDeviceTool

    @Before
    fun setUp() {
        gateway = StubCalendarGateway()
        gateway.resetForTest()
        listTool = CalendarListUpcomingDeviceTool(gateway)
        createTool = CalendarCreateEventDeviceTool(gateway)
    }

    @Test
    fun `tool name is stable`() {
        assertEquals(DeviceToolIds.CALENDAR_LIST_UPCOMING, listTool.name)
    }

    @Test
    fun `listUpcoming returns stub sample`() = runBlocking {
        val outcome = listTool.invoke(emptyMap())
        assertTrue(outcome is DeviceToolOutcome.Ok)
        val ok = outcome as DeviceToolOutcome.Ok
        assertEquals(true, ok.data["ok"])
        assertTrue((ok.data["count"] as Int) >= 1)
        @Suppress("UNCHECKED_CAST")
        val events = ok.data["events"] as List<Map<String, Any?>>
        assertTrue(events.any { it["title"].toString().contains("示例") })
    }

    @Test
    fun `create then list includes new event`() = runBlocking {
        val start = System.currentTimeMillis() + 86_400_000L
        val created = createTool.invoke(
            mapOf(
                "title" to "Phase7 单测",
                "start_epoch_ms" to start,
                "end_epoch_ms" to start + 3_600_000L
            ),
            confirmed = true
        )
        assertTrue(created is DeviceToolOutcome.Ok)
        val list = listTool.invoke(mapOf("limit" to 50)) as DeviceToolOutcome.Ok

        @Suppress("UNCHECKED_CAST")
        val events = list.data["events"] as List<Map<String, Any?>>
        assertTrue(events.any { it["title"] == "Phase7 单测" })
    }

    @Test
    fun `list respects limit`() = runBlocking {
        val start = System.currentTimeMillis() + 10_000L
        repeat(5) { i ->
            createTool.invoke(
                mapOf(
                    "title" to "e$i",
                    "start_epoch_ms" to start + i * 1000L,
                    "end_epoch_ms" to start + i * 1000L + 60_000L
                )
            )
        }
        val list = listTool.invoke(mapOf("limit" to 2)) as DeviceToolOutcome.Ok
        assertEquals(2, list.data["count"])
    }

    @Test
    fun `permission denied maps to Denied outcome`() = runBlocking {
        val deniedGateway = object : CalendarGateway {
            override fun listUpcoming(limit: Int, afterEpochMs: Long, days: Int) =
                CalendarListResult.PermissionDenied("未授予 READ_CALENDAR")

            override fun create(request: CreateCalendarEventRequest): CalendarEvent {
                error("not used")
            }
        }
        val tool = CalendarListUpcomingDeviceTool(deniedGateway)
        val outcome = tool.invoke(emptyMap())
        assertTrue(outcome is DeviceToolOutcome.Denied)
        val denied = outcome as DeviceToolOutcome.Denied
        assertEquals("permission_denied_read_calendar", denied.code)
        assertTrue(denied.reason.contains("READ_CALENDAR"))
    }

    @Test
    fun `query params normalize days and limit`() {
        assertEquals(7, CalendarQueryParams.normalizeDays(null))
        assertEquals(1, CalendarQueryParams.normalizeDays(0))
        assertEquals(30, CalendarQueryParams.normalizeDays(99))
        assertEquals(10, CalendarQueryParams.normalizeLimit(null))
        assertEquals(1, CalendarQueryParams.normalizeLimit(0))
        assertEquals(100, CalendarQueryParams.normalizeLimit(500))
        val now = 1_000_000L
        assertEquals(now + 7 * CalendarQueryParams.DAY_MS, CalendarQueryParams.windowEndEpochMs(now, 7))
    }
}
