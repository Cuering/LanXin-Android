package com.lanxin.android.presentation.ui.setup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.data.ModelConstants
import com.lanxin.android.plugins.chat.data.entity.PlatformV2
import com.lanxin.android.data.model.ClientType
import com.lanxin.android.data.network.LanXinAuthClient
import com.lanxin.android.data.repository.SettingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class SaveStatus {
    data object Idle : SaveStatus()
    data object Saving : SaveStatus()
    data object Success : SaveStatus()
    data class Error(val message: String) : SaveStatus()
}

@HiltViewModel
class SetupViewModelV2 @Inject constructor(
    private val settingRepository: SettingRepository,
    val lanXinAuthClient: LanXinAuthClient
) : ViewModel() {

    private val _platforms = MutableStateFlow<List<PlatformV2>>(emptyList())
    val platforms: StateFlow<List<PlatformV2>> = _platforms.asStateFlow()

    // Wizard state for adding a new platform
    private val _wizardStep = MutableStateFlow(0)
    val wizardStep: StateFlow<Int> = _wizardStep.asStateFlow()

    private val _selectedClientType = MutableStateFlow<ClientType?>(null)
    val selectedClientType: StateFlow<ClientType?> = _selectedClientType.asStateFlow()

    private val _platformName = MutableStateFlow("")
    val platformName: StateFlow<String> = _platformName.asStateFlow()

    private val _apiUrl = MutableStateFlow("")
    val apiUrl: StateFlow<String> = _apiUrl.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _model = MutableStateFlow("")
    val model: StateFlow<String> = _model.asStateFlow()

    private val _saveStatus = MutableStateFlow<SaveStatus>(SaveStatus.Idle)
    val saveStatus: StateFlow<SaveStatus> = _saveStatus.asStateFlow()

    // Login dialog state
    private val _showLoginDialog = MutableStateFlow(false)
    val showLoginDialog: StateFlow<Boolean> = _showLoginDialog.asStateFlow()

    private val _loginStatus = MutableStateFlow<SaveStatus>(SaveStatus.Idle)
    val loginStatus: StateFlow<SaveStatus> = _loginStatus.asStateFlow()

    init {
        loadPlatforms()
    }

    private fun loadPlatforms() {
        viewModelScope.launch {
            val existingPlatforms = settingRepository.fetchPlatformV2s()
            _platforms.value = existingPlatforms
        }
    }

    fun selectClientType(clientType: ClientType) {
        _selectedClientType.value = clientType
        _platformName.value = getDefaultPlatformName(clientType)
        _apiUrl.value = getDefaultApiUrl(clientType)
        _apiKey.value = ""
        _model.value = ""
        _wizardStep.value = 0
    }

    fun updatePlatformName(name: String) {
        _platformName.value = name
    }

    fun updateApiUrl(url: String) {
        _apiUrl.value = url
    }

    fun updateApiKey(key: String) {
        _apiKey.value = key
    }

    fun updateModel(modelName: String) {
        _model.value = modelName
    }

    fun nextWizardStep() {
        _wizardStep.update { it + 1 }
    }

    fun previousWizardStep() {
        _wizardStep.update { maxOf(0, it - 1) }
    }

    fun resetWizard() {
        _wizardStep.value = 0
        _selectedClientType.value = null
        _platformName.value = ""
        _apiUrl.value = ""
        _apiKey.value = ""
        _model.value = ""
    }

    fun savePlatform() {
        val clientType = _selectedClientType.value ?: return

        viewModelScope.launch {
            _saveStatus.value = SaveStatus.Saving
            try {
                val platform = PlatformV2(
                    name = _platformName.value.trim(),
                    compatibleType = clientType,
                    enabled = true,
                    apiUrl = _apiUrl.value.trim(),
                    token = _apiKey.value.trim().takeIf { it.isNotEmpty() },
                    model = _model.value.trim(),
                    temperature = 1.0f,
                    topP = 1.0f,
                    systemPrompt = ModelConstants.DEFAULT_PROMPT,
                    stream = true,
                    reasoning = false,
                    // LanXin goes through full AstrBot agent (tools/search); need longer socket timeout
                    timeout = if (clientType == ClientType.LANXIN) 180 else 30
                )
                settingRepository.addPlatformV2(platform)
                loadPlatforms()
                _saveStatus.value = SaveStatus.Success
                resetWizard()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save platform", e)
                val errorMessage = when (e) {
                    is android.database.sqlite.SQLiteConstraintException -> "A platform with this name already exists."
                    is android.database.sqlite.SQLiteException -> "Database error: ${e.message}"
                    else -> e.message ?: "Unknown error occurred while saving platform."
                }
                _saveStatus.value = SaveStatus.Error(errorMessage)
            }
        }
    }

    fun clearSaveStatus() {
        _saveStatus.value = SaveStatus.Idle
    }

    fun deletePlatform(platform: PlatformV2) {
        viewModelScope.launch {
            settingRepository.deletePlatformV2(platform)
            loadPlatforms()
        }
    }

    // ==== Login functions for LANXIN ====
    fun showLoginDialog() {
        _showLoginDialog.value = true
        _loginStatus.value = SaveStatus.Idle
    }

    fun dismissLoginDialog() {
        _showLoginDialog.value = false
        _loginStatus.value = SaveStatus.Idle
    }

    fun performLogin(username: String, password: String) {
        val url = _apiUrl.value.trim()
        if (url.isBlank() || username.isBlank() || password.isBlank()) return

        viewModelScope.launch {
            _loginStatus.value = SaveStatus.Saving
            val result = lanXinAuthClient.login(url, username, password)
            if (result.success && result.token != null) {
                _apiKey.value = result.token
                settingRepository.setLanXinUserName(username)
                _loginStatus.value = SaveStatus.Success
                _showLoginDialog.value = false
            } else {
                _loginStatus.value = SaveStatus.Error(result.message)
            }
        }
    }

    fun canProceedFromStep(step: Int): Boolean = when (step) {
        0 -> _platformName.value.isNotBlank() && _apiUrl.value.isNotBlank()

        1 -> true

        // API key is optional for some providers (e.g., Ollama)
        2 -> _model.value.isNotBlank()

        else -> false
    }

    fun isSetupComplete(): Boolean = _platforms.value.isNotEmpty()

    private fun getDefaultPlatformName(clientType: ClientType): String = when (clientType) {
        ClientType.OPENAI -> "OpenAI"
        ClientType.ANTHROPIC -> "Anthropic"
        ClientType.GOOGLE -> "Google"
        ClientType.GROQ -> "Groq"
        ClientType.OLLAMA -> "Ollama"
        ClientType.OPENROUTER -> "OpenRouter"
        ClientType.CUSTOM -> ""
        ClientType.LANXIN -> "兰心"
    }

    private fun getDefaultApiUrl(clientType: ClientType): String = when (clientType) {
        ClientType.OPENAI -> ModelConstants.OPENAI_API_URL
        ClientType.ANTHROPIC -> ModelConstants.ANTHROPIC_API_URL
        ClientType.GOOGLE -> ModelConstants.GOOGLE_API_URL
        ClientType.GROQ -> ModelConstants.GROQ_API_URL
        ClientType.OLLAMA -> ModelConstants.OLLAMA_API_URL
        ClientType.OPENROUTER -> ModelConstants.OPENROUTER_API_URL
        ClientType.CUSTOM -> ""
        ClientType.LANXIN -> ModelConstants.LANXIN_API_URL
    }

    companion object {
        private const val TAG = "SetupViewModelV2"
        const val WIZARD_STEP_BASICS = 0
        const val WIZARD_STEP_API_KEY = 1
        const val WIZARD_STEP_MODEL = 2
        const val WIZARD_TOTAL_STEPS = 3
    }
}
