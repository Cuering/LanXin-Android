package com.lanxin.android.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.data.network.OpenAiModelListClient
import com.lanxin.android.data.network.OpenAiModelListResult
import com.lanxin.android.data.network.OpenAiModelProbeClient
import com.lanxin.android.data.network.OpenAiModelProbeResult
import com.lanxin.android.data.network.OpenAiModelProbeSupport
import com.lanxin.android.data.network.ProviderModelListSupport
import com.lanxin.android.data.model.ClientType
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

/**
 * Add-provider form: fetch OpenAI-compatible model list, probe latency, rank fast → slow.
 */
@HiltViewModel
class AddPlatformViewModel @Inject constructor(
    private val openAiModelListClient: OpenAiModelListClient,
    private val openAiModelProbeClient: OpenAiModelProbeClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddPlatformFetchState())
    val uiState: StateFlow<AddPlatformFetchState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null

    fun supportsRemoteModelList(clientType: ClientType): Boolean =
        ProviderModelListSupport.supportsOpenAiCompatibleModelList(clientType)

    /**
     * List models from [apiUrl], measure latency for up to
     * [OpenAiModelProbeSupport.MAX_BULK_LATENCY_PROBE_MODELS], sort fast → slow.
     */
    fun fetchAndRankModels(
        clientType: ClientType,
        apiUrl: String,
        apiKey: String?,
        preferredModel: String? = null,
        timeoutSeconds: Int = OpenAiModelListClient.DEFAULT_TIMEOUT_SECONDS
    ) {
        if (!supportsRemoteModelList(clientType)) {
            _uiState.update {
                AddPlatformFetchState(
                    error = OpenAiModelProbeSupport.humanizeListError("unsupported_type")
                )
            }
            return
        }
        val url = apiUrl.trim()
        if (url.isEmpty()) {
            _uiState.update {
                AddPlatformFetchState(
                    error = OpenAiModelProbeSupport.humanizeListError("empty_api_url")
                )
            }
            return
        }
        if (_uiState.value.loading || _uiState.value.ranking) return

        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.update {
                AddPlatformFetchState(loading = true, ranking = false, error = null)
            }
            when (
                val listResult = openAiModelListClient.listModels(
                    apiUrl = url,
                    token = apiKey?.takeIf { it.isNotBlank() },
                    timeoutSeconds = timeoutSeconds.coerceAtLeast(5)
                )
            ) {
                is OpenAiModelListResult.Error -> {
                    _uiState.update {
                        AddPlatformFetchState(
                            loading = false,
                            error = OpenAiModelProbeSupport.humanizeListError(listResult.message)
                        )
                    }
                    return@launch
                }
                is OpenAiModelListResult.Success -> {
                    val ids = listResult.modelIds
                    _uiState.update {
                        it.copy(
                            loading = false,
                            ranking = true,
                            models = ids,
                            results = emptyList(),
                            error = null
                        )
                    }
                    val targets = OpenAiModelProbeSupport.resolveBulkLatencyTargets(
                        modelIds = ids,
                        preferredModel = preferredModel
                    )
                    val semaphore = Semaphore(OpenAiModelProbeSupport.MAX_BULK_PROBE_CONCURRENCY)
                    val probeTimeout = timeoutSeconds
                        .coerceAtLeast(OpenAiModelProbeSupport.DEFAULT_TIMEOUT_SECONDS)
                    val results = coroutineScope {
                        targets.map { modelId ->
                            async {
                                semaphore.withPermit {
                                    openAiModelProbeClient.probe(
                                        apiUrl = url,
                                        token = apiKey?.takeIf { it.isNotBlank() },
                                        model = modelId,
                                        timeoutSeconds = probeTimeout
                                    )
                                }
                            }
                        }.awaitAll()
                    }
                    val sortedResults = OpenAiModelProbeSupport.sortProbeResultsByLatency(results)
                    val rankedIds = OpenAiModelProbeSupport.sortModelIdsByLatency(ids, results)
                    _uiState.update {
                        AddPlatformFetchState(
                            loading = false,
                            ranking = false,
                            models = rankedIds,
                            results = sortedResults,
                            error = null,
                            lastFetchedAtMs = System.currentTimeMillis()
                        )
                    }
                }
            }
        }
    }

    fun clearFetchState() {
        fetchJob?.cancel()
        _uiState.update { AddPlatformFetchState() }
    }

    data class AddPlatformFetchState(
        val loading: Boolean = false,
        val ranking: Boolean = false,
        val models: List<String> = emptyList(),
        val results: List<OpenAiModelProbeResult> = emptyList(),
        val error: String? = null,
        val lastFetchedAtMs: Long? = null
    ) {
        val busy: Boolean get() = loading || ranking
    }
}
