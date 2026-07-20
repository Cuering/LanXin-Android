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

package com.lanxin.android.builtin.pet.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope

import dagger.hilt.android.lifecycle.HiltViewModel

import com.lanxin.android.builtin.pet.data.DesktopPetBridge
import com.lanxin.android.builtin.pet.domain.BuiltInLive2dAssets
import com.lanxin.android.builtin.pet.domain.BuiltInMusicAssets
import com.lanxin.android.builtin.pet.domain.CompanionBackgrounds
import com.lanxin.android.builtin.pet.domain.CompanionMusicPlayer
import com.lanxin.android.builtin.pet.domain.CompanionVisionFrameEncoder
import com.lanxin.android.builtin.pet.domain.CompanionVisionSession
import com.lanxin.android.builtin.pet.domain.DebugAssetStorage
import com.lanxin.android.builtin.pet.domain.Live2dDisplayController
import com.lanxin.android.builtin.pet.domain.Live2dModel3Reader
import com.lanxin.android.builtin.pet.domain.MeijuDebugPaths
import com.lanxin.android.builtin.pet.domain.PetBridgeCommand
import com.lanxin.android.builtin.pet.domain.PetBridgeMessage
import com.lanxin.android.builtin.pet.domain.PetBridgeProtocol
import com.lanxin.android.builtin.pet.domain.MoodTagMapper
import com.lanxin.android.builtin.pet.domain.PetExpressionController
import com.lanxin.android.builtin.pet.domain.PetSettings
import com.lanxin.android.builtin.pet.domain.TextExpressionMotionMapper
import com.lanxin.android.builtin.capabilities.domain.LocationSettings
import com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesSettings
import com.lanxin.android.builtin.capabilities.tools.LocationTool
import com.lanxin.android.builtin.guide.domain.GuideGate
import com.lanxin.android.builtin.guide.domain.GuideLocationContext
import com.lanxin.android.builtin.guide.domain.GuideNavHandoff
import com.lanxin.android.builtin.guide.domain.GuidePromptBuilder
import com.lanxin.android.builtin.pet.domain.VisionExplainClient
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import com.lanxin.android.builtin.pet.domain.VisionExplainResult
import com.lanxin.android.builtin.pet.domain.VisionModelCapability
import com.lanxin.android.builtin.pet.domain.VoiceSessionCoordinator
import com.lanxin.android.builtin.pet.domain.VoiceSessionInput
import com.lanxin.android.builtin.pet.domain.VoiceSessionPhase
import com.lanxin.android.builtin.platform.domain.SceneSensingSettings
import com.lanxin.android.util.PathImportHelper

import java.io.File

import javax.inject.Inject

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 妹居式 App 内全屏陪伴页：Live2D 铺满 + 顶/底浮层（无白框小窗）。
 *
 * 不依赖悬浮权限 / ASR / TTS 下载；发送走 [VoiceSessionCoordinator] + stub 回复。
 * 设置入口仍进 [DesktopPetScreen]。
 */
@Composable
fun CompanionScreen(
    onBackAction: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: CompanionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var musicPanelOpen by remember { mutableStateOf(false) }
    var bgPanelOpen by remember { mutableStateOf(false) }
    var frameHolder by remember { mutableStateOf<CompanionFrameHolder?>(null) }

    val pickAudio = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importAudio(uri.toString())
        }
    }

    val pickBgImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importBackground(uri.toString())
        }
    }

    val requestCamera = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onCameraPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        viewModel.ensureReady()
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onCameraPermissionResult(granted)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.onLeavePage() }
    }

    if (state.showVisionConsentDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissVisionConsent,
            title = { Text("开启「看世界」？") },
            text = {
                Text(
                    "全屏陪伴内会显示相机预览（画中画，非后台偷拍）。\n\n" +
                        "· 默认关闭；仅你打开开关时预览\n" +
                        "· 提问或点「看一眼」时抓 1 帧缩略图，送已配置的多模态模型讲解\n" +
                        "· 帧不落盘、不写日志原图；关开关 / 离开页面立即停相机\n" +
                        "· 无视觉模型时会明确提示，不会假装本地会看图\n" +
                        "· 同意记录与设置页「场景识别」共用（可随时撤回）"
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmVisionConsentAndEnable) {
                    Text("同意并开启")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissVisionConsent) {
                    Text("取消")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景层：预设渐变 / 自定义图（WebView 透明叠上）
        CompanionBackgroundLayer(
            modifier = Modifier.fillMaxSize(),
            presetId = state.bgPresetId,
            customPath = state.bgCustomPath
        )

        // Live2D / 占位 / 降级：全屏铺底，无卡片裁切
        CompanionLive2dWebView(
            modifier = Modifier.fillMaxSize(),
            pushTicket = state.webPushTicket,
            onWebReady = viewModel::onWebReady,
            onBridgeCommand = viewModel::onBridgeCommand,
            encodeSession = viewModel::encodeSessionRaw,
            encodeExpression = viewModel::encodeExpressionRaw,
            encodePlayMotion = viewModel::encodePlayMotionRaw,
            encodeBubble = viewModel::encodeBubbleRaw,
            encodeLoadLive2d = viewModel::encodeLoadLive2dRaw
        )

        // 顶栏：半透明浮在模型上，不挤占中间舞台
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Color.White.copy(alpha = 0.22f))
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackAction) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color(0xFF5A2038)
                )
            }
            Text(
                text = "陪伴",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF5A2038),
                modifier = Modifier.weight(1f)
            )
            // 看世界开关：导游插件 OFF 时不露入口
            if (state.guidePluginEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 2.dp)
                ) {
                    Icon(
                        if (state.visionLooking) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = null,
                        tint = if (state.visionLooking) Color(0xFFE85D8E) else Color(0xFF5A2038),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "看世界",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF5A2038)
                    )
                    Switch(
                        checked = state.visionLooking,
                        onCheckedChange = { on ->
                            viewModel.setVisionLooking(on)
                            if (on) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!granted) {
                                    requestCamera.launch(Manifest.permission.CAMERA)
                                } else {
                                    viewModel.onCameraPermissionResult(true)
                                }
                            }
                        },
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
            TextButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = null,
                    tint = Color(0xFF5A2038)
                )
                Spacer(modifier = Modifier.padding(horizontal = 2.dp))
                Text("设置", color = Color(0xFF5A2038))
            }
        }

        // 状态角标：轻量浮层（Cubism·闲置 / 看世界 / 提示）
        if (state.statusLine.isNotBlank() || state.visionLooking || !state.visionHint.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 52.dp)
                    .alpha(0.88f),
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFF1A0A12).copy(alpha = 0.42f),
                tonalElevation = 0.dp
            ) {
                val badge = when {
                    !state.visionHint.isNullOrBlank() && !state.visionLooking ->
                        state.visionHint!!
                    state.visionLooking ->
                        CompanionVisionSession.statusLabel(
                            lookingEnabled = true,
                            previewReady = state.visionPreviewReady
                        )
                    else -> companionStatusBadge(state.statusLine)
                }
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .widthIn(max = 220.dp)
                )
            }
        }

        // 画中画预览：仅看世界开 + consent + 相机权限
        if (state.visionLooking && state.visionConsentGranted && state.cameraGranted) {
            CompanionCameraPip(
                active = true,
                onPreviewReady = viewModel::onVisionPreviewReady,
                onFrameHolder = { frameHolder = it },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 56.dp, end = 12.dp)
            )
            // 「看一眼」
            TextButton(
                onClick = {
                    viewModel.lookOnce(frameHolder)
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 232.dp, end = 8.dp),
                enabled = !state.busy && state.visionPreviewReady
            ) {
                Text("看一眼", color = Color(0xFFE85D8E))
            }
        }

        if (state.busy) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp),
                color = Color(0xFFE85D8E)
            )
        }

        // 底栏：输入 + 发送 半透明浮在模型上方
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.White.copy(alpha = 0.55f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("跟兰心说点什么…") },
                    singleLine = true,
                    enabled = !state.busy,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1A0A12),
                        unfocusedTextColor = Color(0xFF1A0A12),
                        cursorColor = Color(0xFFE85D8E),
                        focusedBorderColor = Color(0xFFE85D8E).copy(alpha = 0.5f),
                        unfocusedBorderColor = Color(0xFF5A2038).copy(alpha = 0.25f),
                        focusedPlaceholderColor = Color(0xFF5A2038).copy(alpha = 0.55f),
                        unfocusedPlaceholderColor = Color(0xFF5A2038).copy(alpha = 0.55f)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            val text = draft
                            if (text.isNotBlank() && !state.busy) {
                                viewModel.sendText(text, frameHolder)
                                draft = ""
                                keyboard?.hide()
                            }
                        }
                    )
                )
                IconButton(
                    onClick = {
                        val text = draft
                        if (text.isNotBlank() && !state.busy) {
                            viewModel.sendText(text, frameHolder)
                            draft = ""
                            keyboard?.hide()
                        }
                    },
                    enabled = !state.busy && draft.isNotBlank()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = Color(0xFFE85D8E)
                    )
                }
            }
        }

        // 右下角：换背景 + 背景音乐（抬高避开底栏）
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .imePadding()
                .padding(end = 14.dp, bottom = 88.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedVisibility(visible = bgPanelOpen) {
                CompanionBackgroundPanel(
                    state = state,
                    onSelectPreset = { id ->
                        viewModel.selectBackgroundPreset(id)
                    },
                    onPick = { pickBgImage.launch(arrayOf("image/*")) },
                    onUseCustom = {
                        if (state.bgCustomPath.isNotBlank()) {
                            viewModel.selectBackgroundPreset(CompanionBackgrounds.CUSTOM_ID)
                        } else {
                            pickBgImage.launch(arrayOf("image/*"))
                        }
                    },
                    onClose = { bgPanelOpen = false }
                )
            }
            AnimatedVisibility(visible = musicPanelOpen) {
                CompanionMusicPanel(
                    state = state,
                    onToggle = viewModel::toggleMusic,
                    onPrev = viewModel::previousTrack,
                    onNext = viewModel::nextTrack,
                    onPick = { pickAudio.launch(arrayOf("audio/*")) },
                    onSelectTrack = viewModel::playTrackAt,
                    onVolume = viewModel::setMusicVolume,
                    onRefresh = viewModel::refreshMusicPlaylist,
                    onClose = { musicPanelOpen = false }
                )
            }
            // 换背景按钮（在音乐上方）
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .alpha(0.72f)
                    .clickable {
                        musicPanelOpen = false
                        bgPanelOpen = !bgPanelOpen
                    },
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.42f),
                tonalElevation = 1.dp
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = "换背景",
                        tint = Color(0xFF5A2038),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            if (state.trackCount > 0 || state.musicPlaying) {
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .alpha(if (state.musicPlaying) 0.92f else 0.55f)
                        .clickable {
                            bgPanelOpen = false
                            musicPanelOpen = !musicPanelOpen
                        },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.42f),
                    tonalElevation = 1.dp
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = "背景音乐",
                            tint = if (state.musicPlaying) Color(0xFFE85D8E) else Color(0xFF5A2038),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            } else {
                // 无曲目：置灰小图标，仍可点开导入
                Surface(
                    modifier = Modifier
                        .size(44.dp)
                        .alpha(0.35f)
                        .clickable {
                            bgPanelOpen = false
                            musicPanelOpen = !musicPanelOpen
                        },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.28f)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = "背景音乐（无曲目）",
                            tint = Color(0xFF5A2038),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 状态角标文案：压缩成长串 statusLine，突出 Cubism / 壳 / 占位。 */
private fun companionStatusBadge(statusLine: String): String {
    val s = statusLine.trim()
    if (s.isEmpty()) return "闲置"
    // 「显示：Live2D Cubism · 可直接打字…」→ 取显示段
    val afterShow = s.removePrefix("显示：").trim()
    val head = afterShow.substringBefore("·").trim()
        .ifBlank { afterShow.substringBefore("·").trim() }
    return when {
        head.contains("Cubism", ignoreCase = true) -> "Cubism·闲置"
        head.contains("壳") -> "L2D壳·闲置"
        head.contains("占位") -> "占位"
        head.contains("降级") -> "降级"
        s.startsWith("思考") -> "思考中"
        s.startsWith("已回复") -> "已回复"
        s.startsWith("出错") -> "出错"
        head.length in 1..18 -> head
        else -> head.take(16)
    }
}


@Composable
private fun CompanionBackgroundLayer(
    modifier: Modifier,
    presetId: String,
    customPath: String
) {
    val resolved = remember(presetId, customPath) {
        CompanionBackgrounds.resolve(presetId, customPath)
    }
    when (resolved) {
        is CompanionBackgrounds.Resolved.Image -> {
            val bitmap = remember(resolved.path) {
                runCatching {
                    BitmapFactory.decodeFile(resolved.path)?.asImageBitmap()
                }.getOrNull()
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = modifier,
                    contentScale = ContentScale.Crop
                )
            } else {
                val fallback = CompanionBackgrounds.presetById(CompanionBackgrounds.DEFAULT_ID)!!
                Box(
                    modifier = modifier.background(
                        Brush.verticalGradient(fallback.colorsArgb.map { Color(it) })
                    )
                )
            }
        }
        is CompanionBackgrounds.Resolved.Preset -> {
            Box(
                modifier = modifier.background(
                    Brush.verticalGradient(resolved.preset.colorsArgb.map { Color(it) })
                )
            )
        }
    }
}

@Composable
private fun CompanionBackgroundPanel(
    state: CompanionUiState,
    onSelectPreset: (String) -> Unit,
    onPick: () -> Unit,
    onUseCustom: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .padding(bottom = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.92f),
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "换背景",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF5A2038),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClose) {
                    Text("收起", color = Color(0xFF5A2038))
                }
            }
            Text(
                text = state.bgLabel.ifBlank { "樱花粉" },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF5A2038),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (state.bgError != null) {
                Text(
                    text = state.bgError,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB00020)
                )
            }
            Text(
                text = "目录：${state.bgDirHint}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF5A2038).copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompanionBackgrounds.PRESETS.forEach { preset ->
                    val selected = state.bgPresetId == preset.id
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(64.dp)
                            .clickable { onSelectPreset(preset.id) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.verticalGradient(preset.colorsArgb.map { Color(it) })
                                )
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            2.dp,
                                            Color(0xFFE85D8E),
                                            RoundedCornerShape(12.dp)
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) Color(0xFFE85D8E) else Color(0xFF5A2038),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onPick) { Text("导入图片") }
                if (state.bgCustomPath.isNotBlank()) {
                    TextButton(onClick = onUseCustom) {
                        Text(
                            if (state.bgPresetId == CompanionBackgrounds.CUSTOM_ID) {
                                "自定义·当前"
                            } else {
                                "用自定义图"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanionMusicPanel(
    state: CompanionUiState,
    onToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPick: () -> Unit,
    onSelectTrack: (Int) -> Unit,
    onVolume: (Float) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .padding(bottom = 8.dp)
            .heightIn(max = 320.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.90f),
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "背景音乐",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF5A2038),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClose) {
                    Text("收起", color = Color(0xFF5A2038))
                }
            }
            Text(
                text = state.musicTitle.ifBlank { "未选曲" } +
                    if (state.musicPlaying) " · 播放中" else " · 暂停",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF5A2038),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (state.musicError != null) {
                Text(
                    text = state.musicError,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB00020)
                )
            }
            Text(
                text = "目录：${state.musicDirHint}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF5A2038).copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrev) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "上一首",
                        tint = Color(0xFFE85D8E)
                    )
                }
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFE85D8E).copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        if (state.musicPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.musicPlaying) "暂停" else "播放",
                        tint = Color(0xFFE85D8E)
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "下一首",
                        tint = Color(0xFFE85D8E)
                    )
                }
            }
            Text(
                text = "音量 ${(state.musicVolume * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF5A2038)
            )
            Slider(
                value = state.musicVolume,
                onValueChange = onVolume,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onPick) { Text("导入音频") }
                TextButton(onClick = onRefresh) { Text("扫描目录") }
            }
            if (state.trackNames.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 90.dp)) {
                    itemsIndexed(state.trackNames) { index, name ->
                        val selected = index == state.trackIndex
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) Color(0xFFE85D8E) else Color(0xFF5A2038),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectTrack(index) }
                                .padding(vertical = 6.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CompanionLive2dWebView(
    modifier: Modifier,
    pushTicket: Long,
    onWebReady: () -> Unit,
    onBridgeCommand: (PetBridgeMessage) -> Unit,
    encodeSession: () -> String?,
    encodeExpression: () -> String?,
    encodePlayMotion: () -> String?,
    encodeBubble: () -> String?,
    encodeLoadLive2d: () -> String?
) {
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    fun pushRaw(webView: WebView, raw: String?) {
        if (raw.isNullOrBlank()) return
        val escaped = raw
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
        webView.evaluateJavascript(
            "window.onNativePetMessage && window.onNativePetMessage('$escaped');",
            null
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val bridge = DesktopPetBridge { msg -> onBridgeCommand(msg) }
            WebView(ctx).apply {
                // 固定 MATCH_PARENT：IME 只抬底栏 imePadding，不改 WebView 自身尺寸
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                setBackgroundColor(0x00000000)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mediaPlaybackRequiresUserGesture = true
                addJavascriptInterface(bridge, DesktopPetBridge.JS_NAME)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        onWebReady()
                    }
                }
                loadUrl(ASSET_URL)
                webViewRef.value = this
                tag = bridge
            }
        },
        update = { webView ->
            if (pushTicket > 0L) {
                pushRaw(webView, encodeLoadLive2d())
                pushRaw(webView, encodeSession())
                pushRaw(webView, encodeExpression())
                pushRaw(webView, encodePlayMotion())
                pushRaw(webView, encodeBubble())
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            webViewRef.value?.let { wv ->
                runCatching {
                    wv.evaluateJavascript(
                        "window.__lanxinPetTeardown && window.__lanxinPetTeardown();",
                        null
                    )
                }
                wv.stopLoading()
                wv.destroy()
            }
            webViewRef.value = null
        }
    }
}

private const val ASSET_URL = "file:///android_asset/pet/desktop-pet.html"

data class CompanionUiState(
    val busy: Boolean = false,
    val statusLine: String = "内置 Live2D · 可直接打字",
    val lastReply: String = "",
    val webPushTicket: Long = 0L,
    val musicPlaying: Boolean = false,
    val musicTitle: String = "",
    val musicError: String? = null,
    val musicDirHint: String = "LanXin/music/",
    val musicVolume: Float = 0.7f,
    val trackNames: List<String> = emptyList(),
    val trackIndex: Int = -1,
    val trackCount: Int = 0,
    val bgPresetId: String = CompanionBackgrounds.DEFAULT_ID,
    val bgCustomPath: String = "",
    val bgLabel: String = "樱花粉",
    val bgDirHint: String = "LanXin/backgrounds/",
    val bgError: String? = null,
    /** 陪伴页「看世界」会话开关；默认关，不持久化（离开页即停） */
    val visionLooking: Boolean = false,
    /** 导游插件是否开（默认 OFF；关则不露入口/不主动相机） */
    val guidePluginEnabled: Boolean = false,
    val visionConsentGranted: Boolean = false,
    val cameraGranted: Boolean = false,
    val visionPreviewReady: Boolean = false,
    val showVisionConsentDialog: Boolean = false,
    val visionHint: String? = null
)

@HiltViewModel
class CompanionViewModel @Inject constructor(
    private val sessionCoordinator: VoiceSessionCoordinator,
    private val petSettings: PetSettings,
    private val sceneSensingSettings: SceneSensingSettings,
    private val visionExplainClient: VisionExplainClient,
    private val smartCapabilitiesSettings: SmartCapabilitiesSettings,
    private val locationSettings: LocationSettings,
    private val locationTool: LocationTool,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompanionUiState())
    val uiState: StateFlow<CompanionUiState> = _uiState.asStateFlow()

    private val bridge = DesktopPetBridge { /* outbound only helper */ }

    @Volatile
    private var lastDecision: Live2dDisplayController.Decision? = null

    @Volatile
    private var modelPath: String = ""

    private var musicPlayer: CompanionMusicPlayer? = null

    private fun player(): CompanionMusicPlayer {
        val existing = musicPlayer
        if (existing != null) return existing
        val created = CompanionMusicPlayer(
            appContext = appContext,
            onState = { st ->
                val names = musicPlayer?.currentTracks()?.map { it.name }.orEmpty()
                _uiState.update {
                    it.copy(
                        musicPlaying = st.playing,
                        musicTitle = st.title,
                        musicError = st.error,
                        trackIndex = st.trackIndex,
                        trackCount = st.trackCount,
                        trackNames = names.ifEmpty { it.trackNames },
                        musicVolume = st.volume01
                    )
                }
            }
        )
        musicPlayer = created
        return created
    }

    fun ensureReady() {
        viewModelScope.launch {
            val config = petSettings.getConfig()
            if (!config.enabled) {
                petSettings.setEnabled(true)
            }
            BuiltInMusicAssets.ensureTestTrackInstalled(appContext)
            val scene = sceneSensingSettings.getConfig()
            val smart = runCatching { smartCapabilitiesSettings.getConfig() }
                .getOrDefault(
                    com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesConfig()
                )
            val guideOn = GuideGate.canShowVisionEntry(
                pluginEnabled = smart.guideEnabled,
                masterEnabled = smart.masterEnabled
            )
            val p = player()
            p.refreshPlaylist()
            resolveLive2d()
            applyBackgroundFromConfig()
            _uiState.update {
                it.copy(
                    musicDirHint = p.musicDirPath(),
                    trackNames = p.currentTracks().map { f -> f.name },
                    trackCount = p.currentTracks().size,
                    musicVolume = p.currentVolume(),
                    visionConsentGranted = scene.consentGranted,
                    // 会话开关默认关；导游插件 OFF 不主动开相机
                    visionLooking = false,
                    visionPreviewReady = false,
                    guidePluginEnabled = guideOn
                )
            }
            bumpWeb()
        }
    }

    fun onLeavePage() {
        musicPlayer?.release()
        musicPlayer = null
        // 关开关即停预览+释放相机
        _uiState.update {
            it.copy(
                visionLooking = false,
                visionPreviewReady = false,
                showVisionConsentDialog = false
            )
        }
    }

    fun setVisionLooking(on: Boolean) {
        viewModelScope.launch {
            val smart = runCatching { smartCapabilitiesSettings.getConfig() }
                .getOrDefault(
                    com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesConfig()
                )
            if (on && !GuideGate.canShowVisionEntry(smart.guideEnabled, smart.masterEnabled)) {
                _uiState.update {
                    it.copy(
                        visionLooking = false,
                        guidePluginEnabled = false,
                        visionHint = "导游插件已关闭（设置 → 智能能力 → 导游）"
                    )
                }
                return@launch
            }
            val scene = sceneSensingSettings.getConfig()
            if (on && CompanionVisionSession.needsConsentDialog(scene.consentGranted, turningOn = true)) {
                _uiState.update {
                    it.copy(
                        showVisionConsentDialog = true,
                        visionConsentGranted = scene.consentGranted,
                        guidePluginEnabled = true
                    )
                }
                return@launch
            }
            if (on) {
                // 同步 #99 enabled，便于设置页一致；consent 已有
                sceneSensingSettings.setEnabled(true)
            }
            _uiState.update {
                it.copy(
                    visionLooking = on,
                    visionConsentGranted = scene.consentGranted,
                    visionPreviewReady = if (on) it.visionPreviewReady else false,
                    visionHint = if (on) null else CompanionVisionSession.STATUS_PAUSED,
                    showVisionConsentDialog = false,
                    guidePluginEnabled = smart.guideEnabled && smart.masterEnabled
                )
            }
        }
    }

    fun dismissVisionConsent() {
        _uiState.update {
            it.copy(showVisionConsentDialog = false, visionLooking = false)
        }
    }

    fun confirmVisionConsentAndEnable() {
        viewModelScope.launch {
            val smart = runCatching { smartCapabilitiesSettings.getConfig() }
                .getOrDefault(
                    com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesConfig()
                )
            if (!GuideGate.canShowVisionEntry(smart.guideEnabled, smart.masterEnabled)) {
                _uiState.update {
                    it.copy(
                        showVisionConsentDialog = false,
                        visionLooking = false,
                        guidePluginEnabled = false,
                        visionHint = "导游插件已关闭（设置 → 智能能力 → 导游）"
                    )
                }
                return@launch
            }
            sceneSensingSettings.setConsentGranted(true)
            sceneSensingSettings.setEnabled(true)
            _uiState.update {
                it.copy(
                    showVisionConsentDialog = false,
                    visionConsentGranted = true,
                    visionLooking = true,
                    visionHint = null,
                    guidePluginEnabled = true
                )
            }
        }
    }

    fun onCameraPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(cameraGranted = granted) }
    }

    fun onVisionPreviewReady(ready: Boolean) {
        _uiState.update { it.copy(visionPreviewReady = ready) }
    }

    /**
     * 点「看一眼」：抓 1 帧送多模态；无 vision 时友好提示，不编造。
     */
    fun lookOnce(holder: CompanionFrameHolder?) {
        viewModelScope.launch {
            val st = _uiState.value
            if (!CompanionVisionSession.shouldCaptureOnAsk(
                    lookingEnabled = st.visionLooking,
                    consentGranted = st.visionConsentGranted,
                    cameraGranted = st.cameraGranted
                )
            ) {
                _uiState.update { it.copy(visionHint = "请先打开「看世界」并授权相机") }
                return@launch
            }
            _uiState.update { it.copy(busy = true, statusLine = "看一眼…") }
            val reply = explainWithFrame(
                question = "请描述你现在看到的画面，并简要讲解。",
                holder = holder
            )
            val display = MoodTagMapper.stripTags(reply)
            _uiState.update {
                it.copy(
                    busy = false,
                    lastReply = display,
                    statusLine = "已看一眼",
                    visionHint = null
                )
            }
            bumpWeb()
        }
    }

    fun onWebReady() {
        viewModelScope.launch {
            resolveLive2d()
            bumpWeb()
        }
    }

    fun onBridgeCommand(msg: PetBridgeMessage) {
        when (msg.command) {
            PetBridgeCommand.CLOSE_PET -> Unit
            PetBridgeCommand.LIVE2D_STATUS -> {
                val mode = msg.payload[PetBridgeProtocol.KEY_LIVE2D_MODE].orEmpty()
                val reason = msg.payload[PetBridgeProtocol.KEY_LIVE2D_REASON].orEmpty()
                val label = when (mode.uppercase()) {
                    "FALLBACK" -> "降级" + if (reason.isNotBlank()) "（$reason）" else ""
                    "PLACEHOLDER" -> "占位"
                    "LIVE2D_REAL" -> lastDecision?.shortLabel ?: "Live2D Cubism"
                    "LIVE2D_SHELL" -> lastDecision?.shortLabel ?: "Live2D 壳"
                    else -> lastDecision?.shortLabel ?: mode.ifBlank { "未知" }
                }
                _uiState.update {
                    if (it.busy || it.statusLine.startsWith("思考") ||
                        it.statusLine.startsWith("已回复") ||
                        it.statusLine.startsWith("出错")
                    ) {
                        it
                    } else {
                        it.copy(statusLine = "显示：$label · 可直接打字（无需 ASR/TTS）")
                    }
                }
            }
            else -> Unit
        }
    }

    fun sendText(text: String, frameHolder: CompanionFrameHolder? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, statusLine = "思考中…") }
            if (!petSettings.getConfig().enabled) {
                petSettings.setEnabled(true)
            }
            val st = _uiState.value
            val useVision = CompanionVisionSession.shouldCaptureOnAsk(
                lookingEnabled = st.visionLooking,
                consentGranted = st.visionConsentGranted,
                cameraGranted = st.cameraGranted
            )
            if (useVision) {
                // P0：提问时抓 1 帧 → 多模态；无 vision 不瞎编
                val reply = explainWithFrame(trimmed, frameHolder)
                val display = MoodTagMapper.stripTags(reply)
                _uiState.update {
                    it.copy(
                        busy = false,
                        lastReply = display,
                        statusLine = "已回复",
                        visionHint = null
                    )
                }
                bumpWeb()
                return@launch
            }
            val result = sessionCoordinator.runRound(
                VoiceSessionInput(
                    asrText = trimmed,
                    isStub = true,
                    source = "companion_text"
                )
            )
            // VoiceSessionResult 已 strip；气泡 / lastReply 不进标签
            val reply = MoodTagMapper.stripTags(
                result.subtitle.ifBlank { result.replyText }
            )
            _uiState.update {
                it.copy(
                    busy = false,
                    lastReply = reply,
                    statusLine = if (result.error != null) {
                        "出错：${result.error}"
                    } else {
                        "已回复"
                    }
                )
            }
            bumpWeb()
        }
    }

    /**
     * 抓帧 → vision 讲解（导游 Guide V1：可选位置增强 + 导航互跳提示）。
     * 能力不足时返回明确文案（禁止假装本地 VLM）。帧仅内存，用完即丢。
     */
    private suspend fun explainWithFrame(
        question: String,
        holder: CompanionFrameHolder?
    ): String {
        val locationSnippet = resolveGuideLocationSnippet()
        val enrichedQuestion = GuidePromptBuilder.buildExplainQuestion(
            userQuestion = question,
            locationSnippet = locationSnippet
        )
        val cap = visionExplainClient.resolveCapability()
        if (!cap.available) {
            // 无 vision：友好提示 + 仍可用 stub 闲聊（不注入假画面描述）
            val stub = runCatching {
                sessionCoordinator.runRound(
                    VoiceSessionInput(
                        asrText = enrichedQuestion,
                        isStub = true,
                        source = "companion_text_no_vision"
                    )
                )
            }.getOrNull()
            val chat = stub?.let {
                MoodTagMapper.stripTags(it.subtitle.ifBlank { it.replyText })
            }.orEmpty()
            val notice = cap.message.ifBlank { VisionModelCapability.MSG_NO_VISION }
            val raw = if (chat.isBlank()) {
                "[[mood=think]]\n$notice"
            } else {
                "[[mood=think]]\n$notice\n\n$chat"
            }
            return GuideNavHandoff.appendIfNeeded(raw, question)
        }
        val bmp = holder?.snapshotCopy()
        if (bmp == null) {
            return "[[mood=sorry]]\n${VisionModelCapability.MSG_CAPTURE_FAILED}"
        }
        val frame = withContext(Dispatchers.Default) {
            try {
                CompanionVisionFrameEncoder.encode(bmp)
            } finally {
                if (!bmp.isRecycled) bmp.recycle()
            }
        }
        if (frame == null) {
            return "[[mood=sorry]]\n${VisionModelCapability.MSG_CAPTURE_FAILED}"
        }
        val reply = when (val r = visionExplainClient.explain(enrichedQuestion, frame)) {
            is VisionExplainResult.Ok -> r.replyText
            is VisionExplainResult.Unavailable -> "[[mood=think]]\n${r.userMessage}"
            is VisionExplainResult.Error -> "[[mood=sorry]]\n${r.userMessage}"
        }
        return GuideNavHandoff.appendIfNeeded(reply, question)
    }

    /**
     * 导游位置增强：插件开 + 主开关 + 位置 prefs 开且有权限时读 last known；失败静默。
     */
    private suspend fun resolveGuideLocationSnippet(): String {
        return runCatching {
            val smart = smartCapabilitiesSettings.getConfig()
            val locOpen = locationSettings.getConfig().enabled && smart.locationEnabled
            if (!GuideGate.canAugmentWithLocation(
                    pluginEnabled = smart.guideEnabled,
                    masterEnabled = smart.masterEnabled,
                    locationPrefsOpen = locOpen
                )
            ) {
                return@runCatching ""
            }
            if (!locationTool.hasPermission()) return@runCatching ""
            val json = locationTool.readOnce()
            val ok = json["ok"]?.jsonPrimitive?.booleanOrNull == true
            if (!ok) return@runCatching ""
            val lat = json["latitude"]?.jsonPrimitive?.doubleOrNull
            val lon = json["longitude"]?.jsonPrimitive?.doubleOrNull
            val acc = json["accuracy_m"]?.jsonPrimitive?.doubleOrNull
            val provider = json["provider"]?.jsonPrimitive?.contentOrNull
            val fix = GuideLocationContext.fromMap(lat, lon, acc, provider)
            GuideLocationContext.snippetOrEmpty(fix)
        }.getOrDefault("")
    }

    fun toggleMusic() {
        player().togglePlayPause()
    }

    fun nextTrack() {
        player().next()
    }

    fun previousTrack() {
        player().previous()
    }

    fun playTrackAt(index: Int) {
        player().playIndex(index)
    }

    fun setMusicVolume(level01: Float) {
        player().setVolume(level01)
    }

    fun refreshMusicPlaylist() {
        viewModelScope.launch {
            BuiltInMusicAssets.ensureTestTrackInstalled(appContext)
            val p = player()
            p.refreshPlaylist()
            _uiState.update {
                it.copy(
                    trackNames = p.currentTracks().map { f -> f.name },
                    trackCount = p.currentTracks().size,
                    musicDirHint = p.musicDirPath()
                )
            }
        }
    }

    fun selectBackgroundPreset(presetId: String) {
        viewModelScope.launch {
            val id = presetId.trim().ifBlank { CompanionBackgrounds.DEFAULT_ID }
            petSettings.setCompanionBackground(id, customPath = null)
            applyBackgroundFromConfig(clearError = true)
        }
    }

    fun importBackground(uriString: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(uriString)
                    val name = queryDisplayName(uri) ?: "bg_${System.currentTimeMillis()}.jpg"
                    val safe = PathImportHelper.sanitizeFileName(name)
                    val destDir = CompanionBackgrounds.backgroundsDirFromStorage(appContext)
                    destDir.mkdirs()
                    val dest = uniqueDest(destDir, safe)
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { out -> input.copyTo(out) }
                    } ?: error("无法打开图片")
                    if (!CompanionBackgrounds.isImageFile(dest)) {
                        dest.delete()
                        error("不支持的格式（请用 jpg/png/webp）")
                    }
                    dest.absolutePath
                }
            }
            result.onSuccess { path ->
                petSettings.setCompanionBackground(
                    CompanionBackgrounds.CUSTOM_ID,
                    customPath = path
                )
                applyBackgroundFromConfig(clearError = true)
            }.onFailure { e ->
                _uiState.update { it.copy(bgError = e.message ?: "导入失败") }
            }
        }
    }

    private suspend fun applyBackgroundFromConfig(clearError: Boolean = false) {
        val config = petSettings.getConfig()
        val resolved = CompanionBackgrounds.resolve(
            config.companionBgPresetId,
            config.companionBgCustomPath
        )
        val label = when (resolved) {
            is CompanionBackgrounds.Resolved.Preset -> resolved.preset.label
            is CompanionBackgrounds.Resolved.Image -> "自定义 · ${resolved.displayName}"
        }
        val dirHint = runCatching {
            CompanionBackgrounds.backgroundsDirFromStorage(appContext).absolutePath
        }.getOrDefault("LanXin/backgrounds/")
        val presetId = when (resolved) {
            is CompanionBackgrounds.Resolved.Preset -> resolved.preset.id
            is CompanionBackgrounds.Resolved.Image -> CompanionBackgrounds.CUSTOM_ID
        }
        val customPath = when (resolved) {
            is CompanionBackgrounds.Resolved.Image -> resolved.path
            else -> config.companionBgCustomPath
        }
        _uiState.update {
            it.copy(
                bgPresetId = presetId,
                bgCustomPath = customPath,
                bgLabel = label,
                bgDirHint = dirHint,
                bgError = if (clearError) null else it.bgError
            )
        }
    }

    fun importAudio(uriString: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(uriString)
                    val name = queryDisplayName(uri) ?: "import_${System.currentTimeMillis()}.mp3"
                    val safe = PathImportHelper.sanitizeFileName(name)
                    val destDir = BuiltInMusicAssets.musicDirFromStorage(appContext)
                    destDir.mkdirs()
                    val dest = uniqueDest(destDir, safe)
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { out -> input.copyTo(out) }
                    } ?: error("无法打开音频")
                    if (!BuiltInMusicAssets.isAudioFile(dest)) {
                        dest.delete()
                        error("不支持的格式（请用 mp3/m4a/wav/ogg）")
                    }
                    dest.absolutePath
                }
            }
            result.onSuccess { path ->
                val p = player()
                p.refreshPlaylist()
                p.playPath(path)
                _uiState.update {
                    it.copy(
                        trackNames = p.currentTracks().map { f -> f.name },
                        trackCount = p.currentTracks().size,
                        musicError = null
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(musicError = e.message ?: "导入失败") }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull()
    }

    private fun uniqueDest(dir: File, name: String): File {
        var dest = File(dir, name)
        if (!dest.exists()) return dest
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 2
        while (dest.exists()) {
            dest = File(dir, if (ext.isEmpty()) "${base}_$i" else "${base}_$i.$ext")
            i++
        }
        return dest
    }

    fun encodeSessionRaw(): String? {
        val snap = sessionCoordinator.current()
        val mode = lastDecision?.mode
            ?: Live2dDisplayController.Live2dDisplayMode.PLACEHOLDER
        return bridge.encodeSession(snap, displayMode = mode)
    }

    fun encodeExpressionRaw(): String? {
        val snap = sessionCoordinator.current()
        val mode = lastDecision?.mode
            ?: Live2dDisplayController.Live2dDisplayMode.PLACEHOLDER
        val phasePose = PetExpressionController.poseFor(snap.phase, mode)
        // 匹配用 raw（含 mood 标签）；lastReply 同 raw 作会话外兜底
        val rawForMatch = snap.replyText
            .ifBlank { snap.subtitle }
            .ifBlank { _uiState.value.lastReply }
        val pose = TextExpressionMotionMapper.overlaySpeakingPose(
            phasePose,
            snap.phase,
            rawForMatch
        )
        return bridge.encodeExpression(pose, snap.phase)
    }

    fun encodePlayMotionRaw(): String? {
        val snap = sessionCoordinator.current()
        if (snap.phase != VoiceSessionPhase.SPEAKING) return null
        val rawForMatch = snap.replyText
            .ifBlank { snap.subtitle }
            .ifBlank { _uiState.value.lastReply }
        if (rawForMatch.isBlank()) return null
        val match = TextExpressionMotionMapper.match(rawForMatch) ?: return null
        val group = match.motionGroup ?: return null
        return bridge.encodePlayMotion(group, match.motionIndex)
    }

    fun encodeBubbleRaw(): String? {
        val snap = sessionCoordinator.current()
        val bubble = MoodTagMapper.stripTags(
            snap.subtitle.ifBlank { snap.replyText }
                .ifBlank { _uiState.value.lastReply }
        )
        return if (bubble.isBlank()) null else bridge.encodeBubble(bubble)
    }

    fun encodeLoadLive2dRaw(): String? {
        val decision = lastDecision ?: Live2dDisplayController.decide(modelPath)
        lastDecision = decision
        return bridge.encodeLoadLive2d(decision)
    }

    private suspend fun resolveLive2d() {
        val pet = petSettings.getConfig()
        val filesDir = appContext.filesDir
        val isDebug = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val safUri = petSettings.getConfig().lanXinSafTreeUri
        val openSourceBase = DebugAssetStorage.resolve(appContext, safUri).baseDir
        val installed = BuiltInLive2dAssets.ensureInstalled(appContext)
        val path = MeijuDebugPaths.resolveLive2dIfPresent(
            filesDir = filesDir,
            configured = pet.live2dModelPath,
            preferBuiltinLogical = true,
            allowMeijuRef = isDebug,
            openSourceBaseDir = openSourceBase
        ).let { resolved ->
            when {
                resolved.isNotBlank() &&
                    !resolved.startsWith("asset://") &&
                    File(resolved).isFile -> resolved
                !installed.isNullOrBlank() -> installed
                else -> resolved.ifBlank { BuiltInLive2dAssets.LOGICAL_PATH }
            }
        }
        modelPath = path
        lastDecision = Live2dModel3Reader.enrich(
            appContext,
            Live2dDisplayController.decide(path)
        )
        val label = lastDecision?.shortLabel ?: "内置"
        _uiState.update {
            it.copy(statusLine = "显示：$label · 可直接打字（无需 ASR/TTS）")
        }
    }

    private fun bumpWeb() {
        _uiState.update { it.copy(webPushTicket = it.webPushTicket + 1) }
    }
}
