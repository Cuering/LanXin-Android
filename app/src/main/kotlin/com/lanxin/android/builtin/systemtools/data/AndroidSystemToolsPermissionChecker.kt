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

package com.lanxin.android.builtin.systemtools.data

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.lanxin.android.builtin.systemtools.domain.SystemToolsPermissionChecker
import com.lanxin.android.builtin.systemtools.domain.SystemToolsPermissionStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 日历 / 精确闹钟权限检查与系统设置跳转。
 */
@Singleton
class AndroidSystemToolsPermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context
) : SystemToolsPermissionChecker {

    override fun check(): SystemToolsPermissionStatus {
        val calendarGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val canExact = canScheduleExactAlarms()
        return SystemToolsPermissionStatus(
            calendarReadGranted = calendarGranted,
            canScheduleExactAlarms = canExact,
            calendarHint = if (calendarGranted) {
                "已授予 READ_CALENDAR"
            } else {
                "未授予日历读取权限，list 将返回清晰提示"
            },
            exactAlarmHint = if (canExact) {
                "已允许精确闹钟（setAlarmClock）"
            } else {
                "需在系统设置中允许「精确闹钟」"
            }
        )
    }

    override fun openAppDetailsSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    override fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // fall through to app details
            }
        }
        openAppDetailsSettings()
    }

    private fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.canScheduleExactAlarms()
        } catch (_: Exception) {
            false
        }
    }
}
