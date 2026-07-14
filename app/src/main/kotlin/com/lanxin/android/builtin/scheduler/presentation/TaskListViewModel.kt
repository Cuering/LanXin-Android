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
import com.lanxin.android.builtin.scheduler.domain.SchedulerEngine
import com.lanxin.android.builtin.scheduler.domain.SchedulerRepository
import com.lanxin.android.builtin.scheduler.domain.SchedulerTask
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val repository: SchedulerRepository,
    private val engine: SchedulerEngine
) : ViewModel() {

    val tasks: StateFlow<List<SchedulerTask>> = repository.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbarMessage = _snackbar.asStateFlow()

    private val _deleteConfirmId = MutableStateFlow<String?>(null)
    val deleteConfirmId = _deleteConfirmId.asStateFlow()

    fun clearSnackbar() {
        _snackbar.value = null
    }

    fun requestDelete(id: String) {
        _deleteConfirmId.value = id
    }

    fun cancelDelete() {
        _deleteConfirmId.value = null
    }

    fun confirmDelete() {
        val id = _deleteConfirmId.value ?: return
        _deleteConfirmId.value = null
        viewModelScope.launch {
            runCatching {
                engine.cancelTask(id)
                repository.delete(id)
            }.onSuccess {
                _snackbar.value = "已删除"
            }.onFailure {
                _snackbar.value = it.message ?: "删除失败"
            }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (enabled) {
                    val task = repository.setEnabled(id, true)
                    engine.scheduleTask(task.id)
                } else {
                    engine.cancelTask(id)
                    repository.setEnabled(id, false)
                }
            }.onFailure {
                _snackbar.value = it.message ?: "更新失败"
            }
        }
    }

    fun runNow(id: String) {
        viewModelScope.launch {
            runCatching {
                engine.runNow(id)
            }.onSuccess {
                _snackbar.value = "已触发立即执行"
            }.onFailure {
                _snackbar.value = it.message ?: "执行失败"
            }
        }
    }

    fun cronHuman(cron: String?): String =
        if (cron.isNullOrBlank()) "一次性" else repository.humanReadable(cron)
}
