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

import com.lanxin.android.builtin.systemtools.domain.AlarmIntentBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmIntentBuilderTest {

    @Test
    fun `setAlarm builds SET_ALARM with hour and minutes`() {
        val spec = AlarmIntentBuilder.setAlarm(hour = 7, minutes = 30, message = "起床")
        assertEquals(AlarmIntentBuilder.ACTION_SET_ALARM, spec.action)
        assertEquals(7, spec.extras[AlarmIntentBuilder.EXTRA_HOUR])
        assertEquals(30, spec.extras[AlarmIntentBuilder.EXTRA_MINUTES])
        assertEquals("起床", spec.extras[AlarmIntentBuilder.EXTRA_MESSAGE])
        assertEquals(false, spec.extras[AlarmIntentBuilder.EXTRA_SKIP_UI])
        assertEquals(true, spec.extras[AlarmIntentBuilder.EXTRA_VIBRATE])
        assertTrue(spec.description.contains("7:30"))
        assertTrue(spec.description.contains("起床"))
    }

    @Test
    fun `setAlarm pads minutes in description`() {
        val spec = AlarmIntentBuilder.setAlarm(9, 5)
        assertTrue(spec.description.contains("9:05"))
    }

    @Test
    fun `setAlarm rejects invalid hour`() {
        try {
            AlarmIntentBuilder.setAlarm(hour = 24, minutes = 0)
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("hour"))
        }
    }

    @Test
    fun `setAlarm rejects invalid minutes`() {
        try {
            AlarmIntentBuilder.setAlarm(hour = 10, minutes = 60)
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("minutes"))
        }
    }

    @Test
    fun `setAlarm with days of week`() {
        val spec = AlarmIntentBuilder.setAlarm(
            hour = 8,
            minutes = 0,
            daysOfWeek = listOf(2, 3, 4, 5, 6)
        )

        @Suppress("UNCHECKED_CAST")
        val days = spec.extras[AlarmIntentBuilder.EXTRA_DAYS] as List<Int>
        assertEquals(listOf(2, 3, 4, 5, 6), days)
    }

    @Test
    fun `fromSetAlarmArgs parses minute alias and strings`() {
        val spec = AlarmIntentBuilder.fromSetAlarmArgs(
            mapOf(
                "hour" to "6",
                "minute" to "15",
                "message" to "药",
                "skip_ui" to true,
                "vibrate" to "false",
                "days" to "1,7"
            )
        )
        assertEquals(6, spec.extras[AlarmIntentBuilder.EXTRA_HOUR])
        assertEquals(15, spec.extras[AlarmIntentBuilder.EXTRA_MINUTES])
        assertEquals(true, spec.extras[AlarmIntentBuilder.EXTRA_SKIP_UI])
        assertEquals(false, spec.extras[AlarmIntentBuilder.EXTRA_VIBRATE])
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf(1, 7), spec.extras[AlarmIntentBuilder.EXTRA_DAYS] as List<Int>)
    }

    @Test
    fun `showAlarms uses SHOW_ALARMS action`() {
        val spec = AlarmIntentBuilder.showAlarms()
        assertEquals(AlarmIntentBuilder.ACTION_SHOW_ALARMS, spec.action)
        assertTrue(spec.extras.isEmpty())
    }
}
