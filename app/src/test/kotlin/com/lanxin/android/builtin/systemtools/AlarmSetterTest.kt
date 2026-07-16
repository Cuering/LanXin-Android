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

import com.lanxin.android.builtin.systemtools.data.AlarmSetDeviceTool
import com.lanxin.android.builtin.systemtools.domain.AlarmClockGateway
import com.lanxin.android.builtin.systemtools.domain.AlarmClockResult
import com.lanxin.android.builtin.systemtools.domain.AlarmClockTimeResolver
import com.lanxin.android.builtin.systemtools.domain.AlarmIntentBuilder
import com.lanxin.android.builtin.systemtools.domain.DeviceToolIds
import com.lanxin.android.builtin.systemtools.domain.DeviceToolOutcome
import com.lanxin.android.builtin.systemtools.domain.SetAlarmClockRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSetterTest {

    private class FakeAlarmClock(
        private val canExact: Boolean = true,
        private val onSet: (SetAlarmClockRequest) -> AlarmClockResult = {
            AlarmClockResult.Ok(
                triggerAtEpochMs = it.triggerAtEpochMs,
                requestCode = it.requestCode ?: 1,
                message = it.message
            )
        }
    ) : AlarmClockGateway {
        var lastRequest: SetAlarmClockRequest? = null
        override fun canScheduleExactAlarms(): Boolean = canExact
        override fun setAlarmClock(request: SetAlarmClockRequest): AlarmClockResult {
            lastRequest = request
            return onSet(request)
        }
    }

    @Test
    fun `tool name is stable`() {
        val tool = AlarmSetDeviceTool(FakeAlarmClock())
        assertEquals(DeviceToolIds.ALARM_SET, tool.name)
    }

    @Test
    fun `setAlarmClock schedules with hour minutes`() = runBlocking {
        val fake = FakeAlarmClock()
        val tool = AlarmSetDeviceTool(fake)
        val outcome = tool.invoke(
            mapOf("hour" to 8, "minutes" to 30, "message" to "起床"),
            confirmed = true
        )
        assertTrue(outcome is DeviceToolOutcome.Ok)
        val ok = outcome as DeviceToolOutcome.Ok
        assertEquals("set_alarm_clock", ok.data["mode"])
        assertEquals(true, ok.data["scheduled"])
        assertEquals("起床", ok.data["message"])
        assertTrue(fake.lastRequest!!.triggerAtEpochMs > System.currentTimeMillis())
    }

    @Test
    fun `setAlarmClock with absolute trigger`() = runBlocking {
        val fake = FakeAlarmClock()
        val tool = AlarmSetDeviceTool(fake)
        val trigger = System.currentTimeMillis() + 60_000L
        val outcome = tool.invoke(
            mapOf("trigger_at_epoch_ms" to trigger, "message" to "药"),
            confirmed = true
        ) as DeviceToolOutcome.Ok
        assertEquals(trigger, outcome.data["trigger_at_epoch_ms"])
        assertEquals(trigger, fake.lastRequest!!.triggerAtEpochMs)
    }

    @Test
    fun `missing exact alarm permission returns Denied`() = runBlocking {
        val fake = FakeAlarmClock(
            canExact = false,
            onSet = { AlarmClockResult.NeedsExactAlarmPermission() }
        )
        val tool = AlarmSetDeviceTool(fake)
        val outcome = tool.invoke(
            mapOf("hour" to 9, "minutes" to 0),
            confirmed = true
        )
        assertTrue(outcome is DeviceToolOutcome.Denied)
        assertEquals(
            "needs_exact_alarm_permission",
            (outcome as DeviceToolOutcome.Denied).code
        )
    }

    @Test
    fun `intent mode builds AlarmClock spec without gateway`() = runBlocking {
        val fake = FakeAlarmClock(
            onSet = { error("should not call setAlarmClock in intent mode") }
        )
        val tool = AlarmSetDeviceTool(fake)
        val outcome = tool.invoke(
            mapOf(
                "mode" to "intent",
                "hour" to 7,
                "minutes" to 15,
                "message" to "Intent闹钟"
            ),
            confirmed = true
        ) as DeviceToolOutcome.Ok
        assertEquals("intent", outcome.data["mode"])
        assertEquals(AlarmIntentBuilder.ACTION_SET_ALARM, outcome.data["action"])
        assertEquals(null, fake.lastRequest)
    }

    @Test
    fun `nextTrigger rolls to tomorrow when past`() {
        val zone = "UTC"
        // 2020-01-01 10:00 UTC
        val now = 1_577_876_400_000L
        val trigger = AlarmClockTimeResolver.nextTriggerEpochMs(
            hour = 9,
            minutes = 0,
            nowEpochMs = now,
            timeZoneId = zone
        )
        // should be 2020-01-02 09:00 UTC
        assertTrue(trigger > now)
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(zone)).apply {
            timeInMillis = trigger
        }
        assertEquals(9, cal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(java.util.Calendar.MINUTE))
        assertEquals(2, cal.get(java.util.Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `nextTrigger same day when future`() {
        val zone = "UTC"
        val now = 1_577_876_400_000L // 10:00
        val trigger = AlarmClockTimeResolver.nextTriggerEpochMs(
            hour = 11,
            minutes = 30,
            nowEpochMs = now,
            timeZoneId = zone
        )
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(zone)).apply {
            timeInMillis = trigger
        }
        assertEquals(1, cal.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(11, cal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(java.util.Calendar.MINUTE))
    }

    @Test
    fun `missing hour without trigger returns error`() = runBlocking {
        val tool = AlarmSetDeviceTool(FakeAlarmClock())
        val outcome = tool.invoke(mapOf("minutes" to 0), confirmed = true)
        assertTrue(outcome is DeviceToolOutcome.Error)
        assertEquals("invalid_args", (outcome as DeviceToolOutcome.Error).code)
    }
}
