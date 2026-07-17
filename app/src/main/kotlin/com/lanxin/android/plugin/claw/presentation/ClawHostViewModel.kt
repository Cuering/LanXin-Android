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

package com.lanxin.android.plugin.claw.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.plugin.PluginCatalog
import com.lanxin.android.plugin.claw.data.ClawResidentController
import com.lanxin.android.plugin.claw.data.DefaultPlatformHost
import com.lanxin.android.plugin.claw.domain.ClawHostSettings
import com.lanxin.android.plugin.dynamic.PluginSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClawHostUiState(
    val enabled: Boolean = false,
    val residentRequested: Boolean = false,
    val residentRunning: Boolean = false,
    val signaturePolicy: String = "",
    val dynamicPluginCount: Int = 0,
    val dynamicEnabledCount: Int = 0,
    val keepAliveCount: Int = 0,
    val packagesPath: String = "",
    val snackbarMessage: String? = null
)

@HiltViewModel
class ClawHostViewModel @Inject constructor(
    private val settings: ClawHostSettings,
    private val controller: ClawResidentController,
    private val platformHost: DefaultPlatformHost,
    private val catalog: PluginCatalog
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClawHostUiState())
    val uiState: StateFlow<ClawHostUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val config = settings.getConfig()
            val records = catalog.getPluginRecords()
            val dynamic = records.filter { it.source == PluginSource.DYNAMIC }
            _uiState.update {
                it.copy(
                    enabled = config.enabled,
                    residentRequested = config.residentRequested,
                    residentRunning = platformHost.isResidentRunning(),
                    signaturePolicy = catalog.currentSignaturePolicy(),
                    dynamicPluginCount = dynamic.size,
                    dynamicEnabledCount = dynamic.count { r -> r.enabled },
                    keepAliveCount = platformHost.keepAliveSnapshot().size,
                    packagesPath = catalog.packagesDirectory().absolutePath
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setEnabled(enabled)
            if (!enabled) {
                // 关总开关时同时取消常驻请求，避免幽灵服务
                settings.setResidentRequested(false)
            }
            controller.syncFromSettings()
            refresh()
            _uiState.update {
                it.copy(
                    snackbarMessage = if (enabled) {
                        "已开启 Claw 宿主（PlatformHost 对动态插件开放）"
                    } else {
                        "已关闭 Claw 宿主（默认安全；常驻已停）"
                    }
                )
            }
        }
    }

    fun setResidentRequested(requested: Boolean) {
        viewModelScope.launch {
            val config = settings.getConfig()
            if (requested && !config.enabled) {
                _uiState.update {
                    it.copy(snackbarMessage = "请先开启「Claw 宿主总开关」")
                }
                return@launch
            }
            settings.setResidentRequested(requested)
            controller.syncFromSettings()
            refresh()
            _uiState.update {
                it.copy(
                    snackbarMessage = if (requested) {
                        "已请求前台常驻（通知栏可见）"
                    } else {
                        "已停止前台常驻"
                    }
                )
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
