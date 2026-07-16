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
 * 精确闹钟设置 Gateway。
 *
 * 真机：`AndroidAlarmSetter`（AlarmManager.setAlarmClock + PendingIntent 广播）。
 * 单测：`FakeAlarmClockGateway`。
 *
 * 与 [AlarmIntentBuilder]（系统时钟 App Intent）互补：
 * - Intent 路径：打开系统闹钟 App（无 SCHEDULE_EXACT_ALARM）
 * - setAlarmClock 路径：应用内精确触发（需权限引导）
 */
interface AlarmClockGateway {
    fun setAlarmClock(request: SetAlarmClockRequest): AlarmClockResult

    fun canScheduleExactAlarms(): Boolean

    /** 打开系统「允许精确闹钟」设置页 Intent action 提示。 */
    fun exactAlarmSettingsHint(): String =
        "Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM"
}

data class SetAlarmClockRequest(
    /** 触发时刻 epoch ms（必须晚于现在） */
    val triggerAtEpochMs: Long,
    val message: String? = null,
    /** 稳定 requestCode 种子，默认由 message+trigger 派生 */
    val requestCode: Int? = null
)

sealed class AlarmClockResult {
    data class Ok(
        val triggerAtEpochMs: Long,
        val requestCode: Int,
        val message: String?,
        val method: String = "setAlarmClock"
    ) : AlarmClockResult()

    data class NeedsExactAlarmPermission(
        val message: String =
            "未授予 SCHEDULE_EXACT_ALARM。请到设置 → 系统能力 打开「允许精确闹钟」"
    ) : AlarmClockResult()

    data class Error(val message: String, val code: String = "error") : AlarmClockResult()
}

/**
 * 从 tool args / hour+minutes 解析触发时刻（纯逻辑）。
 */
object AlarmClockTimeResolver {
    /**
     * 将 hour/minutes 转为「下一个」触发 epoch。
     * 若今日该时刻已过，则推到明天。
     */
    fun nextTriggerEpochMs(
        hour: Int,
        minutes: Int,
        nowEpochMs: Long = System.currentTimeMillis(),
        timeZoneId: String? = null
    ): Long {
        require(hour in 0..23) { "hour must be 0..23, got $hour" }
        require(minutes in 0..59) { "minutes must be 0..59, got $minutes" }
        val zone = if (timeZoneId.isNullOrBlank()) {
            java.util.TimeZone.getDefault()
        } else {
            java.util.TimeZone.getTimeZone(timeZoneId)
        }
        val cal = java.util.Calendar.getInstance(zone).apply {
            timeInMillis = nowEpochMs
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minutes)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= nowEpochMs) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun requestCodeOf(triggerAtEpochMs: Long, message: String?): Int {
        val seed = "${triggerAtEpochMs / 60_000L}|${message.orEmpty()}"
        return seed.hashCode()
    }
}
