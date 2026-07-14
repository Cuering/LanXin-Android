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

import com.lanxin.android.builtin.scheduler.domain.CrontabParser
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrontabParserTest {

    private val parser = CrontabParser()
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun parse_everyHour() {
        val schedule = parser.parse("0 * * * *")
        assertEquals(listOf(0), schedule.minutes)
        assertEquals(24, schedule.hours.size)
    }

    @Test
    fun parse_weekdayAlias() {
        val schedule = parser.parse("30 9 * * MON-FRI")
        assertEquals(listOf(30), schedule.minutes)
        assertEquals(listOf(9), schedule.hours)
        assertEquals(listOf(1, 2, 3, 4, 5), schedule.daysOfWeek)
    }

    @Test
    fun nextExecution_dailyNine() {
        val base = ZonedDateTime.of(2026, 7, 14, 8, 0, 0, 0, zone)
        val next = parser.nextExecutionTime(base, "0 9 * * *")
        assertEquals(9, next.hour)
        assertEquals(0, next.minute)
        assertEquals(14, next.dayOfMonth)
    }

    @Test
    fun nextExecution_afterTime_rollsToTomorrow() {
        val base = ZonedDateTime.of(2026, 7, 14, 10, 0, 0, 0, zone)
        val next = parser.nextExecutionTime(base, "0 9 * * *")
        assertEquals(15, next.dayOfMonth)
        assertEquals(9, next.hour)
    }

    @Test
    fun step_everyFiveMinutes() {
        val schedule = parser.parse("*/5 * * * *")
        assertEquals(listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55), schedule.minutes)
    }

    @Test
    fun humanReadable_notEmpty() {
        val text = parser.toHumanReadable("0 9 * * 1-5")
        assertTrue(text.isNotBlank())
        assertTrue(!text.startsWith("无效"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun parse_invalidFieldCount() {
        parser.parse("* * *")
    }
}
