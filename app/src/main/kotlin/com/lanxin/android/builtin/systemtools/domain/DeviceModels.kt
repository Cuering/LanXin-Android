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
 * Phase 7 设备能力域模型。
 *
 * 统一日历 / 闹钟 / 笔记 / 用户文件 的权限、确认策略与工具结果形态。
 */

/** 设备能力分组（设置页分项开关）。 */
enum class DeviceCapability {
    CALENDAR,
    ALARM,
    NOTES,
    USER_FILE
}

/**
 * 声明式权限需求（不直接等同 Manifest 字符串，便于 stub 与门闸）。
 * 真机实现时映射到 Android 权限或 Intent 免权限路径。
 */
enum class DevicePermission {
    /** 无运行时权限（如 AlarmClock Intent、分享 Intent） */
    NONE,
    READ_CALENDAR,
    WRITE_CALENDAR,
    /** SAF / persistable URI；用户授权后可用 */
    SAF_TREE,
    /** 应用私有目录读写 */
    APP_PRIVATE_STORAGE
}

/** 写/删类操作的用户确认策略。 */
enum class ConfirmationLevel {
    /** 只读 / 展示 Intent，无需确认 */
    NONE,
    /** 默认：执行前需用户确认（UI 或 tool 批准） */
    CONFIRM,
    /** 高危：必须显式批准（删除等） */
    EXPLICIT_APPROVE
}

/** 工具调用副作用等级。 */
enum class DeviceToolSideEffect {
    READ,
    WRITE,
    DELETE,
    LAUNCH_INTENT
}

/**
 * 系统工具总配置。默认全关；写操作默认需确认。
 */
data class SystemToolsConfig(
    /** 总开关；关则所有 device tool 拒绝执行 */
    val masterEnabled: Boolean = false,
    val calendarEnabled: Boolean = false,
    val alarmEnabled: Boolean = false,
    val notesEnabled: Boolean = false,
    val userFileEnabled: Boolean = false,
    /** 写/删是否要求确认（默认 true） */
    val requireConfirmOnWrite: Boolean = true
) {
    fun isCapabilityEnabled(capability: DeviceCapability): Boolean {
        if (!masterEnabled) return false
        return when (capability) {
            DeviceCapability.CALENDAR -> calendarEnabled
            DeviceCapability.ALARM -> alarmEnabled
            DeviceCapability.NOTES -> notesEnabled
            DeviceCapability.USER_FILE -> userFileEnabled
        }
    }
}

/** 日历事件（只读列表 / 创建请求共用字段）。 */
data class CalendarEvent(
    val id: String,
    val title: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val location: String? = null,
    val calendarId: String? = null
)

/** 创建日历事件请求。 */
data class CreateCalendarEventRequest(
    val title: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val location: String? = null,
    val description: String? = null
)

/** 轻量笔记条目（App 私有 stub 存储）。 */
data class NoteEntry(
    val id: String,
    val title: String,
    val body: String,
    val updatedAtEpochMs: Long
)

/** 用户文件条目（SAF / 私有路径抽象）。 */
data class UserFileEntry(
    val uriOrPath: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long? = null
)

/** 设备工具执行结果（JSON 友好）。 */
sealed class DeviceToolOutcome {
    data class Ok(
        val data: Map<String, Any?> = emptyMap(),
        val message: String? = null
    ) : DeviceToolOutcome()

    data class Denied(
        val reason: String,
        val code: String
    ) : DeviceToolOutcome()

    data class NeedsConfirmation(
        val summary: String,
        val toolName: String,
        val sideEffect: DeviceToolSideEffect
    ) : DeviceToolOutcome()

    data class Error(
        val message: String,
        val code: String = "error"
    ) : DeviceToolOutcome()
}
