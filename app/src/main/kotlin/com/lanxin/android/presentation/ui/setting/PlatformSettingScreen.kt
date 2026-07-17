package com.lanxin.android.presentation.ui.setting

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanxin.android.R
import com.lanxin.android.data.model.ClientType
import com.lanxin.android.data.network.OpenAiModelProbeSupport
import com.lanxin.android.presentation.common.SettingItem
import com.lanxin.android.util.formatPlatformTimeout
import com.lanxin.android.util.pinnedExitUntilCollapsedScrollBehavior

/** Soft display cap inside scroll; search unlocks the full set. */
private const val REMOTE_MODEL_VISIBLE_LIMIT = 200

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformSettingScreen(
    modifier: Modifier = Modifier,
    settingViewModel: PlatformSettingViewModel = hiltViewModel(),
    onNavigationClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward }
    )
    val platform by settingViewModel.platformState.collectAsStateWithLifecycle()
    val dialogState by settingViewModel.dialogState.collectAsStateWithLifecycle()
    val isDeleted by settingViewModel.isDeleted.collectAsStateWithLifecycle()

    LaunchedEffect(isDeleted) {
        if (isDeleted) {
            onNavigationClick()
        }
    }

    platform?.let { platformData ->
        Scaffold(
            modifier = modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                PlatformTopAppBar(
                    title = platformData.name,
                    onNavigationClick = onNavigationClick,
                    onDeleteClick = settingViewModel::openDeleteDialog,
                    scrollBehavior = scrollBehavior
                )
            }
        ) { innerPadding ->
            Column(
                Modifier
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
            ) {
                PreferenceSwitchWithContainer(
                    title = stringResource(R.string.enable_api),
                    isChecked = platformData.enabled
                ) { settingViewModel.toggleEnabled() }
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.platform_name),
                    description = platformData.name,
                    enabled = platformData.enabled,
                    onItemClick = settingViewModel::openPlatformNameDialog,
                    showTrailingIcon = false,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Label,
                            contentDescription = stringResource(R.string.platform_name_icon)
                        )
                    }
                )
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.api_url),
                    description = platformData.apiUrl,
                    enabled = platformData.enabled,
                    onItemClick = settingViewModel::openApiUrlDialog,
                    showTrailingIcon = false,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            ImageVector.vectorResource(id = R.drawable.ic_link),
                            contentDescription = stringResource(R.string.url_icon)
                        )
                    }
                )
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.api_key),
                    description = if (platformData.token.isNullOrEmpty()) {
                        stringResource(R.string.token_not_set)
                    } else {
                        stringResource(R.string.token_set, platformData.token[0])
                    },
                    enabled = platformData.enabled,
                    onItemClick = settingViewModel::openApiTokenDialog,
                    showTrailingIcon = false,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            ImageVector.vectorResource(id = R.drawable.ic_key),
                            contentDescription = stringResource(R.string.key_icon)
                        )
                    }
                )
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.api_model),
                    description = platformData.model,
                    enabled = platformData.enabled,
                    onItemClick = settingViewModel::openApiModelDialog,
                    showTrailingIcon = false,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            ImageVector.vectorResource(id = R.drawable.ic_model),
                            contentDescription = stringResource(R.string.model_icon)
                        )
                    }
                )
                val remoteModels by settingViewModel.remoteModelListState.collectAsStateWithLifecycle()
                val probeState by settingViewModel.probeState.collectAsStateWithLifecycle()
                if (remoteModels.supported) {
                    SettingItem(
                        modifier = Modifier.height(64.dp),
                        title = stringResource(R.string.fetch_remote_models),
                        description = when {
                            remoteModels.loading -> stringResource(R.string.fetch_remote_models_loading)
                            remoteModels.error != null ->
                                stringResource(
                                    R.string.fetch_remote_models_error,
                                    humanizeModelListError(remoteModels.error!!)
                                )
                            remoteModels.models.isNotEmpty() ->
                                stringResource(R.string.fetch_remote_models_count, remoteModels.models.size)
                            else -> stringResource(R.string.fetch_remote_models_hint)
                        },
                        enabled = platformData.enabled && !remoteModels.loading,
                        onItemClick = settingViewModel::fetchRemoteModels,
                        showTrailingIcon = false,
                        showLeadingIcon = true,
                        leadingIcon = {
                            Icon(
                                ImageVector.vectorResource(id = R.drawable.ic_model),
                                contentDescription = stringResource(R.string.fetch_remote_models)
                            )
                        }
                    )
                    if (remoteModels.models.isNotEmpty()) {
                        OutlinedTextField(
                            value = remoteModels.filterQuery,
                            onValueChange = settingViewModel::updateModelFilter,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            singleLine = true,
                            enabled = platformData.enabled,
                            label = { Text(stringResource(R.string.remote_models_filter_label)) },
                            placeholder = { Text(stringResource(R.string.remote_models_filter_hint)) }
                        )
                        val filtered = remember(remoteModels.models, remoteModels.filterQuery) {
                            OpenAiModelProbeSupport.filterModelIds(
                                remoteModels.models,
                                remoteModels.filterQuery
                            )
                        }
                        val visible = filtered.take(REMOTE_MODEL_VISIBLE_LIMIT)
                        visible.forEach { modelId ->
                            val isSelected = modelId.equals(platformData.model, ignoreCase = true) ||
                                modelId == platformData.model
                            val isChecked = remoteModels.checkedModelIds.contains(modelId)
                            val probeResult = probeState.results.firstOrNull {
                                it.modelId.equals(modelId, ignoreCase = true)
                            }
                            RemoteModelRow(
                                modelId = modelId,
                                selected = isSelected,
                                checked = isChecked,
                                enabled = platformData.enabled,
                                probeLatencyMs = probeResult?.latencyMs,
                                probeSuccess = probeResult?.success,
                                onSelect = { settingViewModel.selectRemoteModel(modelId) },
                                onToggleCheck = { settingViewModel.toggleProbeCheck(modelId) }
                            )
                        }
                        if (filtered.size > REMOTE_MODEL_VISIBLE_LIMIT) {
                            SettingItem(
                                modifier = Modifier.height(48.dp),
                                title = stringResource(
                                    R.string.remote_models_truncated,
                                    filtered.size - REMOTE_MODEL_VISIBLE_LIMIT
                                ),
                                description = stringResource(R.string.remote_models_use_filter),
                                enabled = false,
                                onItemClick = {},
                                showTrailingIcon = false,
                                showLeadingIcon = false
                            )
                        } else if (filtered.isEmpty()) {
                            SettingItem(
                                modifier = Modifier.height(48.dp),
                                title = stringResource(R.string.remote_models_filter_empty),
                                description = null,
                                enabled = false,
                                onItemClick = {},
                                showTrailingIcon = false,
                                showLeadingIcon = false
                            )
                        }
                        SettingItem(
                            modifier = Modifier.height(64.dp),
                            title = stringResource(R.string.probe_models_title),
                            description = when {
                                probeState.running -> stringResource(R.string.probe_models_running)
                                probeState.error != null ->
                                    stringResource(
                                        R.string.probe_models_error,
                                        humanizeModelListError(probeState.error!!)
                                    )
                                probeState.results.isNotEmpty() ->
                                    stringResource(
                                        R.string.probe_models_done,
                                        probeState.results.count { it.success },
                                        probeState.results.size
                                    )
                                else -> stringResource(
                                    R.string.probe_models_hint,
                                    OpenAiModelProbeSupport.MAX_PROBE_MODELS
                                )
                            },
                            enabled = platformData.enabled && !probeState.running,
                            onItemClick = settingViewModel::probeSelectedModels,
                            showTrailingIcon = false,
                            showLeadingIcon = true,
                            leadingIcon = {
                                Icon(
                                    ImageVector.vectorResource(id = R.drawable.ic_chart),
                                    contentDescription = stringResource(R.string.probe_models_title)
                                )
                            }
                        )
                        if (probeState.results.isNotEmpty()) {
                            probeState.results.forEach { result ->
                                SettingItem(
                                    modifier = Modifier.height(56.dp),
                                    title = result.modelId,
                                    description = if (result.success) {
                                        stringResource(
                                            R.string.probe_result_ok,
                                            result.latencyMs
                                        )
                                    } else {
                                        stringResource(
                                            R.string.probe_result_fail,
                                            result.latencyMs,
                                            humanizeModelListError(result.detail)
                                        )
                                    },
                                    enabled = false,
                                    onItemClick = {},
                                    showTrailingIcon = false,
                                    showLeadingIcon = false
                                )
                            }
                        }
                    }
                }
                // Disable temperature and top_p when reasoning is enabled for OpenAI
                val isReasoningDisabled = platformData.compatibleType == ClientType.OPENAI && platformData.reasoning
                val notSetText = stringResource(R.string.not_set)
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.temperature),
                    description = platformData.temperature?.toString() ?: notSetText,
                    enabled = platformData.enabled && !isReasoningDisabled,
                    onItemClick = settingViewModel::openTemperatureDialog,
                    showTrailingIcon = false,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            ImageVector.vectorResource(id = R.drawable.ic_temperature),
                            contentDescription = stringResource(R.string.temperature_icon)
                        )
                    }
                )
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.top_p),
                    description = platformData.topP?.toString() ?: notSetText,
                    enabled = platformData.enabled && !isReasoningDisabled,
                    onItemClick = settingViewModel::openTopPDialog,
                    showTrailingIcon = false,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            ImageVector.vectorResource(id = R.drawable.ic_chart),
                            contentDescription = stringResource(R.string.top_p_icon)
                        )
                    }
                )
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.system_prompt),
                    description = platformData.systemPrompt,
                    enabled = platformData.enabled,
                    onItemClick = settingViewModel::openSystemPromptDialog,
                    showTrailingIcon = false,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            ImageVector.vectorResource(id = R.drawable.ic_instructions),
                            contentDescription = stringResource(R.string.system_prompt_icon)
                        )
                    }
                )
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.timeout),
                    description = formatPlatformTimeout(platformData.timeout, stringResource(R.string.off)),
                    enabled = platformData.enabled,
                    onItemClick = settingViewModel::openTimeoutDialog,
                    showTrailingIcon = false,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            ImageVector.vectorResource(id = R.drawable.ic_info),
                            contentDescription = stringResource(R.string.timeout_icon)
                        )
                    }
                )
                if (platformData.compatibleType == ClientType.GOOGLE) {
                    SettingItem(
                        modifier = Modifier.height(64.dp),
                        title = stringResource(R.string.gemini_safety_settings),
                        description = stringResource(R.string.gemini_safety_settings_description),
                        enabled = platformData.enabled,
                        onItemClick = settingViewModel::openGeminiSafetyDialog,
                        showTrailingIcon = false,
                        showLeadingIcon = true,
                        leadingIcon = {
                            Icon(
                                ImageVector.vectorResource(id = R.drawable.ic_info),
                                contentDescription = stringResource(R.string.gemini_safety_settings_icon)
                            )
                        }
                    )
                }
                ExtendedThinkingSwitch(
                    modifier = Modifier.height(64.dp),
                    enabled = platformData.enabled,
                    isChecked = platformData.reasoning,
                    onCheckedChange = { settingViewModel.toggleReasoning() }
                )

                PlatformNameDialog(dialogState, platformData.name, settingViewModel)
                APIUrlDialog(dialogState, platformData.apiUrl, settingViewModel)
                APIKeyDialog(dialogState, settingViewModel)
                ModelDialog(dialogState, platformData.model, settingViewModel)
                TemperatureDialog(dialogState, platformData.temperature, settingViewModel)
                TopPDialog(dialogState, platformData.topP, settingViewModel)
                SystemPromptDialog(dialogState, platformData.systemPrompt ?: "", settingViewModel)
                TimeoutDialog(dialogState, platformData.timeout, settingViewModel)
                GeminiSafetySettingsDialog(dialogState, platformData, settingViewModel)
                DeletePlatformDialog(dialogState, settingViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformTopAppBar(
    title: String,
    onNavigationClick: () -> Unit,
    onDeleteClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior
) {
    var showMenu by remember { mutableStateOf(false) }

    LargeTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        ),
        title = {
            Text(
                modifier = Modifier.padding(4.dp),
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(
                modifier = Modifier.padding(4.dp),
                onClick = onNavigationClick
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
            }
        },
        actions = {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.more_options)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_platform)) },
                    onClick = {
                        showMenu = false
                        onDeleteClick()
                    }
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
fun ExtendedThinkingSwitch(
    modifier: Modifier,
    enabled: Boolean,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val clickableModifier = if (enabled) {
        modifier
            .fillMaxWidth()
            .clickable(onClick = { onCheckedChange(!isChecked) })
            .padding(horizontal = 8.dp)
    } else {
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    }
    val colors = ListItemDefaults.colors()

    ListItem(
        modifier = clickableModifier,
        headlineContent = {
            Text(
                text = stringResource(R.string.extended_thinking),
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = stringResource(R.string.extended_thinking_description),
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_model),
                contentDescription = stringResource(R.string.extended_thinking)
            )
        },
        trailingContent = {
            Switch(
                checked = isChecked,
                onCheckedChange = null,
                enabled = enabled
            )
        },
        colors = ListItemDefaults.colors(
            headlineColor = if (enabled) colors.headlineColor else colors.disabledHeadlineColor,
            supportingColor = if (enabled) colors.supportingTextColor else colors.disabledHeadlineColor,
            leadingIconColor = if (enabled) colors.leadingIconColor else colors.disabledLeadingIconColor,
            trailingIconColor = if (enabled) colors.trailingIconColor else colors.disabledTrailingIconColor
        )
    )
}

@Composable
private fun RemoteModelRow(
    modelId: String,
    selected: Boolean,
    checked: Boolean,
    enabled: Boolean,
    probeLatencyMs: Long?,
    probeSuccess: Boolean?,
    onSelect: () -> Unit,
    onToggleCheck: () -> Unit
) {
    val colors = ListItemDefaults.colors()
    val supporting = buildString {
        when {
            selected -> append(stringResource(R.string.current_model_selected))
            else -> append(stringResource(R.string.tap_to_select_model))
        }
        if (probeLatencyMs != null && probeSuccess != null) {
            append(" · ")
            append(
                if (probeSuccess) {
                    stringResource(R.string.probe_result_ok_short, probeLatencyMs)
                } else {
                    stringResource(R.string.probe_result_fail_short, probeLatencyMs)
                }
            )
        }
    }
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onSelect)
                } else {
                    Modifier
                }
            )
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 4.dp),
        headlineContent = {
            Text(
                text = modelId,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = stringResource(R.string.current_model_selected),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Checkbox(
                    checked = checked,
                    onCheckedChange = { if (enabled) onToggleCheck() },
                    enabled = enabled
                )
            }
        },
        colors = ListItemDefaults.colors(
            headlineColor = if (enabled) colors.headlineColor else colors.disabledHeadlineColor,
            supportingColor = if (enabled) colors.supportingTextColor else colors.disabledHeadlineColor
        )
    )
}

@Composable
private fun humanizeModelListError(code: String): String {
    return when (code) {
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
        "empty_model" -> stringResource(R.string.probe_error_empty_model)
        "empty_completion" -> stringResource(R.string.probe_error_empty_completion)
        "unexpected_completion" -> stringResource(R.string.probe_error_unexpected)
        else -> code
    }
}

@Composable
fun PreferenceSwitchWithContainer(
    title: String,
    icon: ImageVector? = null,
    isChecked: Boolean,
    onClick: () -> Unit
) {
    val thumbContent: (@Composable () -> Unit)? = remember(isChecked) {
        if (isChecked) {
            {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            }
        } else {
            null
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                MaterialTheme.colorScheme.primaryContainer
            )
            .toggleable(
                value = isChecked,
                onValueChange = { onClick() },
                interactionSource = interactionSource,
                indication = LocalIndication.current
            )
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 8.dp, end = 16.dp)
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (icon == null) 12.dp else 0.dp, end = 12.dp)
        ) {
            Text(
                text = title,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Switch(
            checked = isChecked,
            interactionSource = interactionSource,
            onCheckedChange = null,
            modifier = Modifier.padding(start = 12.dp, end = 6.dp),
            thumbContent = thumbContent
        )
    }
}
