package com.lanxin.android.presentation.ui.setting

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.data.model.ClientType
import com.lanxin.android.data.model.GeminiSafetySettings
import com.lanxin.android.data.network.OpenAiModelListClient
import com.lanxin.android.data.network.OpenAiModelListResult
import com.lanxin.android.data.network.OpenAiModelProbeClient
import com.lanxin.android.data.network.OpenAiModelProbeSupport
import com.lanxin.android.data.network.ProviderModelListSupport
import com.lanxin.android.data.repository.SettingRepository
import com.lanxin.android.plugins.chat.data.entity.PlatformV2
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@HiltViewModel
class PlatformSettingViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val openAiModelListClient: OpenAiModelListClient,
    private val openAiModelProbeClient: OpenAiModelProbeClient,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val platformUid: String = checkNotNull(savedStateHandle["platformUid"])

    private val _platformState = MutableStateFlow<PlatformV2?>(null)
    val platformState: StateFlow<PlatformV2?> = _platformState.asStateFlow()

    private val _dialogState = MutableStateFlow(DialogState())
    val dialogState: StateFlow<DialogState> = _dialogState.asStateFlow()

    private val _isDeleted = MutableStateFlow(false)
    val isDeleted: StateFlow<Boolean> = _isDeleted.asStateFlow()

    private val _remoteModelListState = MutableStateFlow(RemoteModelListState())
    val remoteModelListState: StateFlow<RemoteModelListState> = _remoteModelListState.asStateFlow()

    private val _probeState = MutableStateFlow(ModelProbeState())
    val probeState: StateFlow<ModelProbeState> = _probeState.asStateFlow()

    private var autoFetchAttempted = false
    private var probeJob: Job? = null

    init {
        loadPlatform()
    }

    private fun loadPlatform() {
        viewModelScope.launch {
            val platforms = settingRepository.fetchPlatformV2s()
            val platform = platforms.firstOrNull { it.uid == platformUid }
            _platformState.update { platform }
            val supported = platform != null && supportsRemoteModelList(platform.compatibleType)
            _remoteModelListState.update {
                it.copy(supported = supported)
            }
            if (platform != null && supported) {
                maybeAutoFetch(platform)
            }
        }
    }

    fun supportsRemoteModelList(clientType: ClientType): Boolean =
        ProviderModelListSupport.supportsOpenAiCompatibleModelList(clientType)

    /**
     * Auto-fetch once when detail is opened and URL is usable.
     * Failure only surfaces error text; never clears existing [PlatformV2.model].
     */
    private fun maybeAutoFetch(platform: PlatformV2) {
        if (autoFetchAttempted) return
        if (!supportsRemoteModelList(platform.compatibleType)) return
        if (platform.apiUrl.isBlank()) return
        autoFetchAttempted = true
        fetchRemoteModels(triggeredByUser = false)
    }

    /** Manual refresh from settings UI. */
    fun fetchRemoteModels() = fetchRemoteModels(triggeredByUser = true)

    private fun fetchRemoteModels(triggeredByUser: Boolean) {
        val platform = _platformState.value ?: return
        if (!supportsRemoteModelList(platform.compatibleType)) {
            if (triggeredByUser) {
                _remoteModelListState.update {
                    it.copy(
                        supported = false,
                        loading = false,
                        error = OpenAiModelProbeSupport.humanizeListError("unsupported_type")
                    )
                }
            }
            return
        }
        if (_remoteModelListState.value.loading) return

        viewModelScope.launch {
            _remoteModelListState.update {
                it.copy(loading = true, error = null, autoFetch = !triggeredByUser)
            }
            when (
                val result = openAiModelListClient.listModels(
                    apiUrl = platform.apiUrl,
                    token = platform.token,
                    timeoutSeconds = platform.timeout.takeIf { it > 0 }
                        ?: OpenAiModelListClient.DEFAULT_TIMEOUT_SECONDS
                )
            ) {
                is OpenAiModelListResult.Success -> {
                    val previousChecked = _remoteModelListState.value.checkedModelIds
                    val current = platform.model
                    val nextChecked = LinkedHashSet<String>()
                    if (current.isNotBlank()) nextChecked.add(current)
                    previousChecked.forEach { id ->
                        if (result.modelIds.any { it.equals(id, ignoreCase = true) }) {
                            nextChecked.add(
                                result.modelIds.first { it.equals(id, ignoreCase = true) }
                            )
                        }
                    }
                    _remoteModelListState.update {
                        RemoteModelListState(
                            supported = true,
                            loading = false,
                            models = result.modelIds,
                            error = null,
                            lastFetchedAtMs = System.currentTimeMillis(),
                            filterQuery = it.filterQuery,
                            checkedModelIds = nextChecked,
                            autoFetch = !triggeredByUser
                        )
                    }
                }
                is OpenAiModelListResult.Error -> {
                    // Keep previous models; never clear platform.model
                    _remoteModelListState.update {
                        it.copy(
                            loading = false,
                            error = OpenAiModelProbeSupport.humanizeListError(result.message),
                            autoFetch = !triggeredByUser
                        )
                    }
                }
            }
        }
    }

    fun updateModelFilter(query: String) {
        _remoteModelListState.update { it.copy(filterQuery = query) }
    }

    fun filteredRemoteModels(): List<String> {
        val state = _remoteModelListState.value
        return OpenAiModelProbeSupport.filterModelIds(state.models, state.filterQuery)
    }

    fun toggleProbeCheck(modelId: String) {
        val id = modelId.trim()
        if (id.isEmpty()) return
        _remoteModelListState.update { state ->
            val next = LinkedHashSet(state.checkedModelIds)
            if (!next.add(id)) next.remove(id)
            // Soft cap so UI cannot select unbounded models for probe
            val capped = if (next.size > OpenAiModelProbeSupport.MAX_PROBE_MODELS) {
                next.toList().takeLast(OpenAiModelProbeSupport.MAX_PROBE_MODELS).toCollection(LinkedHashSet())
            } else {
                next
            }
            state.copy(checkedModelIds = capped)
        }
    }

    fun selectRemoteModel(modelId: String) {
        val trimmed = modelId.trim()
        if (trimmed.isEmpty()) return
        updateApiModel(trimmed)
        _remoteModelListState.update { state ->
            val next = LinkedHashSet(state.checkedModelIds)
            next.add(trimmed)
            state.copy(checkedModelIds = next)
        }
    }

    /**
     * Probe selected models (default: current model and/or checked).
     * Results stay in [probeState] only — never writes bad config.
     */
    fun probeSelectedModels() {
        val platform = _platformState.value ?: return
        if (!supportsRemoteModelList(platform.compatibleType)) return
        if (_probeState.value.running) return

        val listState = _remoteModelListState.value
        val targets = OpenAiModelProbeSupport.resolveProbeTargets(
            checkedModelIds = listState.checkedModelIds,
            currentModel = platform.model,
            availableModels = listState.models
        )
        if (targets.isEmpty()) {
            _probeState.update {
                ModelProbeState(
                    running = false,
                    results = emptyList(),
                    error = "no_probe_targets"
                )
            }
            return
        }

        probeJob?.cancel()
        probeJob = viewModelScope.launch {
            _probeState.update {
                ModelProbeState(running = true, results = emptyList(), error = null)
            }
            val timeout = platform.timeout.takeIf { it > 0 }
                ?: OpenAiModelProbeSupport.DEFAULT_TIMEOUT_SECONDS
            val semaphore = Semaphore(OpenAiModelProbeSupport.MAX_PROBE_CONCURRENCY)
            val results = coroutineScope {
                targets.map { modelId ->
                    async {
                        semaphore.withPermit {
                            openAiModelProbeClient.probe(
                                apiUrl = platform.apiUrl,
                                token = platform.token,
                                model = modelId,
                                timeoutSeconds = timeout
                            )
                        }
                    }
                }.awaitAll()
            }
            _probeState.update {
                ModelProbeState(
                    running = false,
                    results = results,
                    error = null,
                    lastProbedAtMs = System.currentTimeMillis()
                )
            }
        }
    }

    fun clearProbeResults() {
        probeJob?.cancel()
        _probeState.update { ModelProbeState() }
    }

    fun toggleEnabled() {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(enabled = !platform.enabled))
        }
    }

    fun toggleReasoning() {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(reasoning = !platform.reasoning))
        }
    }

    fun updatePlatform(platform: PlatformV2) {
        viewModelScope.launch {
            settingRepository.updatePlatformV2(platform)
            val previous = _platformState.value
            _platformState.update { platform }
            // Re-auto-fetch when URL/token become available after edit
            if (
                supportsRemoteModelList(platform.compatibleType) &&
                platform.apiUrl.isNotBlank() &&
                (
                    previous?.apiUrl != platform.apiUrl ||
                        previous.token != platform.token
                    ) &&
                _remoteModelListState.value.models.isEmpty() &&
                !_remoteModelListState.value.loading
            ) {
                autoFetchAttempted = false
                maybeAutoFetch(platform)
            }
        }
    }

    fun openPlatformNameDialog() = _dialogState.update { it.copy(isPlatformNameDialogOpen = true) }
    fun closePlatformNameDialog() = _dialogState.update { it.copy(isPlatformNameDialogOpen = false) }

    fun openApiUrlDialog() = _dialogState.update { it.copy(isApiUrlDialogOpen = true) }
    fun closeApiUrlDialog() = _dialogState.update { it.copy(isApiUrlDialogOpen = false) }

    fun openApiTokenDialog() = _dialogState.update { it.copy(isApiTokenDialogOpen = true) }
    fun closeApiTokenDialog() = _dialogState.update { it.copy(isApiTokenDialogOpen = false) }

    fun openApiModelDialog() = _dialogState.update { it.copy(isApiModelDialogOpen = true) }
    fun closeApiModelDialog() = _dialogState.update { it.copy(isApiModelDialogOpen = false) }

    fun openTemperatureDialog() = _dialogState.update { it.copy(isTemperatureDialogOpen = true) }
    fun closeTemperatureDialog() = _dialogState.update { it.copy(isTemperatureDialogOpen = false) }

    fun openTopPDialog() = _dialogState.update { it.copy(isTopPDialogOpen = true) }
    fun closeTopPDialog() = _dialogState.update { it.copy(isTopPDialogOpen = false) }

    fun openSystemPromptDialog() = _dialogState.update { it.copy(isSystemPromptDialogOpen = true) }
    fun closeSystemPromptDialog() = _dialogState.update { it.copy(isSystemPromptDialogOpen = false) }

    fun openTimeoutDialog() = _dialogState.update { it.copy(isTimeoutDialogOpen = true) }
    fun closeTimeoutDialog() = _dialogState.update { it.copy(isTimeoutDialogOpen = false) }

    fun openGeminiSafetyDialog() = _dialogState.update { it.copy(isGeminiSafetyDialogOpen = true) }
    fun closeGeminiSafetyDialog() = _dialogState.update { it.copy(isGeminiSafetyDialogOpen = false) }

    fun updatePlatformName(name: String) {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(name = name.trim()))
            closePlatformNameDialog()
        }
    }

    fun updateApiUrl(url: String) {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(apiUrl = url.trim()))
            closeApiUrlDialog()
        }
    }

    fun updateApiToken(token: String) {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(token = token.trim().takeIf { it.isNotEmpty() }))
            closeApiTokenDialog()
        }
    }

    fun updateApiModel(model: String) {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(model = model.trim()))
            closeApiModelDialog()
        }
    }

    fun updateTemperature(temperature: Float?) {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(temperature = temperature))
            closeTemperatureDialog()
        }
    }

    fun updateTopP(topP: Float?) {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(topP = topP))
            closeTopPDialog()
        }
    }

    fun updateSystemPrompt(prompt: String) {
        _platformState.value?.let { platform ->
            updatePlatform(platform.copy(systemPrompt = prompt.trim()))
            closeSystemPromptDialog()
        }
    }

    fun updateTimeout(timeoutSeconds: Int) {
        _platformState.value?.let { platform ->
            val normalizedTimeout = timeoutSeconds.coerceAtLeast(0)
            updatePlatform(platform.copy(timeout = normalizedTimeout))
            closeTimeoutDialog()
        }
    }

    fun updateGeminiSafetySettings(
        harassmentSafetyThreshold: String,
        hateSpeechSafetyThreshold: String,
        sexuallyExplicitSafetyThreshold: String,
        dangerousContentSafetyThreshold: String
    ) {
        _platformState.value?.let { platform ->
            updatePlatform(
                platform.copy(
                    harassmentSafetyThreshold = GeminiSafetySettings.normalizeThreshold(harassmentSafetyThreshold),
                    hateSpeechSafetyThreshold = GeminiSafetySettings.normalizeThreshold(hateSpeechSafetyThreshold),
                    sexuallyExplicitSafetyThreshold = GeminiSafetySettings.normalizeThreshold(sexuallyExplicitSafetyThreshold),
                    dangerousContentSafetyThreshold = GeminiSafetySettings.normalizeThreshold(dangerousContentSafetyThreshold)
                )
            )
            closeGeminiSafetyDialog()
        }
    }

    fun openDeleteDialog() = _dialogState.update { it.copy(isDeleteDialogOpen = true) }
    fun closeDeleteDialog() = _dialogState.update { it.copy(isDeleteDialogOpen = false) }

    fun deletePlatform() {
        _platformState.value?.let { platform ->
            viewModelScope.launch {
                settingRepository.deletePlatformV2(platform)
                closeDeleteDialog()
                _isDeleted.update { true }
            }
        }
    }

    data class DialogState(
        val isPlatformNameDialogOpen: Boolean = false,
        val isApiUrlDialogOpen: Boolean = false,
        val isApiTokenDialogOpen: Boolean = false,
        val isApiModelDialogOpen: Boolean = false,
        val isTemperatureDialogOpen: Boolean = false,
        val isTopPDialogOpen: Boolean = false,
        val isSystemPromptDialogOpen: Boolean = false,
        val isTimeoutDialogOpen: Boolean = false,
        val isGeminiSafetyDialogOpen: Boolean = false,
        val isDeleteDialogOpen: Boolean = false
    )

    data class RemoteModelListState(
        val supported: Boolean = false,
        val loading: Boolean = false,
        val models: List<String> = emptyList(),
        val error: String? = null,
        val lastFetchedAtMs: Long? = null,
        val filterQuery: String = "",
        val checkedModelIds: Set<String> = emptySet(),
        val autoFetch: Boolean = false
    )

    data class ModelProbeState(
        val running: Boolean = false,
        val results: List<com.lanxin.android.data.network.OpenAiModelProbeResult> = emptyList(),
        val error: String? = null,
        val lastProbedAtMs: Long? = null
    )
}
