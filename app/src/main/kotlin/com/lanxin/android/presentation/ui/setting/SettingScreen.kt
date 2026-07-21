package com.lanxin.android.presentation.ui.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanxin.android.R
import com.lanxin.android.plugins.chat.data.entity.PlatformV2
import com.lanxin.android.data.model.DynamicTheme
import com.lanxin.android.data.model.ThemeMode
import com.lanxin.android.presentation.common.LocalDynamicTheme
import com.lanxin.android.presentation.common.LocalThemeMode
import com.lanxin.android.presentation.common.LocalThemeViewModel
import com.lanxin.android.presentation.common.RadioItem
import com.lanxin.android.presentation.common.SettingItem
import com.lanxin.android.util.getClientTypeDisplayName
import com.lanxin.android.util.getDynamicThemeTitle
import com.lanxin.android.util.getThemeModeTitle
import com.lanxin.android.util.pinnedExitUntilCollapsedScrollBehavior
import com.lanxin.android.core.updater.ui.DownloadProgressDialog
import com.lanxin.android.core.updater.ui.UpdateConfirmDialog
import com.lanxin.android.core.updater.ui.VersionSelectDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    modifier: Modifier = Modifier,
    settingViewModel: SettingViewModelV2 = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
    onNavigationClick: () -> Unit,
    onNavigateToAddPlatform: () -> Unit,
    onNavigateToPlatformSetting: (String) -> Unit,
    onNavigateToAboutPage: () -> Unit,
    onNavigateToLogger: () -> Unit = {},
    onNavigateToPersona: () -> Unit = {},
    onNavigateToStatistics: () -> Unit = {},
    onNavigateToKnowledge: () -> Unit = {},
    onNavigateToScheduler: () -> Unit = {},
    onNavigateToUnifiedInbox: () -> Unit = {},
    onNavigateToUnifiedSearch: () -> Unit = {},
    onNavigateToPluginManager: () -> Unit = {},
    onNavigateToPluginMarket: () -> Unit = {},
    onNavigateToSmartCapabilities: () -> Unit = {},
    onNavigateToDesktopPet: () -> Unit = {},
    onNavigateToClawHost: () -> Unit = {},
    // 旧入口保留：redirect 用（高级细页仍可直达）
    onNavigateToLocalInference: () -> Unit = {},
    onNavigateToOfflineAsr: () -> Unit = {},
    onNavigateToSystemTools: () -> Unit = {},
    onNavigateToWebSearch: () -> Unit = {},
    onNavigateToDeviceSensing: () -> Unit = {},
    onNavigateToSceneSensing: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward }
    )
    val platformState by settingViewModel.platformState.collectAsStateWithLifecycle()
    val dialogState by settingViewModel.dialogState.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                settingViewModel.fetchPlatforms()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SettingTopBar(
                scrollBehavior = scrollBehavior,
                navigationOnClick = onNavigationClick
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .verticalScroll(scrollState)
        ) {
            ThemeSetting { settingViewModel.openThemeDialog() }

            // Add Platform button
            SettingItem(
                title = stringResource(R.string.add_platform),
                description = stringResource(R.string.add_platform_description),
                onItemClick = onNavigateToAddPlatform,
                showTrailingIcon = false,
                showLeadingIcon = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )

            // Dynamic platform list
            platformState.forEach { platform ->
                PlatformItem(
                    platform = platform,
                    onItemClick = { onNavigateToPlatformSetting(platform.uid) },
                    onDeleteClick = { settingViewModel.openDeleteDialog(platform.id) }
                )
            }

            AboutPageItem(onItemClick = onNavigateToAboutPage)

            SettingItem(
                title = "人格设定",
                description = "切换或自定义 AI 人格 / system prompt",
                onItemClick = onNavigateToPersona,
                showTrailingIcon = true,
                showLeadingIcon = false
            )

            SettingItem(
                title = "数据统计",
                description = "对话轮数、token 估算与按日活跃度",
                onItemClick = onNavigateToStatistics,
                showTrailingIcon = true,
                showLeadingIcon = false
            )

            SettingItem(
                title = "知识库",
                description = "导入 txt/md/pdf，滑动窗口分段并向量化入库",
                onItemClick = onNavigateToKnowledge,
                showTrailingIcon = true,
                showLeadingIcon = false
            )

            SettingItem(
                title = "定时任务",
                description = "周期/一次性提醒与 BASIC 回调自动执行",
                onItemClick = onNavigateToScheduler,
                showTrailingIcon = true,
                showLeadingIcon = false
            )

            SettingItem(
                title = "跨会话历史",
                description = "聚合本地会话对话，跨工作区文件浏览",
                onItemClick = onNavigateToUnifiedInbox,
                showTrailingIcon = true,
                showLeadingIcon = false
            )

            SettingItem(
                title = "统一搜索",
                description = "memory/knowledge/chat/跨会话 四路 RRF 融合与命中数",
                onItemClick = onNavigateToUnifiedSearch,
                showTrailingIcon = true,
                showLeadingIcon = false
            )

            SettingItem(
                title = "插件管理",
                description = "启用/停用插件，扫描与卸载动态 .apk 包",
                onItemClick = onNavigateToPluginManager,
                showTrailingIcon = true,
                showLeadingIcon = false
            )

            SettingItem(
                title = "插件市场",
                description = "从远程索引浏览/下载插件到 plugin-packages",
                onItemClick = onNavigateToPluginMarket,
                showTrailingIcon = true,
                showLeadingIcon = false
            )

            SettingItem(
                title = "智能能力",
                description = "总开关与能力清单：本地模型 / 语音 / 助手工具 / 位置 / 看世界（体验资源不在此页）",
                onItemClick = onNavigateToSmartCapabilities,
                showTrailingIcon = true,
                showLeadingIcon = false
            )

            SettingItem(
                title = "桌宠 / 语音陪伴",
                description = "体验与资源：全屏陪伴、悬浮窗、Live2D/背景/音乐、ASR·TTS 下载（开关见智能能力）",
                onItemClick = onNavigateToDesktopPet,
                showTrailingIcon = true,
                showLeadingIcon = false
            )

            SettingItem(
                title = "机器人 / Claw 宿主",
                description = "默认关；动态机器人插件 PlatformHost + 可选前台常驻",
                onItemClick = onNavigateToClawHost,
                showTrailingIcon = true,
                showLeadingIcon = false
            )

            SettingItem(
                title = "检查更新",
                description = "从 GitHub Releases 检查 / 回退版本",
                onItemClick = { updateViewModel.openVersionDialog() },
                showTrailingIcon = true,
                showLeadingIcon = false
            )

            SettingItem(
                title = "日志查看",
                description = "浏览、过滤与导出本地日志",
                onItemClick = onNavigateToLogger,
                showTrailingIcon = true,
                showLeadingIcon = false
            )

            val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
            if (updateState.showVersionDialog) {
                VersionSelectDialog(
                    currentVersion = updateState.currentVersion,
                    releases = updateState.releases,
                    onSelect = updateViewModel::onReleaseSelected,
                    onDismiss = updateViewModel::dismissDialogs
                )
            }
            if (updateState.showConfirmDialog && updateState.selectedRelease != null) {
                UpdateConfirmDialog(
                    release = updateState.selectedRelease!!,
                    onConfirm = updateViewModel::confirmAndInstall,
                    onDismiss = updateViewModel::dismissDialogs
                )
            }
            if (updateState.showProgressDialog) {
                DownloadProgressDialog(
                    percent = updateState.downloadPercent,
                    downloadedBytes = updateState.downloadedBytes,
                    totalBytes = updateState.totalBytes,
                    onCancel = updateViewModel::cancelDownload
                )
            }

            if (dialogState.isThemeDialogOpen) {
                ThemeSettingDialog(settingViewModel)
            }

            if (dialogState.isDeleteDialogOpen) {
                DeletePlatformDialog(settingViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    navigationOnClick: () -> Unit
) {
    LargeTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        ),
        title = {
            Text(
                modifier = Modifier.padding(4.dp),
                text = stringResource(R.string.settings),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(
                modifier = Modifier.padding(4.dp),
                onClick = navigationOnClick
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
fun ThemeSetting(
    onItemClick: () -> Unit
) {
    SettingItem(
        title = stringResource(R.string.theme_settings),
        description = stringResource(R.string.theme_description),
        onItemClick = onItemClick,
        showTrailingIcon = false,
        showLeadingIcon = false
    )
}

@Composable
fun AboutPageItem(
    onItemClick: () -> Unit
) {
    SettingItem(
        title = stringResource(R.string.about),
        description = stringResource(R.string.about_description),
        onItemClick = onItemClick,
        showTrailingIcon = true,
        showLeadingIcon = false
    )
}

@Composable
fun ThemeSettingDialog(
    settingViewModel: SettingViewModelV2 = hiltViewModel()
) {
    val themeViewModel = LocalThemeViewModel.current
    AlertDialog(
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(text = stringResource(R.string.dynamic_theme), style = MaterialTheme.typography.titleMedium)
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                )
                DynamicTheme.entries.forEach { theme ->
                    RadioItem(
                        title = getDynamicThemeTitle(theme),
                        description = null,
                        value = theme.name,
                        selected = LocalDynamicTheme.current == theme
                    ) {
                        themeViewModel.updateDynamicTheme(theme)
                    }
                }
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )
                Text(text = stringResource(R.string.dark_mode), style = MaterialTheme.typography.titleMedium)
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                )
                ThemeMode.entries.forEach { theme ->
                    RadioItem(
                        title = getThemeModeTitle(theme),
                        description = null,
                        value = theme.name,
                        selected = LocalThemeMode.current == theme
                    ) {
                        themeViewModel.updateThemeMode(theme)
                    }
                }
            }
        },
        onDismissRequest = settingViewModel::closeThemeDialog,
        confirmButton = {
            TextButton(
                onClick = settingViewModel::closeThemeDialog
            ) {
                Text(stringResource(R.string.confirm))
            }
        }
    )
}

@Composable
fun PlatformItem(
    platform: PlatformV2,
    onItemClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    SettingItem(
        title = platform.name,
        description = "${getClientTypeDisplayName(platform.compatibleType)} • ${if (platform.enabled) stringResource(R.string.enabled) else stringResource(R.string.disabled)}",
        onItemClick = onItemClick,
        showTrailingIcon = true,
        showLeadingIcon = false
    )
}

@Composable
fun DeletePlatformDialog(
    settingViewModel: SettingViewModelV2 = hiltViewModel()
) {
    AlertDialog(
        title = {
            Text(stringResource(R.string.delete_platform))
        },
        text = {
            Text(stringResource(R.string.delete_platform_confirmation))
        },
        onDismissRequest = settingViewModel::closeDeleteDialog,
        confirmButton = {
            TextButton(
                onClick = settingViewModel::confirmDelete
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(
                onClick = settingViewModel::closeDeleteDialog
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
