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
 * 设置页展示的运行时权限状态（日历 / 精确闹钟）。
 */
data class SystemToolsPermissionStatus(
    val calendarReadGranted: Boolean = false,
    val canScheduleExactAlarms: Boolean = true,
    val calendarHint: String = "",
    val exactAlarmHint: String = ""
) {
    val calendarLabel: String
        get() = if (calendarReadGranted) "已授权" else "未授权"

    val exactAlarmLabel: String
        get() = if (canScheduleExactAlarms) "已允许" else "需引导"
}

/**
 * 权限检查抽象（便于单测 Fake）。
 */
interface SystemToolsPermissionChecker {
    fun check(): SystemToolsPermissionStatus

    /** 打开应用详情页（日历权限等）。 */
    fun openAppDetailsSettings()

    /** 打开「允许精确闹钟」系统页（API 31+）。 */
    fun openExactAlarmSettings()
}
