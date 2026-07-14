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

package com.lanxin.android.builtin.scheduler.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lanxin.android.builtin.scheduler.domain.SchedulerTask
import com.lanxin.android.builtin.scheduler.domain.SchedulerTaskType
import com.lanxin.android.builtin.scheduler.domain.TaskStatus
import java.time.ZoneId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(
    tableName = "scheduler_tasks",
    indices = [
        Index(value = ["enabled"]),
        Index(value = ["status"]),
        Index(value = ["next_run_at"]),
        Index(value = ["type"])
    ]
)
data class SchedulerTaskEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "cron_expression")
    val cronExpression: String? = null,

    @ColumnInfo(name = "run_once")
    val runOnce: Boolean = false,

    @ColumnInfo(name = "run_at")
    val runAt: Long? = null,

    @ColumnInfo(name = "timezone")
    val timezone: String = ZoneId.systemDefault().id,

    @ColumnInfo(name = "payload")
    val payload: String = "{}",

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true,

    @ColumnInfo(name = "auto_start_conversation")
    val autoStartConversation: Boolean = true,

    @ColumnInfo(name = "next_run_at")
    val nextRunAt: Long? = null,

    @ColumnInfo(name = "last_run_at")
    val lastRunAt: Long? = null,

    @ColumnInfo(name = "last_error")
    val lastError: String? = null,

    @ColumnInfo(name = "status")
    val status: String = TaskStatus.IDLE.name,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

private val payloadJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun SchedulerTaskEntity.toDomain(): SchedulerTask {
    val payloadMap: Map<String, String> = runCatching {
        payloadJson.decodeFromString<Map<String, String>>(payload)
    }.getOrDefault(emptyMap())

    return SchedulerTask(
        id = id,
        name = name,
        type = runCatching { SchedulerTaskType.valueOf(type) }.getOrDefault(SchedulerTaskType.BASIC),
        cronExpression = cronExpression,
        runOnce = runOnce,
        runAt = runAt,
        timezone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault()),
        payload = payloadMap,
        enabled = enabled,
        autoStartConversation = autoStartConversation,
        nextRunAt = nextRunAt,
        lastRunAt = lastRunAt,
        lastError = lastError,
        status = runCatching { TaskStatus.valueOf(status) }.getOrDefault(TaskStatus.IDLE),
        createdAt = createdAt
    )
}

fun SchedulerTask.toEntity(): SchedulerTaskEntity = SchedulerTaskEntity(
    id = id,
    name = name,
    type = type.name,
    cronExpression = cronExpression,
    runOnce = runOnce,
    runAt = runAt,
    timezone = timezone.id,
    payload = payloadJson.encodeToString(payload),
    enabled = enabled,
    autoStartConversation = autoStartConversation,
    nextRunAt = nextRunAt,
    lastRunAt = lastRunAt,
    lastError = lastError,
    status = status.name,
    createdAt = createdAt
)
