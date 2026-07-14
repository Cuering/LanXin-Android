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

import android.content.Context
import java.time.ZoneId
import java.util.UUID

enum class SchedulerTaskType {
    BASIC,
    ACTIVE_AGENT
}

enum class TaskStatus {
    IDLE,
    SCHEDULED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class SchedulerTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: SchedulerTaskType,
    val cronExpression: String? = null,
    val runOnce: Boolean = false,
    val runAt: Long? = null,
    val timezone: ZoneId = ZoneId.systemDefault(),
    val payload: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val autoStartConversation: Boolean = true,
    val nextRunAt: Long? = null,
    val lastRunAt: Long? = null,
    val lastError: String? = null,
    val status: TaskStatus = TaskStatus.IDLE,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isPeriodic: Boolean get() = !cronExpression.isNullOrBlank()
}

/**
 * BASIC 任务 action 处理器。禁止代码注入，只能走注册表。
 */
fun interface TaskActionHandler {
    suspend fun execute(context: Context, payload: Map<String, String>): Result<Unit>
}
