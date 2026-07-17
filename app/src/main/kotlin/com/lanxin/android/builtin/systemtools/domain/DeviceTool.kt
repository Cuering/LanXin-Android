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
 * 统一设备工具接口。
 *
 * ChatRouter `needsTools`、桌宠 VoiceSession、MCP Plugin 共用同一契约：
 * - [name] 稳定工具名（如 alarm_set）
 * - [capability] 对应设置分项
 * - [permissions] 声明权限；stub 不真正申请
 * - [sideEffect] / [confirmationLevel] 门闸用
 * - [invoke] 纯业务；门闸在 [DeviceToolGate] 外层
 */
interface DeviceTool {
    val name: String
    val description: String
    val capability: DeviceCapability
    val permissions: List<DevicePermission>
    val sideEffect: DeviceToolSideEffect
    val confirmationLevel: ConfirmationLevel

    /**
     * 执行工具。调用方应已通过 [DeviceToolGate]。
     * @param args 扁平参数（string/number/bool）
     * @param confirmed 用户是否已确认写操作
     */
    suspend fun invoke(
        args: Map<String, Any?>,
        confirmed: Boolean = false
    ): DeviceToolOutcome
}

/** 稳定工具名常量（单测与文档对齐）。 */
object DeviceToolIds {
    const val CALENDAR_LIST_UPCOMING = "calendar_list_upcoming"
    const val CALENDAR_CREATE_EVENT = "calendar_create_event"
    const val ALARM_SET = "alarm_set"
    const val ALARM_SHOW = "alarm_show"
    const val NOTE_CREATE = "note_create"
    const val NOTE_LIST = "note_list"
    const val NOTE_APPEND = "note_append"
    const val NOTE_UPDATE = "note_update"
    const val NOTE_DELETE = "note_delete"
    const val NOTE_EXPORT = "note_export"
    const val NOTE_IMPORT = "note_import"
    const val FILE_PICK = "file_pick"
    const val FILE_LIST = "file_list"
    const val FILE_READ_TEXT = "file_read_text"
    const val FILE_WRITE = "file_write"
    const val FILE_SHARE = "file_share"
    const val FILE_DELETE = "file_delete"

    val ALL: Set<String> = setOf(
        CALENDAR_LIST_UPCOMING,
        CALENDAR_CREATE_EVENT,
        ALARM_SET,
        ALARM_SHOW,
        NOTE_CREATE,
        NOTE_LIST,
        NOTE_APPEND,
        NOTE_UPDATE,
        NOTE_DELETE,
        NOTE_EXPORT,
        NOTE_IMPORT,
        FILE_PICK,
        FILE_LIST,
        FILE_READ_TEXT,
        FILE_WRITE,
        FILE_SHARE,
        FILE_DELETE
    )

    /** Phase 7.1+ 已接线工具（含 7.2 日历/闹钟、7.3 笔记、7.4 用户文件）。 */
    val M1_STUB_READY: Set<String> = setOf(
        ALARM_SET,
        ALARM_SHOW,
        CALENDAR_LIST_UPCOMING,
        CALENDAR_CREATE_EVENT,
        NOTE_CREATE,
        NOTE_LIST,
        NOTE_APPEND,
        NOTE_UPDATE,
        NOTE_DELETE,
        NOTE_EXPORT,
        NOTE_IMPORT,
        FILE_PICK,
        FILE_LIST,
        FILE_READ_TEXT,
        FILE_WRITE,
        FILE_SHARE,
        FILE_DELETE
    )

    /** 7.3 笔记工具集合。 */
    val NOTES_READY: Set<String> = setOf(
        NOTE_CREATE,
        NOTE_LIST,
        NOTE_APPEND,
        NOTE_UPDATE,
        NOTE_DELETE,
        NOTE_EXPORT,
        NOTE_IMPORT
    )

    /** 7.4 用户文件工具集合。 */
    val FILES_READY: Set<String> = setOf(
        FILE_PICK,
        FILE_LIST,
        FILE_READ_TEXT,
        FILE_WRITE,
        FILE_SHARE,
        FILE_DELETE
    )
}
