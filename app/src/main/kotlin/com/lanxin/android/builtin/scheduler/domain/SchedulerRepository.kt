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

package com.lanxin.android.builtin.scheduler.domain

import com.lanxin.android.builtin.scheduler.data.SchedulerDao
import com.lanxin.android.builtin.scheduler.data.toDomain
import com.lanxin.android.builtin.scheduler.data.toEntity
import java.time.Instant
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class SchedulerRepository @Inject constructor(
    private val dao: SchedulerDao,
    private val cronParser: CrontabParser
) {
    private val writeMutex = Mutex()

    fun observeTasks(): Flow<List<SchedulerTask>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAll(): List<SchedulerTask> = dao.getAll().map { it.toDomain() }

    suspend fun getTask(id: String): SchedulerTask? = dao.getById(id)?.toDomain()

    suspend fun getEnabledTasks(): List<SchedulerTask> =
        dao.getEnabledTasks().map { it.toDomain() }

    suspend fun filter(type: SchedulerTaskType?, status: TaskStatus?): List<SchedulerTask> =
        dao.filter(type?.name, status?.name).map { it.toDomain() }

    suspend fun upsert(task: SchedulerTask): SchedulerTask = writeMutex.withLock {
        dao.upsert(task.toEntity())
        task
    }

    suspend fun create(
        name: String,
        type: SchedulerTaskType,
        cronExpression: String?,
        runAt: Long?,
        payload: Map<String, String>,
        autoStartConversation: Boolean = true,
        enabled: Boolean = true
    ): SchedulerTask = writeMutex.withLock {
        val runOnce = cronExpression.isNullOrBlank()
        require(name.isNotBlank()) { "任务名称不能为空" }
        require(!runOnce || runAt != null) { "一次性任务必须提供 runAt" }
        if (!cronExpression.isNullOrBlank()) {
            cronParser.parse(cronExpression)
        }
        if (type == SchedulerTaskType.BASIC) {
            require(payload["action"].orEmpty().isNotBlank()) {
                "BASIC 任务 payload 必须包含 action"
            }
        }

        val task = SchedulerTask(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            type = type,
            cronExpression = cronExpression?.trim()?.takeIf { it.isNotEmpty() },
            runOnce = runOnce,
            runAt = runAt,
            payload = payload,
            enabled = enabled,
            autoStartConversation = autoStartConversation,
            status = if (enabled) TaskStatus.IDLE else TaskStatus.IDLE
        )
        val withNext = if (enabled) task.copy(nextRunAt = computeNextRunAt(task)) else task
        dao.upsert(withNext.toEntity())
        withNext
    }

    suspend fun updateFields(
        id: String,
        name: String? = null,
        type: SchedulerTaskType? = null,
        cronExpression: String? = null,
        clearCron: Boolean = false,
        runAt: Long? = null,
        clearRunAt: Boolean = false,
        payload: Map<String, String>? = null,
        autoStartConversation: Boolean? = null,
        enabled: Boolean? = null
    ): SchedulerTask = writeMutex.withLock {
        val existing = dao.getById(id)?.toDomain() ?: error("任务不存在：$id")
        val newCron = when {
            clearCron -> null
            cronExpression != null -> {
                cronParser.parse(cronExpression)
                cronExpression.trim()
            }
            else -> existing.cronExpression
        }
        val newRunAt = when {
            clearRunAt -> null
            runAt != null -> runAt
            else -> existing.runAt
        }
        val runOnce = newCron.isNullOrBlank()
        require(!runOnce || newRunAt != null) { "一次性任务必须提供 runAt" }

        val updated = existing.copy(
            name = name?.trim()?.takeIf { it.isNotEmpty() } ?: existing.name,
            type = type ?: existing.type,
            cronExpression = newCron,
            runOnce = runOnce,
            runAt = newRunAt,
            payload = payload ?: existing.payload,
            autoStartConversation = autoStartConversation ?: existing.autoStartConversation,
            enabled = enabled ?: existing.enabled,
            lastError = null
        )
        val withNext = if (updated.enabled) {
            updated.copy(
                nextRunAt = computeNextRunAt(updated),
                status = TaskStatus.IDLE
            )
        } else {
            updated.copy(nextRunAt = null, status = TaskStatus.IDLE)
        }
        dao.upsert(withNext.toEntity())
        withNext
    }

    suspend fun delete(id: String) = writeMutex.withLock {
        dao.delete(id)
    }

    suspend fun updateStatus(id: String, status: TaskStatus) {
        dao.updateStatus(id, status.name)
    }

    suspend fun updateNextRunAndStatus(id: String, nextRunAt: Long?, status: TaskStatus) {
        dao.updateNextRunAndStatus(id, nextRunAt, status.name)
    }

    suspend fun updateLastError(id: String, error: String?, status: TaskStatus = TaskStatus.FAILED) {
        dao.updateLastError(id, error, status.name)
    }

    suspend fun markRunning(id: String) {
        dao.updateStatus(id, TaskStatus.RUNNING.name)
        dao.updateLastRunAt(id, System.currentTimeMillis())
    }

    suspend fun setEnabled(id: String, enabled: Boolean): SchedulerTask = writeMutex.withLock {
        val existing = dao.getById(id)?.toDomain() ?: error("任务不存在：$id")
        val updated = if (enabled) {
            existing.copy(
                enabled = true,
                nextRunAt = computeNextRunAt(existing.copy(enabled = true)),
                status = TaskStatus.IDLE,
                lastError = null
            )
        } else {
            existing.copy(
                enabled = false,
                nextRunAt = null,
                status = TaskStatus.IDLE
            )
        }
        dao.upsert(updated.toEntity())
        updated
    }

    fun computeNextRunAt(task: SchedulerTask, fromEpochMs: Long = System.currentTimeMillis()): Long {
        if (!task.cronExpression.isNullOrBlank()) {
            val base = ZonedDateTime.ofInstant(Instant.ofEpochMilli(fromEpochMs), task.timezone)
            return cronParser.nextExecutionTime(base, task.cronExpression)
                .toInstant()
                .toEpochMilli()
        }
        val runAt = task.runAt ?: error("一次性任务缺少 runAt")
        return runAt
    }

    fun humanReadable(cron: String): String = cronParser.toHumanReadable(cron)

    fun parseCron(cron: String) = cronParser.parse(cron)

    fun previewNext(cron: String, timezone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long {
        val base = ZonedDateTime.now(timezone)
        return cronParser.nextExecutionTime(base, cron).toInstant().toEpochMilli()
    }
}
