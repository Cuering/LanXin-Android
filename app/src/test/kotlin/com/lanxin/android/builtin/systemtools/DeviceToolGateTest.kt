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
import com.lanxin.android.builtin.systemtools.data.CalendarListUpcomingDeviceTool
import com.lanxin.android.builtin.systemtools.data.StubCalendarGateway
import com.lanxin.android.builtin.systemtools.domain.DeviceToolGate
import com.lanxin.android.builtin.systemtools.domain.DeviceToolOutcome
import com.lanxin.android.builtin.systemtools.domain.SystemToolsConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceToolGateTest {

    private val alarm = AlarmSetDeviceTool()
    private val calendarList = CalendarListUpcomingDeviceTool(StubCalendarGateway())

    @Test
    fun `master off denies`() = runBlocking {
        val gate = DeviceToolGate {
            SystemToolsConfig(masterEnabled = false, alarmEnabled = true)
        }
        val out = gate.invoke(alarm, mapOf("hour" to 8, "minutes" to 0), confirmed = true)
        assertTrue(out is DeviceToolOutcome.Denied)
        assertTrue((out as DeviceToolOutcome.Denied).code == "master_disabled")
    }

    @Test
    fun `capability off denies`() = runBlocking {
        val gate = DeviceToolGate {
            SystemToolsConfig(masterEnabled = true, alarmEnabled = false)
        }
        val out = gate.invoke(alarm, mapOf("hour" to 8, "minutes" to 0), confirmed = true)
        assertTrue(out is DeviceToolOutcome.Denied)
        assertTrue((out as DeviceToolOutcome.Denied).code == "capability_disabled")
    }

    @Test
    fun `write without confirm needs confirmation`() = runBlocking {
        val gate = DeviceToolGate {
            SystemToolsConfig(
                masterEnabled = true,
                alarmEnabled = true,
                requireConfirmOnWrite = true
            )
        }
        val out = gate.invoke(alarm, mapOf("hour" to 8, "minutes" to 0), confirmed = false)
        assertTrue(out is DeviceToolOutcome.NeedsConfirmation)
    }

    @Test
    fun `write with confirm succeeds stub`() = runBlocking {
        val gate = DeviceToolGate {
            SystemToolsConfig(
                masterEnabled = true,
                alarmEnabled = true,
                requireConfirmOnWrite = true
            )
        }
        val out = gate.invoke(alarm, mapOf("hour" to 8, "minutes" to 15), confirmed = true)
        assertTrue(out is DeviceToolOutcome.Ok)
        val data = (out as DeviceToolOutcome.Ok).data
        assertTrue(data["action"] == "android.intent.action.SET_ALARM")
        assertTrue(data["stub"] == true)
    }

    @Test
    fun `read calendar does not need confirm`() = runBlocking {
        val gate = DeviceToolGate {
            SystemToolsConfig(
                masterEnabled = true,
                calendarEnabled = true,
                requireConfirmOnWrite = true
            )
        }
        val out = gate.invoke(calendarList, emptyMap(), confirmed = false)
        assertTrue(out is DeviceToolOutcome.Ok)
    }
}
