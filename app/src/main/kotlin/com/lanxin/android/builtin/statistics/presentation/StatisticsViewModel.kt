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

package com.lanxin.android.builtin.statistics.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.builtin.statistics.domain.StatisticsRepository
import com.lanxin.android.builtin.statistics.domain.StatisticsSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) : ViewModel() {

    private val _summary = MutableStateFlow(StatisticsSummary())
    val summary: StateFlow<StatisticsSummary> = _summary.asStateFlow()

    private val _rangeDays = MutableStateFlow(7)
    val rangeDays: StateFlow<Int> = _rangeDays.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _clearConfirm = MutableStateFlow(false)
    val clearConfirm: StateFlow<Boolean> = _clearConfirm.asStateFlow()

    init {
        refresh()
    }

    fun setRangeDays(days: Int) {
        _rangeDays.value = days.coerceIn(1, 90)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                statisticsRepository.getSummary(_rangeDays.value)
            }.onSuccess { data ->
                _summary.value = data
            }.onFailure {
                _snackbarMessage.value = "加载统计失败：${it.message ?: "未知错误"}"
            }
            _isLoading.value = false
        }
    }

    fun requestClear() {
        _clearConfirm.value = true
    }

    fun cancelClear() {
        _clearConfirm.value = false
    }

    fun confirmClear() {
        viewModelScope.launch {
            _clearConfirm.value = false
            runCatching {
                statisticsRepository.clearAll()
            }.onSuccess {
                _summary.value = StatisticsSummary(rangeDays = _rangeDays.value)
                _snackbarMessage.value = "统计数据已清空"
            }.onFailure {
                _snackbarMessage.value = "清空失败：${it.message ?: "未知错误"}"
            }
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.update { null }
    }
}
