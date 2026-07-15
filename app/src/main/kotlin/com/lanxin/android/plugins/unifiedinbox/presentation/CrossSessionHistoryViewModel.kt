package com.lanxin.android.plugins.unifiedinbox.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.plugins.unifiedinbox.data.CrossSessionEntity
import com.lanxin.android.plugins.unifiedinbox.data.CrossSessionRepository
import com.lanxin.android.plugins.unifiedinbox.data.CrossSessionSummaryRow
import com.lanxin.android.plugins.unifiedinbox.domain.CrossSessionIndexer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CrossSessionHistoryViewModel @Inject constructor(
    private val repository: CrossSessionRepository,
    private val indexer: CrossSessionIndexer
) : ViewModel() {

    data class UiState(
        val messages: List<CrossSessionEntity> = emptyList(),
        val sessions: List<CrossSessionSummaryRow> = emptyList(),
        val platforms: List<String> = emptyList(),
        val query: String = "",
        val platformFilter: String = "",
        val sessionFilter: String = "",
        val totalCount: Int = 0,
        val isLoading: Boolean = false,
        val isIndexing: Boolean = false,
        val indexInfo: String? = null,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val state = _uiState.value
                val messages = repository.search(
                    query = state.query,
                    platform = state.platformFilter,
                    sessionId = state.sessionFilter,
                    limit = 300
                )
                val sessions = repository.listSessions()
                val platforms = repository.listPlatforms()
                val count = repository.count()
                _uiState.update {
                    it.copy(
                        messages = messages,
                        sessions = sessions,
                        platforms = platforms,
                        totalCount = count,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "加载失败")
                }
            }
        }
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        refresh()
    }

    fun setPlatformFilter(platform: String) {
        _uiState.update { it.copy(platformFilter = platform) }
        refresh()
    }

    fun setSessionFilter(sessionId: String) {
        _uiState.update { it.copy(sessionFilter = sessionId) }
        refresh()
    }

    fun reindex() {
        viewModelScope.launch {
            _uiState.update { it.copy(isIndexing = true, error = null, indexInfo = null) }
            try {
                val result = indexer.reindexAll()
                _uiState.update {
                    it.copy(
                        isIndexing = false,
                        indexInfo = "已索引 ${result.sessions} 个会话 / ${result.messages} 条消息（${result.durationMs}ms）"
                    )
                }
                refresh()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isIndexing = false, error = e.message ?: "索引失败")
                }
            }
        }
    }
}
