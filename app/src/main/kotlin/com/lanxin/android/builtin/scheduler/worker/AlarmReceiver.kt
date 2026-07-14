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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf

/**
 * AlarmManager 精确闹钟触发后，立即 enqueue 0-delay Worker。
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_FIRE) return
        val taskId = intent.getStringExtra(SchedulerTaskWorker.KEY_TASK_ID) ?: return

        val workName = "scheduler_task_$taskId"
        val workRequest = OneTimeWorkRequestBuilder<SchedulerTaskWorker>()
            .setInputData(workDataOf(SchedulerTaskWorker.KEY_TASK_ID to taskId))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(SchedulerTaskWorker.TAG)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    companion object {
        const val ACTION_FIRE = "com.lanxin.android.scheduler.ACTION_FIRE"
    }
}
