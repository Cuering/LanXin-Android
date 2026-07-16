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

package com.lanxin.android.presentation.ui.plugin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.plugin.PluginCatalog
import com.lanxin.android.plugin.dynamic.PluginLoadResult
import com.lanxin.android.plugin.dynamic.PluginRecord
import com.lanxin.android.plugin.dynamic.PluginSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 插件管理 UI 状态（Phase 5.4 + 5.6 签名策略展示）。
 */
data class PluginManagerUiState(
    val records: List<PluginRecord> = emptyList(),
    val failures: List<PluginLoadResult.Failure> = emptyList(),
    val packagesPath: String = "",
    val signaturePolicy: String = "",
    val isLoading: Boolean = false,
    val snackbarMessage: String? = null,
    /** 待确认卸载的动态插件 id。 */
    val unloadConfirmId: String? = null,
    /** 待确认删除 APK 的动态插件 id。 */
    val deleteApkConfirmId: String? = null
)

@HiltViewModel
class PluginManagerViewModel @Inject constructor(
    private val catalog: PluginCatalog
) : ViewModel() {

    private val _uiState = MutableStateFlow(PluginManagerUiState())
    val uiState: StateFlow<PluginManagerUiState> = _uiState.asStateFlow()

    init {
        refresh(scanDynamic = false)
    }

    /**
     * 刷新列表。
     * @param scanDynamic 为 true 时先执行 discoverAndLoadDynamicPlugins。
     */
    fun refresh(scanDynamic: Boolean = false) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val packagesPath = runCatching { catalog.packagesDirectory().absolutePath }
                    .getOrDefault("")
                val policy = runCatching { catalog.currentSignaturePolicy() }.getOrDefault("")
                if (scanDynamic) {
                    val result = catalog.discoverAndLoadDynamicPlugins()
                    val msg = buildString {
                        append("扫描完成：成功 ${result.successes.size}")
                        if (result.failures.isNotEmpty()) {
                            append("，失败 ${result.failures.size}")
                        }
                    }
                    _uiState.update {
                        it.copy(
                            records = catalog.getPluginRecords(),
                            failures = catalog.getLastDynamicFailures(),
                            packagesPath = packagesPath,
                            signaturePolicy = policy,
                            isLoading = false,
                            snackbarMessage = msg
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            records = catalog.getPluginRecords(),
                            failures = catalog.getLastDynamicFailures(),
                            packagesPath = packagesPath,
                            signaturePolicy = policy,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        snackbarMessage = "刷新失败：${e.message ?: "未知错误"}"
                    )
                }
            }
        }
    }

    fun setEnabled(pluginId: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val ok = catalog.setEnabled(pluginId, enabled)
                _uiState.update {
                    it.copy(
                        records = catalog.getPluginRecords(),
                        snackbarMessage = when {
                            !ok -> "插件 $pluginId 未注册，已写入启用状态"
                            enabled -> "已启用 $pluginId"
                            else -> "已停用 $pluginId"
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(snackbarMessage = "切换失败：${e.message ?: "未知错误"}")
                }
            }
        }
    }

    fun requestUnload(pluginId: String) {
        val record = _uiState.value.records.firstOrNull { it.id == pluginId } ?: return
        if (record.source != PluginSource.DYNAMIC || !record.removable) {
            _uiState.update { it.copy(snackbarMessage = "编译期插件不可卸载") }
            return
        }
        _uiState.update { it.copy(unloadConfirmId = pluginId) }
    }

    fun cancelUnload() {
        _uiState.update { it.copy(unloadConfirmId = null) }
    }

    fun confirmUnload() {
        val id = _uiState.value.unloadConfirmId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(unloadConfirmId = null, isLoading = true) }
            try {
                val ok = catalog.unloadPlugin(id)
                _uiState.update {
                    it.copy(
                        records = catalog.getPluginRecords(),
                        failures = catalog.getLastDynamicFailures(),
                        isLoading = false,
                        snackbarMessage = if (ok) {
                            "已卸载 $id（APK 文件仍保留，可删除或重新扫描）"
                        } else {
                            "卸载失败：$id（可能为编译期插件）"
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        snackbarMessage = "卸载异常：${e.message ?: "未知错误"}"
                    )
                }
            }
        }
    }

    fun requestDeleteApk(pluginId: String) {
        val record = _uiState.value.records.firstOrNull { it.id == pluginId } ?: return
        if (record.source != PluginSource.DYNAMIC || record.apkPath.isNullOrBlank()) {
            _uiState.update { it.copy(snackbarMessage = "无可删除的 APK") }
            return
        }
        _uiState.update { it.copy(deleteApkConfirmId = pluginId) }
    }

    fun cancelDeleteApk() {
        _uiState.update { it.copy(deleteApkConfirmId = null) }
    }

    /**
     * 卸载动态插件并从磁盘删除 APK（谨慎操作）。
     */
    fun confirmDeleteApk() {
        val id = _uiState.value.deleteApkConfirmId ?: return
        val apkPath = _uiState.value.records.firstOrNull { it.id == id }?.apkPath
        viewModelScope.launch {
            _uiState.update { it.copy(deleteApkConfirmId = null, isLoading = true) }
            try {
                catalog.unloadPlugin(id)
                var deleted = false
                if (!apkPath.isNullOrBlank()) {
                    deleted = runCatching {
                        val f = File(apkPath)
                        f.isFile && f.delete()
                    }.getOrDefault(false)
                }
                _uiState.update {
                    it.copy(
                        records = catalog.getPluginRecords(),
                        failures = catalog.getLastDynamicFailures(),
                        isLoading = false,
                        snackbarMessage = if (deleted) {
                            "已卸载并删除 APK：$id"
                        } else {
                            "已卸载 $id，但 APK 未删除（可能已被移除）"
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        snackbarMessage = "删除失败：${e.message ?: "未知错误"}"
                    )
                }
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
