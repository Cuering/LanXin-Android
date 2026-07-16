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
 * 系统闹钟 Intent 规格构建器（纯逻辑，不依赖 Android Runtime）。
 *
 * 真机侧用 [AlarmIntentSpec] 填入 `android.provider.AlarmClock` 常量启动系统时钟 App。
 * **不**实现自有闹钟；应用内提醒继续走 `builtin/scheduler`。
 *
 * @see android.provider.AlarmClock
 */
data class AlarmIntentSpec(
    val action: String,
    val extras: Map<String, Any?> = emptyMap(),
    val description: String = ""
) {
    /** 转为可启动的 [IntentLaunchSpec]。 */
    fun toLaunchSpec(): IntentLaunchSpec = IntentLaunchSpec(
        action = action,
        extras = extras,
        description = description
    )
}

object AlarmIntentBuilder {

    /** android.provider.AlarmClock.ACTION_SET_ALARM */
    const val ACTION_SET_ALARM = "android.intent.action.SET_ALARM"

    /** android.provider.AlarmClock.ACTION_SHOW_ALARMS */
    const val ACTION_SHOW_ALARMS = "android.intent.action.SHOW_ALARMS"

    const val EXTRA_HOUR = "android.intent.extra.alarm.HOUR"
    const val EXTRA_MINUTES = "android.intent.extra.alarm.MINUTES"
    const val EXTRA_MESSAGE = "android.intent.extra.alarm.MESSAGE"
    const val EXTRA_SKIP_UI = "android.intent.extra.alarm.SKIP_UI"
    const val EXTRA_VIBRATE = "android.intent.extra.alarm.VIBRATE"
    const val EXTRA_DAYS = "android.intent.extra.alarm.DAYS"

    /**
     * 构建「设置闹钟」Intent 规格。
     *
     * @param hour 0–23
     * @param minutes 0–59
     * @param message 闹钟标签
     * @param skipUi 是否尽量跳过系统 UI（部分 ROM 仍会弹）
     * @param vibrate 是否振动
     * @param daysOfWeek Calendar.SUNDAY=1 … SATURDAY=7；null 表示仅一次
     */
    fun setAlarm(
        hour: Int,
        minutes: Int,
        message: String? = null,
        skipUi: Boolean = false,
        vibrate: Boolean = true,
        daysOfWeek: List<Int>? = null
    ): AlarmIntentSpec {
        require(hour in 0..23) { "hour must be 0..23, got $hour" }
        require(minutes in 0..59) { "minutes must be 0..59, got $minutes" }
        if (daysOfWeek != null) {
            require(daysOfWeek.all { it in 1..7 }) {
                "daysOfWeek values must be 1..7 (Calendar), got $daysOfWeek"
            }
        }
        val extras = buildMap<String, Any?> {
            put(EXTRA_HOUR, hour)
            put(EXTRA_MINUTES, minutes)
            put(EXTRA_SKIP_UI, skipUi)
            put(EXTRA_VIBRATE, vibrate)
            if (!message.isNullOrBlank()) put(EXTRA_MESSAGE, message.trim())
            if (!daysOfWeek.isNullOrEmpty()) put(EXTRA_DAYS, daysOfWeek)
        }
        val label = message?.takeIf { it.isNotBlank() } ?: "(无标签)"
        return AlarmIntentSpec(
            action = ACTION_SET_ALARM,
            extras = extras,
            description = "设置闹钟 $hour:${minutes.toString().padStart(2, '0')} $label"
        )
    }

    /** 打开系统闹钟列表。 */
    fun showAlarms(): AlarmIntentSpec = AlarmIntentSpec(
        action = ACTION_SHOW_ALARMS,
        extras = emptyMap(),
        description = "打开系统闹钟列表"
    )

    /**
     * 从扁平 tool args 解析。
     * 支持 hour/minutes 或 hour/minute；message 可选。
     */
    fun fromSetAlarmArgs(args: Map<String, Any?>): AlarmIntentSpec {
        val hour = args.intArg("hour")
            ?: throw IllegalArgumentException("hour 必填 (0-23)")
        val minutes = args.intArg("minutes")
            ?: args.intArg("minute")
            ?: throw IllegalArgumentException("minutes 必填 (0-59)")
        val message = args["message"]?.toString()
        val skipUi = args.boolArg("skip_ui") ?: false
        val vibrate = args.boolArg("vibrate") ?: true
        val days = args["days"]?.let { raw ->
            when (raw) {
                is List<*> -> raw.mapNotNull {
                    when (it) {
                        is Number -> it.toInt()
                        is String -> it.toIntOrNull()
                        else -> null
                    }
                }
                is String -> raw.split(",").mapNotNull { it.trim().toIntOrNull() }
                else -> null
            }
        }
        return setAlarm(
            hour = hour,
            minutes = minutes,
            message = message,
            skipUi = skipUi,
            vibrate = vibrate,
            daysOfWeek = days
        )
    }
}

internal fun Map<String, Any?>.intArg(key: String): Int? {
    val v = this[key] ?: return null
    return when (v) {
        is Int -> v
        is Long -> v.toInt()
        is Double -> v.toInt()
        is Float -> v.toInt()
        is String -> v.toIntOrNull()
        is Number -> v.toInt()
        else -> null
    }
}

internal fun Map<String, Any?>.boolArg(key: String): Boolean? {
    val v = this[key] ?: return null
    return when (v) {
        is Boolean -> v
        is String -> v.toBooleanStrictOrNull()
        is Number -> v.toInt() != 0
        else -> null
    }
}
