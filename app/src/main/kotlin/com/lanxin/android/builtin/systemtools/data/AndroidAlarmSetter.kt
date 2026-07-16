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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lanxin.android.builtin.systemtools.domain.AlarmClockGateway
import com.lanxin.android.builtin.systemtools.domain.AlarmClockResult
import com.lanxin.android.builtin.systemtools.domain.AlarmClockTimeResolver
import com.lanxin.android.builtin.systemtools.domain.SetAlarmClockRequest
import com.lanxin.android.builtin.systemtools.receiver.SystemToolsAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 真机精确闹钟：`AlarmManager.setAlarmClock()` + PendingIntent 广播。
 *
 * - 缺 `SCHEDULE_EXACT_ALARM`（API 31+）时返回 [AlarmClockResult.NeedsExactAlarmPermission]
 * - 触发后由 [SystemToolsAlarmReceiver] 发通知（不抢系统时钟 App）
 *
 * 同时保留 [AlarmIntentBuilder] 路径：`alarm_set` 可选择 Intent 或 setAlarmClock。
 */
@Singleton
class AndroidAlarmSetter @Inject constructor(
    @ApplicationContext private val context: Context
) : AlarmClockGateway {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                alarmManager.canScheduleExactAlarms()
            } catch (_: Exception) {
                false
            }
        } else {
            true
        }
    }

    override fun setAlarmClock(request: SetAlarmClockRequest): AlarmClockResult {
        val trigger = request.triggerAtEpochMs
        if (trigger <= System.currentTimeMillis()) {
            return AlarmClockResult.Error(
                message = "trigger_at 必须晚于当前时间",
                code = "invalid_time"
            )
        }
        if (!canScheduleExactAlarms()) {
            return AlarmClockResult.NeedsExactAlarmPermission()
        }
        val requestCode = request.requestCode
            ?: AlarmClockTimeResolver.requestCodeOf(trigger, request.message)
        return try {
            val showIntent = PendingIntent.getActivity(
                context,
                requestCode,
                context.packageManager.getLaunchIntentForPackage(context.packageName)
                    ?: Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val fireIntent = Intent(context, SystemToolsAlarmReceiver::class.java).apply {
                action = SystemToolsAlarmReceiver.ACTION_FIRE
                putExtra(SystemToolsAlarmReceiver.EXTRA_MESSAGE, request.message.orEmpty())
                putExtra(SystemToolsAlarmReceiver.EXTRA_TRIGGER_AT, trigger)
            }
            val operation = PendingIntent.getBroadcast(
                context,
                requestCode,
                fireIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val info = AlarmManager.AlarmClockInfo(trigger, showIntent)
            alarmManager.setAlarmClock(info, operation)
            AlarmClockResult.Ok(
                triggerAtEpochMs = trigger,
                requestCode = requestCode,
                message = request.message,
                method = "setAlarmClock"
            )
        } catch (se: SecurityException) {
            AlarmClockResult.NeedsExactAlarmPermission(
                message = se.message
                    ?: "SCHEDULE_EXACT_ALARM 被拒绝（SecurityException）"
            )
        } catch (e: Exception) {
            AlarmClockResult.Error(e.message ?: e.toString())
        }
    }
}
