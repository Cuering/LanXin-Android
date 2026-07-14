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

package com.lanxin.android.builtin.scheduler.domain

import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 标准 5 字段 cron 解析器：分 时 日 月 周。
 * 支持：* / , - 以及星期英文简写（SUN..SAT）。
 */
class CrontabParser {

    data class CronSchedule(
        val minutes: List<Int>,
        val hours: List<Int>,
        val daysOfMonth: List<Int>,
        val months: List<Int>,
        val daysOfWeek: List<Int>
    )

    fun parse(expression: String): CronSchedule {
        val parts = expression.trim().split(Regex("\\s+"))
        require(parts.size == 5) {
            "cron 表达式必须是 5 字段（分 时 日 月 周），实际 ${parts.size} 字段：$expression"
        }
        return CronSchedule(
            minutes = parseField(parts[0], 0, 59, emptyMap()),
            hours = parseField(parts[1], 0, 23, emptyMap()),
            daysOfMonth = parseField(parts[2], 1, 31, emptyMap()),
            months = parseField(parts[3], 1, 12, MONTH_ALIASES),
            daysOfWeek = parseField(parts[4], 0, 6, DOW_ALIASES)
        )
    }

    fun nextExecutionTime(baseTime: ZonedDateTime, schedule: CronSchedule): ZonedDateTime {
        // 分钟级精度：从下一分钟开始搜索
        var cursor = baseTime.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        // 最多搜索 4 年，避免死循环
        val limit = cursor.plusYears(4)
        while (cursor.isBefore(limit)) {
            if (matches(cursor, schedule)) {
                return cursor.withSecond(0).withNano(0)
            }
            cursor = cursor.plusMinutes(1)
        }
        error("无法在 4 年内找到匹配的下次执行时间")
    }

    fun nextExecutionTime(baseTime: ZonedDateTime, expression: String): ZonedDateTime {
        return nextExecutionTime(baseTime, parse(expression))
    }

    fun toHumanReadable(expression: String): String {
        return runCatching {
            val schedule = parse(expression)
            buildString {
                append(describeMinutes(schedule.minutes))
                append(" ")
                append(describeHours(schedule.hours))
                append("，")
                append(describeDays(schedule.daysOfMonth, schedule.daysOfWeek))
                append("，")
                append(describeMonths(schedule.months))
            }
        }.getOrElse { "无效 cron：$expression" }
    }

    private fun matches(time: ZonedDateTime, schedule: CronSchedule): Boolean {
        val minuteOk = time.minute in schedule.minutes
        val hourOk = time.hour in schedule.hours
        val monthOk = time.monthValue in schedule.months
        val dayOfMonthOk = time.dayOfMonth in schedule.daysOfMonth
        // cron: 0=SUN ... 6=SAT；Java DayOfWeek: MON=1 ... SUN=7
        val cronDow = if (time.dayOfWeek == DayOfWeek.SUNDAY) 0 else time.dayOfWeek.value
        val dayOfWeekOk = cronDow in schedule.daysOfWeek

        // 标准 cron：日与周同时非 * 时，OR 语义；此处字段已展开，用两者都受限时取 OR。
        val dayConstraint = when {
            schedule.daysOfMonth.size < 31 && schedule.daysOfWeek.size < 7 ->
                dayOfMonthOk || dayOfWeekOk
            schedule.daysOfMonth.size < 31 -> dayOfMonthOk
            schedule.daysOfWeek.size < 7 -> dayOfWeekOk
            else -> true
        }
        return minuteOk && hourOk && monthOk && dayConstraint
    }

    private fun parseField(
        field: String,
        min: Int,
        max: Int,
        aliases: Map<String, Int>
    ): List<Int> {
        if (field == "*") return (min..max).toList()
        val values = linkedSetOf<Int>()
        field.split(",").forEach { tokenRaw ->
            val token = tokenRaw.trim().uppercase(Locale.ROOT)
            require(token.isNotEmpty()) { "空字段片段" }

            val stepParts = token.split("/")
            require(stepParts.size <= 2) { "非法步进：$token" }
            val step = if (stepParts.size == 2) {
                stepParts[1].toIntOrNull()?.takeIf { it > 0 }
                    ?: error("非法步进：$token")
            } else {
                1
            }
            val rangePart = stepParts[0]

            val (start, end) = when {
                rangePart == "*" -> min to max
                rangePart.contains("-") -> {
                    val bounds = rangePart.split("-")
                    require(bounds.size == 2) { "非法范围：$token" }
                    resolveNumber(bounds[0], aliases) to resolveNumber(bounds[1], aliases)
                }
                else -> {
                    val v = resolveNumber(rangePart, aliases)
                    v to v
                }
            }
            require(start in min..max && end in min..max && start <= end) {
                "字段越界：$token，允许 $min..$max"
            }
            var current = start
            while (current <= end) {
                values += current
                current += step
            }
        }
        return values.sorted()
    }

    private fun resolveNumber(raw: String, aliases: Map<String, Int>): Int {
        val key = raw.trim().uppercase(Locale.ROOT)
        aliases[key]?.let { return it }
        return key.toIntOrNull() ?: error("无法解析数值：$raw")
    }

    private fun describeMinutes(minutes: List<Int>): String = when {
        minutes.size == 60 -> "每分钟"
        minutes.size == 1 -> "第 ${minutes[0]} 分"
        minutes == (0..59 step 5).toList() -> "每 5 分钟"
        minutes == (0..59 step 10).toList() -> "每 10 分钟"
        minutes == (0..59 step 15).toList() -> "每 15 分钟"
        minutes == (0..59 step 30).toList() -> "每 30 分钟"
        else -> "分钟 ${minutes.joinToString(",")}"
    }

    private fun describeHours(hours: List<Int>): String = when {
        hours.size == 24 -> "每小时"
        hours.size == 1 -> String.format(Locale.ROOT, "%02d 点", hours[0])
        else -> "小时 ${hours.joinToString(",")}"
    }

    private fun describeDays(daysOfMonth: List<Int>, daysOfWeek: List<Int>): String {
        val dayPart = when {
            daysOfMonth.size == 31 -> null
            daysOfMonth.size == 1 -> "每月 ${daysOfMonth[0]} 日"
            else -> "日期 ${daysOfMonth.joinToString(",")}"
        }
        val weekPart = when {
            daysOfWeek.size == 7 -> null
            daysOfWeek == WEEKDAYS -> "工作日"
            daysOfWeek == WEEKEND -> "周末"
            else -> "星期 ${daysOfWeek.joinToString(",") { DOW_LABELS[it] ?: it.toString() }}"
        }
        return listOfNotNull(dayPart, weekPart).joinToString(" / ").ifBlank { "每天" }
    }

    private fun describeMonths(months: List<Int>): String = when {
        months.size == 12 -> "全年"
        months.size == 1 -> "${months[0]} 月"
        else -> "月份 ${months.joinToString(",")}"
    }

    companion object {
        private val MONTH_ALIASES = mapOf(
            "JAN" to 1,
            "FEB" to 2,
            "MAR" to 3,
            "APR" to 4,
            "MAY" to 5,
            "JUN" to 6,
            "JUL" to 7,
            "AUG" to 8,
            "SEP" to 9,
            "OCT" to 10,
            "NOV" to 11,
            "DEC" to 12
        )
        private val DOW_ALIASES = mapOf(
            "SUN" to 0,
            "MON" to 1,
            "TUE" to 2,
            "WED" to 3,
            "THU" to 4,
            "FRI" to 5,
            "SAT" to 6
        )
        private val DOW_LABELS = mapOf(
            0 to "日",
            1 to "一",
            2 to "二",
            3 to "三",
            4 to "四",
            5 to "五",
            6 to "六"
        )
        private val WEEKDAYS = listOf(1, 2, 3, 4, 5)
        private val WEEKEND = listOf(0, 6)

        /** 常用预设：名称 → cron */
        val PRESETS: List<Pair<String, String>> = listOf(
            "每小时" to "0 * * * *",
            "每天 09:00" to "0 9 * * *",
            "每天 12:00" to "0 12 * * *",
            "每天 18:00" to "0 18 * * *",
            "工作日 09:00" to "0 9 * * 1-5",
            "每周一 09:00" to "0 9 * * 1",
            "每月 1 日 09:00" to "0 9 1 * *"
        )
    }
}
