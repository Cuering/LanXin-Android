package com.lanxin.android.presentation.ui.setting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanxin.android.R
import com.lanxin.android.data.ModelConstants
import com.lanxin.android.data.model.ClientType
import com.lanxin.android.plugins.chat.data.entity.PlatformV2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlatformScreen(
    modifier: Modifier = Modifier,
    onNavigationClick: () -> Unit,
    onSave: (PlatformV2) -> Unit,
    viewModel: AddPlatformViewModel = hiltViewModel()
) {
    var platformName by remember { mutableStateOf("") }
    var selectedClientType by remember { mutableStateOf(ClientType.OPENAI) }
    var clientTypeExpanded by remember { mutableStateOf(false) }
    var apiUrl by remember { mutableStateOf(ModelConstants.OPENAI_API_URL) }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var reasoningEnabled by remember { mutableStateOf(false) }

    val fetchState by viewModel.uiState.collectAsStateWithLifecycle()
    val supportsList = viewModel.supportsRemoteModelList(selectedClientType)
    val canFetch = supportsList && apiUrl.isNotBlank() && !fetchState.busy

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_platform)) },
                navigationIcon = {
                    IconButton(onClick = onNavigationClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.add_platform_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = platformName,
                onValueChange = { platformName = it },
                label = { Text(stringResource(R.string.platform_name)) },
                placeholder = { Text(stringResource(R.string.platform_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text(stringResource(R.string.platform_name_supporting))
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            ExposedDropdownMenuBox(
                expanded = clientTypeExpanded,
                onExpandedChange = { clientTypeExpanded = it }
            ) {
                OutlinedTextField(
                    value = getClientTypeName(selectedClientType),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.api_type)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = clientTypeExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        .fillMaxWidth(),
                    supportingText = {
                        Text(getClientTypeDescription(selectedClientType))
                    }
                )

                ExposedDropdownMenu(
                    expanded = clientTypeExpanded,
                    onDismissRequest = { clientTypeExpanded = false }
                ) {
                    ClientType.entries.forEach { clientType ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = getClientTypeName(clientType),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = getClientTypeDescription(clientType),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                selectedClientType = clientType
                                apiUrl = when (clientType) {
                                    ClientType.OPENAI -> ModelConstants.OPENAI_API_URL
                                    ClientType.ANTHROPIC -> ModelConstants.ANTHROPIC_API_URL
                                    ClientType.GOOGLE -> ModelConstants.GOOGLE_API_URL
                                    ClientType.GROQ -> ModelConstants.GROQ_API_URL
                                    ClientType.OLLAMA -> ModelConstants.OLLAMA_API_URL
                                    ClientType.OPENROUTER -> ModelConstants.OPENROUTER_API_URL
                                    ClientType.CUSTOM -> ""
                                    ClientType.LANXIN -> ModelConstants.LANXIN_API_URL
                                }
                                viewModel.clearFetchState()
                                clientTypeExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = apiUrl,
                onValueChange = { apiUrl = it },
                label = { Text(stringResource(R.string.api_url)) },
                placeholder = { Text(stringResource(R.string.api_url_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.api_key)) },
                placeholder = { Text(stringResource(R.string.api_key_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = {
                    Text(stringResource(R.string.api_key_supporting))
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Fetch models + latency rank
            OutlinedButton(
                onClick = {
                    if (!supportsList) {
                        // state will show unsupported via fetch path
                    }
                    viewModel.fetchAndRankModels(
                        clientType = selectedClientType,
                        apiUrl = apiUrl,
                        apiKey = apiKey,
                        preferredModel = model.takeIf { it.isNotBlank() }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canFetch || (!supportsList && apiUrl.isNotBlank() && !fetchState.busy)
            ) {
                if (fetchState.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(18.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(
                    text = when {
                        fetchState.loading -> stringResource(R.string.fetch_remote_models_loading)
                        fetchState.ranking -> stringResource(R.string.fetch_remote_models_ranking)
                        else -> stringResource(R.string.fetch_models_button)
                    }
                )
            }

            when {
                !supportsList -> {
                    Text(
                        text = stringResource(R.string.fetch_models_unsupported),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                fetchState.error != null -> {
                    Text(
                        text = stringResource(
                            R.string.fetch_remote_models_error,
                            humanizeAddPlatformListError(fetchState.error!!)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                fetchState.models.isNotEmpty() -> {
                    Text(
                        text = if (fetchState.results.isNotEmpty()) {
                            stringResource(
                                R.string.fetch_remote_models_count_ranked,
                                fetchState.models.size,
                                fetchState.results.count { it.success }
                            )
                        } else {
                            stringResource(
                                R.string.fetch_remote_models_count,
                                fetchState.models.size
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.fetch_models_select_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                    val visible = fetchState.models.take(ADD_PLATFORM_MODEL_VISIBLE_LIMIT)
                    visible.forEach { modelId ->
                        val probe = fetchState.results.firstOrNull {
                            it.modelId.equals(modelId, ignoreCase = true)
                        }
                        val selected = modelId.equals(model, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { model = modelId }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = modelId,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                if (probe != null) {
                                    Text(
                                        text = if (probe.success) {
                                            stringResource(
                                                R.string.probe_result_ok_short,
                                                probe.latencyMs
                                            )
                                        } else {
                                            stringResource(
                                                R.string.probe_result_fail_short,
                                                probe.latencyMs
                                            )
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (probe.success) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (fetchState.models.size > ADD_PLATFORM_MODEL_VISIBLE_LIMIT) {
                        Text(
                            text = stringResource(
                                R.string.remote_models_truncated,
                                fetchState.models.size - ADD_PLATFORM_MODEL_VISIBLE_LIMIT
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
                supportsList && apiUrl.isBlank() -> {
                    Text(
                        text = stringResource(R.string.fetch_models_need_fields),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text(stringResource(R.string.model)) },
                placeholder = { Text(getModelPlaceholder(selectedClientType)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    Text(stringResource(R.string.model_supporting))
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.extended_thinking),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.extended_thinking_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = reasoningEnabled,
                    onCheckedChange = { reasoningEnabled = it }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val platform = PlatformV2(
                        name = platformName.trim(),
                        compatibleType = selectedClientType,
                        enabled = true,
                        apiUrl = apiUrl.trim(),
                        token = apiKey.trim().takeIf { it.isNotEmpty() },
                        model = model.trim(),
                        temperature = 1.0f,
                        topP = 1.0f,
                        systemPrompt = ModelConstants.DEFAULT_PROMPT,
                        stream = true,
                        reasoning = reasoningEnabled,
                        timeout = 30
                    )
                    onSave(platform)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = platformName.isNotBlank() && apiUrl.isNotBlank() && model.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onNavigationClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.cancel))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private const val ADD_PLATFORM_MODEL_VISIBLE_LIMIT = 40

@Composable
private fun humanizeAddPlatformListError(code: String): String = when (code) {
    "empty_api_url" -> stringResource(R.string.model_list_error_empty_url)
    "unsupported_type" -> stringResource(R.string.model_list_error_unsupported)
    "http_401" -> stringResource(R.string.model_list_error_401)
    "http_403" -> stringResource(R.string.model_list_error_403)
    "http_404" -> stringResource(R.string.model_list_error_404)
    "http_429" -> stringResource(R.string.model_list_error_429)
    "http_5xx" -> stringResource(R.string.model_list_error_5xx)
    "no_models" -> stringResource(R.string.model_list_error_no_models)
    "empty_body" -> stringResource(R.string.model_list_error_empty_body)
    "network_error" -> stringResource(R.string.model_list_error_network)
    "no_probe_targets" -> stringResource(R.string.probe_error_no_targets)
    else -> code
}

@Composable
private fun getClientTypeName(clientType: ClientType): String = when (clientType) {
    ClientType.OPENAI -> "OpenAI"
    ClientType.ANTHROPIC -> "Anthropic"
    ClientType.GOOGLE -> "Google"
    ClientType.GROQ -> "Groq"
    ClientType.OLLAMA -> "Ollama"
    ClientType.OPENROUTER -> "OpenRouter"
    ClientType.CUSTOM -> stringResource(R.string.custom)
    ClientType.LANXIN -> "兰心"
}

@Composable
private fun getClientTypeDescription(clientType: ClientType): String = when (clientType) {
    ClientType.OPENAI -> stringResource(R.string.client_type_openai_desc)
    ClientType.ANTHROPIC -> stringResource(R.string.client_type_anthropic_desc)
    ClientType.GOOGLE -> stringResource(R.string.client_type_google_desc)
    ClientType.GROQ -> stringResource(R.string.client_type_groq_desc)
    ClientType.OLLAMA -> stringResource(R.string.client_type_ollama_desc)
    ClientType.OPENROUTER -> stringResource(R.string.client_type_openrouter_desc)
    ClientType.CUSTOM -> stringResource(R.string.client_type_custom_desc)
    ClientType.LANXIN -> "专属定制的兰心 AI 助理"
}

@Composable
private fun getModelPlaceholder(clientType: ClientType): String = when (clientType) {
    ClientType.OPENAI -> "gpt-5.2"
    ClientType.ANTHROPIC -> "claude-sonnet-4-5-20250929"
    ClientType.GOOGLE -> "gemini-3-pro-preview"
    ClientType.GROQ -> "openai/gpt-oss-120b"
    ClientType.OLLAMA -> "gpt-oss"
    ClientType.OPENROUTER -> "openai/gpt-4o"
    ClientType.CUSTOM -> stringResource(R.string.model_name)
    ClientType.LANXIN -> "兰心"
}
