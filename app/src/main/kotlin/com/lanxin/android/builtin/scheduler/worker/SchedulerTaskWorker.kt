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

package com.lanxin.android.builtin.scheduler.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.lanxin.android.R
import com.lanxin.android.builtin.scheduler.domain.SchedulerEngine
import com.lanxin.android.builtin.scheduler.domain.SchedulerRepository
import com.lanxin.android.builtin.scheduler.domain.SchedulerTask
import com.lanxin.android.builtin.scheduler.domain.SchedulerTaskType
import com.lanxin.android.builtin.scheduler.domain.TaskStatus
import com.lanxin.android.builtin.scheduler.registry.TaskActionRegistry
import com.lanxin.android.core.log.LogManager
import com.lanxin.android.presentation.ui.main.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SchedulerTaskWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: SchedulerRepository,
    private val actionRegistry: TaskActionRegistry,
    private val schedulerEngine: SchedulerEngine,
    private val logManager: LogManager
) : CoroutineWorker(appContext, params) {

    private val logger get() = logManager.getLogger("SchedulerTaskWorker")

    override suspend fun getForegroundInfo(): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle("定时任务执行中")
            .setContentText("兰心正在处理后台任务…")
            .setSmallIcon(R.drawable.ic_rounded_chat)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val task = repository.getTask(taskId) ?: return Result.failure()

        if (!task.enabled) {
            logger.info("任务已禁用，跳过：$taskId")
            return Result.success()
        }

        runCatching { setForeground(getForegroundInfo()) }

        repository.markRunning(taskId)
        logger.info("开始执行任务 ${task.name} ($taskId) type=${task.type}")

        val execResult = try {
            when (task.type) {
                SchedulerTaskType.BASIC -> executeBasic(task)
                SchedulerTaskType.ACTIVE_AGENT -> executeActiveAgent(task)
            }
        } catch (e: Exception) {
            logger.error("任务执行异常：${e.message}")
            repository.updateLastError(taskId, e.message)
            return Result.retry()
        }

        if (execResult.isFailure) {
            val err = execResult.exceptionOrNull()?.message ?: "执行失败"
            repository.updateLastError(taskId, err)
            return Result.failure()
        }

        // 执行后处理：周期任务递归调度，一次性任务标记完成
        if (task.isPeriodic) {
            val next = repository.computeNextRunAt(task)
            repository.updateNextRunAndStatus(taskId, next, TaskStatus.SCHEDULED)
            schedulerEngine.scheduleTask(taskId)
            logger.info("周期任务已重新调度 nextRunAt=$next")
        } else {
            repository.updateNextRunAndStatus(taskId, null, TaskStatus.COMPLETED)
            schedulerEngine.cancelTask(taskId)
            logger.info("一次性任务完成")
        }
        return Result.success()
    }

    private suspend fun executeBasic(task: SchedulerTask): kotlin.Result<Unit> {
        val action = task.payload["action"]
            ?: return kotlin.Result.failure(IllegalArgumentException("缺少 action"))
        val handler = actionRegistry.getHandler(action)
            ?: return kotlin.Result.failure(IllegalArgumentException("未注册 action：$action"))
        return handler.execute(appContext, task.payload)
    }

    private fun executeActiveAgent(task: SchedulerTask): kotlin.Result<Unit> {
        ensureChannel()
        val title = task.payload["notificationTitle"]
            ?: task.payload["title"]
            ?: task.name
        val content = task.payload["notificationContent"]
            ?: task.payload["prompt"]
            ?: task.payload["content"]
            ?: ""

        val autoStartIntent = chatIntent(
            taskId = task.id,
            prompt = content,
            autoStart = true
        )
        val viewOnlyIntent = chatIntent(
            taskId = task.id,
            prompt = content,
            autoStart = false
        )
        val defaultIntent = chatIntent(
            taskId = task.id,
            prompt = content,
            autoStart = task.autoStartConversation
        )

        val autoStartPi = PendingIntent.getActivity(
            appContext,
            task.id.hashCode() + 1,
            autoStartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val viewOnlyPi = PendingIntent.getActivity(
            appContext,
            task.id.hashCode() + 2,
            viewOnlyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentPi = PendingIntent.getActivity(
            appContext,
            task.id.hashCode(),
            defaultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSmallIcon(R.drawable.ic_rounded_chat)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(R.drawable.ic_send, "立即对话", autoStartPi)
            .addAction(R.drawable.ic_rounded_chat, "仅查看", viewOnlyPi)
            .build()

        return try {
            NotificationManagerCompat.from(appContext)
                .notify(task.id.hashCode(), notification)
            kotlin.Result.success(Unit)
        } catch (se: SecurityException) {
            kotlin.Result.failure(se)
        }
    }

    private fun chatIntent(taskId: String, prompt: String, autoStart: Boolean): Intent {
        return Intent(appContext, MainActivity::class.java).apply {
            action = ACTION_SCHEDULER_CHAT
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_PROMPT, prompt)
            putExtra(EXTRA_AUTO_START, autoStart)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "定时任务",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "ACTIVE_AGENT 提醒与任务执行通知"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val TAG = "SchedulerTaskWorker"
        const val CHANNEL_ID = "lanxin_scheduler"
        const val FOREGROUND_NOTIFICATION_ID = 71001

        const val ACTION_SCHEDULER_CHAT = "com.lanxin.android.scheduler.CHAT"
        const val EXTRA_TASK_ID = "scheduler_task_id"
        const val EXTRA_PROMPT = "scheduler_prompt"
        const val EXTRA_AUTO_START = "scheduler_auto_start"
    }
}
