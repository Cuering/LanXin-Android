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

package com.lanxin.android.builtin.persona.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.builtin.persona.domain.Persona
import com.lanxin.android.builtin.persona.domain.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PersonaViewModel @Inject constructor(
    private val personaRepository: PersonaRepository
) : ViewModel() {

    val personas: StateFlow<List<Persona>> = personaRepository.personas
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentPersonaId: StateFlow<String> = personaRepository.currentPersonaId
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            com.lanxin.android.builtin.persona.domain.BuiltinPersonas.DEFAULT_ID
        )

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _deleteConfirmId = MutableStateFlow<String?>(null)
    val deleteConfirmId: StateFlow<String?> = _deleteConfirmId.asStateFlow()

    init {
        viewModelScope.launch {
            personaRepository.ensureSeeded()
        }
    }

    fun switchPersona(id: String) {
        viewModelScope.launch {
            val ok = personaRepository.switchPersona(id)
            _snackbarMessage.update {
                if (ok) "已切换人格" else "切换失败：人格不存在"
            }
        }
    }

    fun savePersona(id: String?, name: String, systemPrompt: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val trimmedName = name.trim()
            val trimmedPrompt = systemPrompt.trim()
            if (trimmedName.isEmpty()) {
                _snackbarMessage.update { "名称不能为空" }
                return@launch
            }
            if (trimmedPrompt.isEmpty()) {
                _snackbarMessage.update { "System Prompt 不能为空" }
                return@launch
            }

            if (id.isNullOrBlank()) {
                val created = personaRepository.createPersona(trimmedName, trimmedPrompt)
                _snackbarMessage.update { "人格已创建" }
                onDone(created.id)
            } else {
                val ok = personaRepository.updatePersona(id, trimmedName, trimmedPrompt)
                _snackbarMessage.update {
                    if (ok) "人格已更新" else "更新失败"
                }
                if (ok) onDone(id)
            }
        }
    }

    fun requestDelete(id: String) {
        _deleteConfirmId.update { id }
    }

    fun cancelDelete() {
        _deleteConfirmId.update { null }
    }

    fun confirmDelete() {
        val id = _deleteConfirmId.value ?: return
        viewModelScope.launch {
            val ok = personaRepository.deletePersona(id)
            _deleteConfirmId.update { null }
            _snackbarMessage.update {
                if (ok) "人格已删除" else "无法删除（内置人格或不存在）"
            }
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.update { null }
    }

    suspend fun getPersona(id: String): Persona? = personaRepository.getById(id)
}
