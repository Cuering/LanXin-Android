package com.lanxin.android.plugins.unifiedinbox.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.plugins.unifiedinbox.domain.UnifiedFileBrowser
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class UnifiedFileBrowserViewModel @Inject constructor(
    private val browser: UnifiedFileBrowser
) : ViewModel() {

    data class UiState(
        val currentPath: String = "",
        val items: List<UnifiedFileBrowser.FileItem> = emptyList(),
        val preview: UnifiedFileBrowser.FilePreview? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        val canGoUp: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    init {
        openRoot()
    }

    fun openRoot() {
        val root = browser.defaultRoot().absolutePath
        openDirectory(root)
    }

    fun openDirectory(path: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, error = null, preview = null, currentPath = path)
            }
            try {
                val items = browser.listDirectory(path)
                val parent = browser.parentPath(path)
                _uiState.update {
                    it.copy(
                        items = items,
                        isLoading = false,
                        canGoUp = parent != null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "打开目录失败")
                }
            }
        }
    }

    fun openItem(item: UnifiedFileBrowser.FileItem) {
        if (item.isDirectory) {
            openDirectory(item.path)
        } else {
            previewFile(item.path)
        }
    }

    fun goUp() {
        viewModelScope.launch {
            val parent = browser.parentPath(_uiState.value.currentPath) ?: return@launch
            openDirectory(parent)
        }
    }

    fun previewFile(path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val preview = browser.preview(path)
                _uiState.update { it.copy(preview = preview, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "预览失败")
                }
            }
        }
    }

    fun clearPreview() {
        _uiState.update { it.copy(preview = null) }
    }

    fun refresh() {
        val path = _uiState.value.currentPath
        if (path.isBlank()) openRoot() else openDirectory(path)
    }
}
