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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.lanxin.android.builtin.scheduler.worker.AlarmReceiver
import com.lanxin.android.builtin.scheduler.worker.SchedulerTaskWorker
import com.lanxin.android.core.log.LogManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 双轨调度引擎：
 * - delay ≥ 15min → WorkManager OneTimeWorkRequest
 * - 0 < delay < 15min → AlarmManager exact + allow while idle
 * - delay ≤ 0 → 立即 enqueue 0-delay Worker
 */
@Singleton
class SchedulerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SchedulerRepository,
    private val logManager: LogManager
) {
    private val logger get() = logManager.getLogger("SchedulerEngine")
    private val mutex = Mutex()
    private val workNames = ConcurrentHashMap<String, String>()

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun scheduleTask(taskId: String) = mutex.withLock {
        val task = repository.getTask(taskId) ?: run {
            logger.warning("scheduleTask: 任务不存在 $taskId")
            return
        }
        if (!task.enabled || task.status == TaskStatus.CANCELLED || task.status == TaskStatus.COMPLETED) {
            cancelInternal(taskId)
            return
        }

        val nextRunAt = task.nextRunAt ?: repository.computeNextRunAt(task).also {
            repository.updateNextRunAndStatus(taskId, it, TaskStatus.SCHEDULED)
        }
        cancelInternal(taskId)

        val delayMs = nextRunAt - System.currentTimeMillis()
        when {
            delayMs <= 0L -> {
                logger.info("任务 $taskId 已到期，立即执行")
                enqueueWorker(taskId, 0L)
            }
            delayMs < FIFTEEN_MIN_MS -> {
                logger.info("任务 $taskId 使用 AlarmManager，delay=${delayMs}ms")
                scheduleAlarm(taskId, nextRunAt)
            }
            else -> {
                logger.info("任务 $taskId 使用 WorkManager，delay=${delayMs}ms")
                enqueueWorker(taskId, delayMs)
            }
        }
        repository.updateNextRunAndStatus(taskId, nextRunAt, TaskStatus.SCHEDULED)
    }

    suspend fun cancelTask(taskId: String) = mutex.withLock {
        cancelInternal(taskId)
    }

    suspend fun rescheduleAllEnabled() {
        val tasks = repository.getEnabledTasks()
        logger.info("rescheduleAllEnabled: ${tasks.size} 个任务")
        tasks.forEach { task ->
            runCatching { scheduleTask(task.id) }
                .onFailure { logger.error("重新调度失败 ${task.id}: ${it.message}") }
        }
    }

    /** 立即执行，不改变周期调度状态。 */
    suspend fun runNow(taskId: String) {
        val task = repository.getTask(taskId) ?: error("任务不存在：$taskId")
        enqueueWorker(task.id, 0L)
    }

    private fun cancelInternal(taskId: String) {
        val workName = workNameOf(taskId)
        WorkManager.getInstance(context).cancelUniqueWork(workName)
        workNames.remove(taskId)
        cancelAlarm(taskId)
    }

    private fun enqueueWorker(taskId: String, delayMs: Long) {
        val workName = workNameOf(taskId)
        val builder = OneTimeWorkRequestBuilder<SchedulerTaskWorker>()
            .setInputData(workDataOf(SchedulerTaskWorker.KEY_TASK_ID to taskId))
            .addTag(TAG_SCHEDULER)
            .addTag(tagOf(taskId))

        if (delayMs > 0) {
            builder.setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
        } else {
            // 立即/近端任务尽量 expedited
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            builder.build()
        )
        workNames[taskId] = workName
    }

    private fun scheduleAlarm(taskId: String, triggerAtMs: Long) {
        val pending = alarmPendingIntent(taskId, create = true) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                logger.warning("无精确闹钟权限，回退 WorkManager: $taskId")
                val delay = (triggerAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
                enqueueWorker(taskId, delay)
                return
            }
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMs,
                pending
            )
        } catch (se: SecurityException) {
            logger.warning("setExact 失败，回退 WorkManager: ${se.message}")
            val delay = (triggerAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
            enqueueWorker(taskId, delay)
        }
    }

    private fun cancelAlarm(taskId: String) {
        val pending = alarmPendingIntent(taskId, create = false) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    private fun alarmPendingIntent(taskId: String, create: Boolean): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(SchedulerTaskWorker.KEY_TASK_ID, taskId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (create) {
            PendingIntent.getBroadcast(context, requestCodeOf(taskId), intent, flags)
        } else {
            PendingIntent.getBroadcast(
                context,
                requestCodeOf(taskId),
                intent,
                flags or PendingIntent.FLAG_NO_CREATE
            )
        }
    }

    private fun workNameOf(taskId: String) = "scheduler_task_$taskId"
    private fun tagOf(taskId: String) = "scheduler_tag_$taskId"
    private fun requestCodeOf(taskId: String) = taskId.hashCode()

    companion object {
        const val TAG_SCHEDULER = "lanxin_scheduler"
        private const val FIFTEEN_MIN_MS = 15 * 60 * 1000L
    }
}
