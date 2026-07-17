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

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 轻量意图 → 工具计划解析（无 LLM）。
 *
 * Phase 7.5：供 [DeviceToolBridge] / VoiceSession / Chat 统一入口做
 * 「用户话术 → tool id + 默认 args」；不替代云端 tool_call 解析。
 *
 * 仅覆盖高置信关键词；未命中返回 null（纯闲聊）。
 */
@Singleton
class DeviceToolIntentResolver @Inject constructor() {

    /**
     * @param availableToolNames 注册表中的工具名；空则按全部 [DeviceToolIds.ALL] 匹配
     */
    fun resolve(
        userText: String,
        availableToolNames: Set<String> = DeviceToolIds.ALL
    ): DeviceToolPlan? {
        val text = userText.trim()
        if (text.isEmpty()) return null
        val lower = text.lowercase()

        fun allowed(id: String): Boolean =
            availableToolNames.isEmpty() || id in availableToolNames

        // 闹钟列表（优先于「闹钟」写操作）
        if (allowed(DeviceToolIds.ALARM_SHOW) &&
            (containsAny(lower, "打开闹钟", "闹钟列表", "show alarm", "show_alarms") ||
                (containsAny(lower, "闹钟") && containsAny(lower, "打开", "列表", "看看")))
        ) {
            return DeviceToolPlan(
                toolName = DeviceToolIds.ALARM_SHOW,
                args = emptyMap(),
                reason = "intent:alarm_show"
            )
        }

        // 设置闹钟：尽量抽出 hour/minutes
        if (allowed(DeviceToolIds.ALARM_SET) &&
            containsAny(lower, "设闹钟", "定闹钟", "设置闹钟", "定个闹钟", "设个闹钟", "set alarm", "alarm_set")
        ) {
            val hm = extractHourMinute(text)
            val args = mutableMapOf<String, Any?>(
                "hour" to (hm?.first ?: 8),
                "minutes" to (hm?.second ?: 0)
            )
            extractAlarmMessage(text)?.let { args["message"] = it }
            return DeviceToolPlan(
                toolName = DeviceToolIds.ALARM_SET,
                args = args,
                reason = "intent:alarm_set"
            )
        }

        // 日历列表
        if (allowed(DeviceToolIds.CALENDAR_LIST_UPCOMING) &&
            containsAny(
                lower,
                "日程",
                "日历",
                "upcoming event",
                "有什么安排",
                "今天安排",
                "明天安排",
                "list calendar"
            )
        ) {
            return DeviceToolPlan(
                toolName = DeviceToolIds.CALENDAR_LIST_UPCOMING,
                args = mapOf("limit" to 10, "days" to 7),
                reason = "intent:calendar_list"
            )
        }

        // 列笔记
        if (allowed(DeviceToolIds.NOTE_LIST) &&
            containsAny(lower, "列笔记", "笔记列表", "有哪些笔记", "list note", "note_list")
        ) {
            return DeviceToolPlan(
                toolName = DeviceToolIds.NOTE_LIST,
                args = mapOf("limit" to 20),
                reason = "intent:note_list"
            )
        }

        // 创建笔记
        if (allowed(DeviceToolIds.NOTE_CREATE) &&
            containsAny(lower, "记笔记", "写笔记", "新建笔记", "记一下", "create note", "note_create")
        ) {
            val body = extractNoteBody(text)
            return DeviceToolPlan(
                toolName = DeviceToolIds.NOTE_CREATE,
                args = mapOf(
                    "title" to (body?.take(32) ?: "桌宠笔记"),
                    "body" to (body ?: text)
                ),
                reason = "intent:note_create"
            )
        }

        // 文件列表
        if (allowed(DeviceToolIds.FILE_LIST) &&
            containsAny(lower, "文件列表", "列出文件", "我的文件", "list file", "file_list")
        ) {
            return DeviceToolPlan(
                toolName = DeviceToolIds.FILE_LIST,
                args = mapOf("sort" to "date", "limit" to 20),
                reason = "intent:file_list"
            )
        }

        return null
    }

    private fun containsAny(text: String, vararg needles: String): Boolean =
        needles.any { text.contains(it.lowercase()) }

    /**
     * 匹配「7点30」「7:30」「7：30」「早上8点」等；失败返回 null。
     */
    internal fun extractHourMinute(text: String): Pair<Int, Int>? {
        val colon = Regex("""(\d{1,2})\s*[:：]\s*(\d{1,2})""").find(text)
        if (colon != null) {
            val h = colon.groupValues[1].toIntOrNull() ?: return null
            val m = colon.groupValues[2].toIntOrNull() ?: return null
            if (h in 0..23 && m in 0..59) return h to m
        }
        val cn = Regex("""(\d{1,2})\s*点\s*(\d{1,2})?""").find(text)
        if (cn != null) {
            val h = cn.groupValues[1].toIntOrNull() ?: return null
            val m = cn.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            if (h in 0..23 && m in 0..59) return h to m
        }
        return null
    }

    private fun extractAlarmMessage(text: String): String? {
        val m = Regex("""(?:叫我|提醒我|标签)[:：\s]*(.+)""").find(text) ?: return null
        return m.groupValues[1].trim().take(64).ifBlank { null }
    }

    private fun extractNoteBody(text: String): String? {
        val patterns = listOf(
            Regex("""(?:记笔记|写笔记|新建笔记|记一下)[:：\s]*(.+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:create note|note_create)[:：\s]*(.+)""", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val m = p.find(text) ?: continue
            val body = m.groupValues[1].trim()
            if (body.isNotEmpty()) return body
        }
        return null
    }
}

/**
 * 解析出的工具调用计划（尚未经 Gate）。
 */
data class DeviceToolPlan(
    val toolName: String,
    val args: Map<String, Any?> = emptyMap(),
    val reason: String = "",
    /** 调用方是否已带 confirmed；默认 false，由 Gate 决策 */
    val confirmed: Boolean = false
)
