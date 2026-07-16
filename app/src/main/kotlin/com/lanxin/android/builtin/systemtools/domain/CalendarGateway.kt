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
 * 日历读/写 Gateway 抽象。
 *
 * - 真机：`AndroidCalendarReader`（CalendarContract.Instances）
 * - 单测 / 无权限降级：`StubCalendarGateway`
 */
interface CalendarGateway {
    /**
     * 查询 [afterEpochMs, afterEpochMs + days * dayMs] 窗口内即将到来的事件。
     *
     * @param days 未来天数（1–30），默认 7
     * @param limit 最多返回条数
     */
    fun listUpcoming(
        limit: Int = 10,
        afterEpochMs: Long = System.currentTimeMillis(),
        days: Int = 7
    ): CalendarListResult

    /**
     * 创建事件。
     *
     * 真机默认走 Intent 少权限路径，返回 [CalendarCreateResult.IntentLaunched]；
     * stub 内存写入返回 [CalendarCreateResult.Created]。
     */
    fun create(request: CreateCalendarEventRequest): CalendarCreateResult
}

/** 日历创建结果（Intent 路径 / stub / 错误）。 */
sealed class CalendarCreateResult {
    /** stub 或 ContentProvider 直接写入成功。 */
    data class Created(
        val event: CalendarEvent,
        val stub: Boolean = true
    ) : CalendarCreateResult()

    /** 已调起系统日历 INSERT Intent（用户在系统 UI 确认）。 */
    data class IntentLaunched(
        val action: String,
        val description: String,
        val resolvedActivity: String? = null,
        val request: CreateCalendarEventRequest
    ) : CalendarCreateResult()

    data class ActivityNotFound(val message: String) : CalendarCreateResult()

    data class Error(val message: String, val code: String = "calendar_create_error") :
        CalendarCreateResult()
}

/** 日历列表结果（含权限/错误语义，不抛崩溃）。 */
sealed class CalendarListResult {
    data class Ok(val events: List<CalendarEvent>) : CalendarListResult()

    data class PermissionDenied(
        val message: String = "未授予 READ_CALENDAR，请到设置 → 系统能力 授权日历权限"
    ) : CalendarListResult()

    data class Error(val message: String) : CalendarListResult()
}

/**
 * 日历查询参数校验（纯逻辑，可单测）。
 */
object CalendarQueryParams {
    const val DEFAULT_DAYS = 7
    const val MAX_DAYS = 30
    const val DEFAULT_LIMIT = 10
    const val MAX_LIMIT = 100
    const val DAY_MS = 86_400_000L

    fun normalizeDays(days: Int?): Int =
        (days ?: DEFAULT_DAYS).coerceIn(1, MAX_DAYS)

    fun normalizeLimit(limit: Int?): Int =
        (limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)

    fun windowEndEpochMs(afterEpochMs: Long, days: Int): Long =
        afterEpochMs + normalizeDays(days) * DAY_MS
}
