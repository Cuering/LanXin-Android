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
import com.lanxin.android.builtin.systemtools.data.AlarmShowDeviceTool
import com.lanxin.android.builtin.systemtools.domain.AlarmClockGateway
import com.lanxin.android.builtin.systemtools.domain.AlarmClockResult
import com.lanxin.android.builtin.systemtools.domain.AlarmClockTimeResolver
import com.lanxin.android.builtin.systemtools.domain.AlarmIntentBuilder
import com.lanxin.android.builtin.systemtools.domain.DeviceToolIds
import com.lanxin.android.builtin.systemtools.domain.DeviceToolOutcome
import com.lanxin.android.builtin.systemtools.domain.IntentLaunchResult
import com.lanxin.android.builtin.systemtools.domain.IntentLaunchSpec
import com.lanxin.android.builtin.systemtools.domain.SetAlarmClockRequest
import com.lanxin.android.builtin.systemtools.domain.SystemToolsIntentLauncher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private class RecordingLauncher(
        private val result: (IntentLaunchSpec) -> IntentLaunchResult = {
            IntentLaunchResult.Ok(
                action = it.action,
                launched = true,
                resolvedActivity = "fake/.Alarm",
                description = it.description
            )
        }
    ) : SystemToolsIntentLauncher {
        var lastSpec: IntentLaunchSpec? = null
        var launchCount = 0
        override fun launch(spec: IntentLaunchSpec): IntentLaunchResult {
            lastSpec = spec
            launchCount++
            return result(spec)
        }
    }

    @Test
    fun `tool name is stable`() {
        val tool = AlarmSetDeviceTool(FakeAlarmClock(), RecordingLauncher())
        assertEquals(DeviceToolIds.ALARM_SET, tool.name)
    }

    @Test
    fun `setAlarmClock schedules with hour minutes`() = runBlocking {
        val fake = FakeAlarmClock()
        val tool = AlarmSetDeviceTool(fake, RecordingLauncher())
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
        val tool = AlarmSetDeviceTool(fake, RecordingLauncher())
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
        val tool = AlarmSetDeviceTool(fake, RecordingLauncher())
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
    fun `intent mode launches SET_ALARM via launcher`() = runBlocking {
        val fake = FakeAlarmClock(
            onSet = { error("should not call setAlarmClock in intent mode") }
        )
        val launcher = RecordingLauncher()
        val tool = AlarmSetDeviceTool(fake, launcher)
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
        assertEquals(true, outcome.data["launched"])
        assertEquals(1, launcher.launchCount)
        assertEquals(AlarmIntentBuilder.ACTION_SET_ALARM, launcher.lastSpec!!.action)
        assertEquals(7, launcher.lastSpec!!.extras[AlarmIntentBuilder.EXTRA_HOUR])
        assertEquals(15, launcher.lastSpec!!.extras[AlarmIntentBuilder.EXTRA_MINUTES])
        assertNull(fake.lastRequest)
    }

    @Test
    fun `intent mode activity not found maps to error`() = runBlocking {
        val launcher = RecordingLauncher {
            IntentLaunchResult.ActivityNotFound("no clock app", it.action)
        }
        val tool = AlarmSetDeviceTool(FakeAlarmClock(), launcher)
        val outcome = tool.invoke(
            mapOf("mode" to "intent", "hour" to 6, "minutes" to 0),
            confirmed = true
        )
        assertTrue(outcome is DeviceToolOutcome.Error)
        assertEquals("activity_not_found", (outcome as DeviceToolOutcome.Error).code)
    }

    @Test
    fun `alarm_show launches SHOW_ALARMS`() = runBlocking {
        val launcher = RecordingLauncher()
        val tool = AlarmShowDeviceTool(launcher)
        val outcome = tool.invoke(emptyMap()) as DeviceToolOutcome.Ok
        assertEquals(AlarmIntentBuilder.ACTION_SHOW_ALARMS, outcome.data["action"])
        assertEquals(true, outcome.data["launched"])
        assertEquals(AlarmIntentBuilder.ACTION_SHOW_ALARMS, launcher.lastSpec!!.action)
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
        val tool = AlarmSetDeviceTool(FakeAlarmClock(), RecordingLauncher())
        val outcome = tool.invoke(mapOf("minutes" to 0), confirmed = true)
        assertTrue(outcome is DeviceToolOutcome.Error)
        assertEquals("invalid_args", (outcome as DeviceToolOutcome.Error).code)
    }
}
