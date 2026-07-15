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

package com.lanxin.android.builtin.unifiedsearch.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.builtin.unifiedsearch.domain.SearchRoute
import com.lanxin.android.builtin.unifiedsearch.domain.UnifiedSearchResult
import com.lanxin.android.builtin.unifiedsearch.domain.UnifiedSearchService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class UnifiedSearchViewModel @Inject constructor(
    private val unifiedSearchService: UnifiedSearchService
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _result = MutableStateFlow<UnifiedSearchResult?>(null)
    val result: StateFlow<UnifiedSearchResult?> = _result.asStateFlow()

    private val _enabled = MutableStateFlow(unifiedSearchService.enabled)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _routeToggles = MutableStateFlow(
        mapOf(
            SearchRoute.MEMORY to unifiedSearchService.memoryEnabled,
            SearchRoute.KNOWLEDGE to unifiedSearchService.knowledgeEnabled,
            SearchRoute.CHAT to unifiedSearchService.chatEnabled,
            SearchRoute.UNIFIED_INBOX to unifiedSearchService.unifiedInboxEnabled
        )
    )
    val routeToggles: StateFlow<Map<SearchRoute, Boolean>> = _routeToggles.asStateFlow()

    fun onQueryChange(value: String) {
        _query.update { value }
    }

    fun setEnabled(value: Boolean) {
        unifiedSearchService.enabled = value
        _enabled.update { value }
    }

    fun setRouteEnabled(route: SearchRoute, value: Boolean) {
        when (route) {
            SearchRoute.MEMORY -> unifiedSearchService.memoryEnabled = value
            SearchRoute.KNOWLEDGE -> unifiedSearchService.knowledgeEnabled = value
            SearchRoute.CHAT -> unifiedSearchService.chatEnabled = value
            SearchRoute.UNIFIED_INBOX -> unifiedSearchService.unifiedInboxEnabled = value
        }
        _routeToggles.update { it + (route to value) }
    }

    fun search() {
        val q = _query.value.trim()
        if (q.isBlank() || _isSearching.value) return
        viewModelScope.launch {
            _isSearching.update { true }
            try {
                _result.update { unifiedSearchService.search(q) }
            } finally {
                _isSearching.update { false }
            }
        }
    }

    fun lastHitCounts(): Map<SearchRoute, Int> = unifiedSearchService.lastRouteHitCounts
}
