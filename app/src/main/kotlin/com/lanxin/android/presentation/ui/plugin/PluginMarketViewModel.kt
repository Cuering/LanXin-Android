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
import com.lanxin.android.plugin.market.DefaultPluginInstaller
import com.lanxin.android.plugin.market.InstallPhase
import com.lanxin.android.plugin.market.InstallProgress
import com.lanxin.android.plugin.market.MarketConfig
import com.lanxin.android.plugin.market.MarketDefaults
import com.lanxin.android.plugin.market.MarketInstallStatus
import com.lanxin.android.plugin.market.MarketPluginEntry
import com.lanxin.android.plugin.market.MarketPreferences
import com.lanxin.android.plugin.market.PluginInstallResult
import com.lanxin.android.plugin.market.PluginInstaller
import com.lanxin.android.plugin.market.PluginMarketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 市场列表行（条目 + 本地状态）。 */
data class MarketListItem(
    val entry: MarketPluginEntry,
    val installStatus: MarketInstallStatus,
    val localVersion: String? = null
)

data class PluginMarketUiState(
    val items: List<MarketListItem> = emptyList(),
    val query: String = "",
    val catalogUrl: String = MarketDefaults.DEFAULT_CATALOG_URL,
    val isLoading: Boolean = false,
    val isInstallingId: String? = null,
    val installPhase: InstallPhase = InstallPhase.IDLE,
    val installProgress: Float = 0f,
    val snackbarMessage: String? = null,
    val sourceHint: String = "",
    val draftCatalogUrl: String = "",
    val showUrlDialog: Boolean = false
)

@HiltViewModel
class PluginMarketViewModel @Inject constructor(
    private val marketRepository: PluginMarketRepository,
    private val installer: PluginInstaller,
    private val catalog: PluginCatalog,
    private val marketPreferences: MarketPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(PluginMarketUiState())
    val uiState: StateFlow<PluginMarketUiState> = _uiState.asStateFlow()

    private var allEntries: List<MarketPluginEntry> = emptyList()

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val url = marketPreferences.getCatalogUrl()
                val result = marketRepository.search(_uiState.value.query)
                val entries = result.getOrElse { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            catalogUrl = url,
                            snackbarMessage = "加载市场失败：${e.message ?: "未知错误"}"
                        )
                    }
                    return@launch
                }
                // search 已过滤；保留全量以便本地 query 切换
                val full = marketRepository.fetchCatalog().getOrNull()?.plugins ?: entries
                allEntries = full
                val filtered = filterEntries(full, _uiState.value.query)
                _uiState.update {
                    it.copy(
                        items = toListItems(filtered),
                        catalogUrl = url,
                        draftCatalogUrl = url,
                        isLoading = false,
                        sourceHint = "索引：$url",
                        snackbarMessage = "已加载 ${filtered.size} 个插件"
                    )
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

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        val filtered = filterEntries(allEntries, query)
        _uiState.update { it.copy(items = toListItems(filtered)) }
    }

    fun install(entry: MarketPluginEntry) {
        if (_uiState.value.isInstallingId != null) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isInstallingId = entry.id,
                    installPhase = InstallPhase.DOWNLOADING,
                    installProgress = 0f
                )
            }
            val result = installer.install(entry) { progress: InstallProgress ->
                _uiState.update {
                    it.copy(
                        installPhase = progress.phase,
                        installProgress = progress.progress
                    )
                }
            }
            when (result) {
                is PluginInstallResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isInstallingId = null,
                            installPhase = InstallPhase.DONE,
                            installProgress = 1f,
                            snackbarMessage = result.message.ifBlank {
                                "已安装 ${result.pluginId}"
                            },
                            items = toListItems(filterEntries(allEntries, it.query))
                        )
                    }
                }
                is PluginInstallResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isInstallingId = null,
                            installPhase = InstallPhase.FAILED,
                            installProgress = 0f,
                            snackbarMessage = result.reason,
                            items = toListItems(filterEntries(allEntries, it.query))
                        )
                    }
                }
            }
        }
    }

    fun openUrlDialog() {
        _uiState.update {
            it.copy(showUrlDialog = true, draftCatalogUrl = it.catalogUrl)
        }
    }

    fun cancelUrlDialog() {
        _uiState.update { it.copy(showUrlDialog = false) }
    }

    fun onDraftUrlChange(url: String) {
        _uiState.update { it.copy(draftCatalogUrl = url) }
    }

    fun saveCatalogUrl() {
        viewModelScope.launch {
            val url = _uiState.value.draftCatalogUrl.trim()
            marketPreferences.setCatalogUrl(url.ifBlank { null })
            _uiState.update {
                it.copy(
                    showUrlDialog = false,
                    catalogUrl = if (url.isBlank()) {
                        MarketDefaults.DEFAULT_CATALOG_URL
                    } else {
                        url
                    }
                )
            }
            refresh()
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun filterEntries(
        entries: List<MarketPluginEntry>,
        query: String
    ): List<MarketPluginEntry> {
        if (query.isBlank()) return entries
        val q = query.trim().lowercase()
        return entries.filter { entry ->
            entry.id.lowercase().contains(q) ||
                entry.name.lowercase().contains(q) ||
                entry.description.lowercase().contains(q) ||
                entry.author.lowercase().contains(q)
        }
    }

    private fun toListItems(entries: List<MarketPluginEntry>): List<MarketListItem> {
        val localById = catalog.getPluginRecords().associateBy { it.id }
        return entries.map { entry ->
            val local = localById[entry.id]
            val status = DefaultPluginInstaller.resolveInstallStatus(
                localVersion = local?.version,
                marketVersion = entry.version
            )
            MarketListItem(
                entry = entry,
                installStatus = status,
                localVersion = local?.version
            )
        }
    }
}
