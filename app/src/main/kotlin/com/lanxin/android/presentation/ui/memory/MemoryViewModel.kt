package com.lanxin.android.presentation.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.data.memory.MemoryEntity
import com.lanxin.android.data.memory.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _selectedType = MutableStateFlow<String?>(null)
    val selectedType: StateFlow<String?> = _selectedType.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    private val _editingMemory = MutableStateFlow<MemoryEntity?>(null)
    val editingMemory: StateFlow<MemoryEntity?> = _editingMemory.asStateFlow()

    private val _deleteConfirmId = MutableStateFlow<Long?>(null)
    val deleteConfirmId: StateFlow<Long?> = _deleteConfirmId.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val baseMemories = _selectedType.flatMapLatest { type ->
        if (type == null) memoryRepository.getAllMemories()
        else memoryRepository.getMemoriesByType(type)
    }

    val memories: StateFlow<List<MemoryEntity>> = combine(
        baseMemories,
        _searchQuery
    ) { list, query ->
        if (query.isBlank()) list
        else list.filter { it.content.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun filterByType(type: String?) {
        _selectedType.update { type }
    }

    fun search(query: String) {
        _searchQuery.update { query }
    }

    fun openAddDialog() {
        _editingMemory.update { null }
        _showAddDialog.update { true }
    }

    fun openEditDialog(memory: MemoryEntity) {
        _editingMemory.update { memory }
        _showAddDialog.update { true }
    }

    fun closeAddDialog() {
        _showAddDialog.update { false }
        _editingMemory.update { null }
    }

    fun requestDelete(id: Long) {
        _deleteConfirmId.update { id }
    }

    fun cancelDelete() {
        _deleteConfirmId.update { null }
    }

    fun addMemory(content: String, type: String, importance: Float) {
        viewModelScope.launch {
            _isLoading.update { true }
            try {
                memoryRepository.addMemory(content, type, importance)
                _snackbarMessage.update { "记忆已添加" }
                closeAddDialog()
            } finally {
                _isLoading.update { false }
            }
        }
    }

    fun updateMemory(memory: MemoryEntity, content: String, type: String, importance: Float) {
        viewModelScope.launch {
            _isLoading.update { true }
            try {
                memoryRepository.updateMemory(
                    memory.copy(content = content, type = type, importance = importance)
                )
                _snackbarMessage.update { "记忆已更新" }
                closeAddDialog()
            } finally {
                _isLoading.update { false }
            }
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(id)
            _deleteConfirmId.update { null }
            _snackbarMessage.update { "记忆已删除" }
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.update { null }
    }

    suspend fun getMemoryById(id: Long): MemoryEntity? = memoryRepository.getMemoryById(id)
}
