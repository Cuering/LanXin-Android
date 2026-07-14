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

package com.lanxin.android.builtin.scheduler.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.builtin.scheduler.domain.CrontabParser
import com.lanxin.android.builtin.scheduler.domain.SchedulerEngine
import com.lanxin.android.builtin.scheduler.domain.SchedulerRepository
import com.lanxin.android.builtin.scheduler.domain.SchedulerTask
import com.lanxin.android.builtin.scheduler.domain.SchedulerTaskType
import com.lanxin.android.builtin.scheduler.registry.TaskActionRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class TaskEditViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val engine: SchedulerEngine,
    private val actionRegistry: TaskActionRegistry,
    private val cronParser: CrontabParser
) : ViewModel() {

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbarMessage = _snackbar.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved = _saved.asStateFlow()

    val actions: List<String> get() = actionRegistry.listActions()
    val presets: List<Pair<String, String>> get() = CrontabParser.PRESETS

    private val timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    fun clearSnackbar() {
        _snackbar.value = null
    }

    suspend fun getTask(id: String): SchedulerTask? = repository.getTask(id)

    fun humanReadable(cron: String): String = runCatching {
        cronParser.toHumanReadable(cron)
    }.getOrElse { "无效 cron" }

    fun previewNext(cron: String): String = runCatching {
        val next = repository.previewNext(cron)
        timeFmt.format(Instant.ofEpochMilli(next))
    }.getOrElse { "无法计算：$it" }

    fun validateCron(cron: String): String? = runCatching {
        cronParser.parse(cron)
        null
    }.getOrElse { it.message }

    fun save(
        taskId: String?,
        name: String,
        type: SchedulerTaskType,
        useCron: Boolean,
        cron: String,
        runAtMs: Long?,
        action: String,
        notificationTitle: String,
        notificationContent: String,
        autoStart: Boolean,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            runCatching {
                require(name.isNotBlank()) { "名称不能为空" }
                val payload = when (type) {
                    SchedulerTaskType.BASIC -> {
                        require(action.isNotBlank()) { "请选择 action" }
                        require(actionRegistry.getHandler(action) != null) {
                            "action 未注册：$action"
                        }
                        mapOf("action" to action)
                    }
                    SchedulerTaskType.ACTIVE_AGENT -> mapOf(
                        "notificationTitle" to notificationTitle.ifBlank { name },
                        "notificationContent" to notificationContent,
                        "prompt" to notificationContent
                    )
                }

                val cronExpr = if (useCron) {
                    require(cron.isNotBlank()) { "cron 不能为空" }
                    cronParser.parse(cron)
                    cron.trim()
                } else {
                    null
                }
                val runAt = if (!useCron) {
                    require(runAtMs != null) { "请选择执行时间" }
                    runAtMs
                } else {
                    null
                }

                val task = if (taskId.isNullOrBlank()) {
                    repository.create(
                        name = name,
                        type = type,
                        cronExpression = cronExpr,
                        runAt = runAt,
                        payload = payload,
                        autoStartConversation = autoStart,
                        enabled = enabled
                    )
                } else {
                    engine.cancelTask(taskId)
                    repository.updateFields(
                        id = taskId,
                        name = name,
                        type = type,
                        cronExpression = cronExpr,
                        clearCron = cronExpr == null,
                        runAt = runAt,
                        clearRunAt = runAt == null,
                        payload = payload,
                        autoStartConversation = autoStart,
                        enabled = enabled
                    )
                }

                if (task.enabled) {
                    engine.scheduleTask(task.id)
                } else {
                    engine.cancelTask(task.id)
                }
                _saved.value = true
                _snackbar.value = "已保存"
            }.onFailure {
                _snackbar.value = it.message ?: "保存失败"
            }
        }
    }
}
