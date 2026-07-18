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

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lanxin.android.builtin.pet.data.DesktopPetBridge
import com.lanxin.android.builtin.pet.domain.BuiltInLive2dAssets
import com.lanxin.android.builtin.pet.domain.BuiltInMusicAssets
import com.lanxin.android.builtin.pet.domain.CompanionMusicPlayer
import com.lanxin.android.builtin.pet.domain.DebugAssetStorage
import com.lanxin.android.builtin.pet.domain.Live2dDisplayController
import com.lanxin.android.builtin.pet.domain.Live2dModel3Reader
import com.lanxin.android.builtin.pet.domain.MeijuDebugPaths
import com.lanxin.android.builtin.pet.domain.PetBridgeCommand
import com.lanxin.android.builtin.pet.domain.PetBridgeMessage
import com.lanxin.android.builtin.pet.domain.PetBridgeProtocol
import com.lanxin.android.builtin.pet.domain.PetExpressionController
import com.lanxin.android.builtin.pet.domain.PetSettings
import com.lanxin.android.builtin.pet.domain.VoiceSessionCoordinator
import com.lanxin.android.builtin.pet.domain.VoiceSessionInput
import com.lanxin.android.util.PathImportHelper
import dagger.hilt.android.lifecycle.HiltViewModel
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
    var draft by remember { mutableStateOf("") }
    var musicPanelOpen by remember { mutableStateOf(false) }

    val pickAudio = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importAudio(uri.toString())
        }
    }

    LaunchedEffect(Unit) {
        viewModel.ensureReady()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.onLeavePage() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFFE4EC),
                        Color(0xFFFFC1D6),
                        Color(0xFFFF9BB8)
                    )
                )
            )
    ) {
        // Live2D / 占位 / 降级：全屏铺底，无卡片裁切
        CompanionLive2dWebView(
            modifier = Modifier.fillMaxSize(),
            pushTicket = state.webPushTicket,
            beatTicket = state.beatPushTicket,
            onWebReady = viewModel::onWebReady,
            onBridgeCommand = viewModel::onBridgeCommand,
            encodeSession = viewModel::encodeSessionRaw,
            encodeExpression = viewModel::encodeExpressionRaw,
            encodeBubble = viewModel::encodeBubbleRaw,
            encodeLoadLive2d = viewModel::encodeLoadLive2dRaw,
            encodeMusicBeat = viewModel::encodeMusicBeatRaw
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

        // 状态角标：轻量浮层（Cubism·闲置 等）
        if (state.statusLine.isNotBlank()) {
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
                Text(
                    text = companionStatusBadge(state.statusLine),
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
                        placeholderColor = Color(0xFF5A2038).copy(alpha = 0.55f)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            val text = draft
                            if (text.isNotBlank() && !state.busy) {
                                viewModel.sendText(text)
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
                            viewModel.sendText(text)
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

        // 右下角半透明音乐图标 + 弹出控制（抬高避开底栏）
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .imePadding()
                .padding(end = 14.dp, bottom = 88.dp),
            horizontalAlignment = Alignment.End
        ) {
            AnimatedVisibility(visible = musicPanelOpen) {
                CompanionMusicPanel(
                    state = state,
                    onToggle = viewModel::toggleMusic,
                    onPrev = viewModel::previousTrack,
                    onNext = viewModel::nextTrack,
                    onPick = { pickAudio.launch(arrayOf("audio/*")) },
                    onSelectTrack = viewModel::playTrackAt,
                    onToggleBeatSway = viewModel::setMusicBeatSway,
                    onVolume = viewModel::setMusicVolume,
                    onRefresh = viewModel::refreshMusicPlaylist,
                    onClose = { musicPanelOpen = false }
                )
            }
            if (state.trackCount > 0 || state.musicPlaying) {
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .alpha(if (state.musicPlaying) 0.92f else 0.55f)
                        .clickable { musicPanelOpen = !musicPanelOpen },
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
                        .clickable { musicPanelOpen = !musicPanelOpen },
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
private fun CompanionMusicPanel(
    state: CompanionUiState,
    onToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPick: () -> Unit,
    onSelectTrack: (Int) -> Unit,
    onToggleBeatSway: (Boolean) -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "跟随节奏晃动",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5A2038),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = state.musicBeatSway,
                    onCheckedChange = onToggleBeatSway
                )
            }
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
    beatTicket: Long,
    onWebReady: () -> Unit,
    onBridgeCommand: (PetBridgeMessage) -> Unit,
    encodeSession: () -> String?,
    encodeExpression: () -> String?,
    encodeBubble: () -> String?,
    encodeLoadLive2d: () -> String?,
    encodeMusicBeat: () -> String?
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
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
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
                pushRaw(webView, encodeBubble())
            }
            if (beatTicket > 0L) {
                pushRaw(webView, encodeMusicBeat())
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
    val beatPushTicket: Long = 0L,
    val musicPlaying: Boolean = false,
    val musicTitle: String = "",
    val musicError: String? = null,
    val musicDirHint: String = "LanXin/music/",
    val musicBeatSway: Boolean = true,
    val musicVolume: Float = 0.7f,
    val beatLevel: Float = 0f,
    val trackNames: List<String> = emptyList(),
    val trackIndex: Int = -1,
    val trackCount: Int = 0
)

@HiltViewModel
class CompanionViewModel @Inject constructor(
    private val sessionCoordinator: VoiceSessionCoordinator,
    private val petSettings: PetSettings,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompanionUiState())
    val uiState: StateFlow<CompanionUiState> = _uiState.asStateFlow()

    private val bridge = DesktopPetBridge { /* outbound only helper */ }

    @Volatile
    private var lastDecision: Live2dDisplayController.Decision? = null

    @Volatile
    private var modelPath: String = ""

    @Volatile
    private var lastBeatLevel: Float = 0f

    @Volatile
    private var beatSwayEnabled: Boolean = true

    /** WebView beat 推送限频：约 12Hz，避免每帧 evaluateJavascript 造成剧抖。 */
    private var lastBeatPushMs: Long = 0L
    private var lastPushedBeatLevel: Float = -1f

    private var musicPlayer: CompanionMusicPlayer? = null

    private fun player(): CompanionMusicPlayer {
        val existing = musicPlayer
        if (existing != null) return existing
        val created = CompanionMusicPlayer(
            appContext = appContext,
            onBeat = { level ->
                lastBeatLevel = level
                if (!beatSwayEnabled) return@CompanionMusicPlayer
                val now = System.currentTimeMillis()
                // 限频 ~12Hz；幅度变化极小则跳过
                val elapsed = now - lastBeatPushMs
                val delta = kotlin.math.abs(level - lastPushedBeatLevel)
                if (elapsed < 80L && delta < 0.03f) return@CompanionMusicPlayer
                if (elapsed < 40L) return@CompanionMusicPlayer
                lastBeatPushMs = now
                lastPushedBeatLevel = level
                _uiState.update {
                    it.copy(
                        beatLevel = level,
                        beatPushTicket = it.beatPushTicket + 1
                    )
                }
            },
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
            beatSwayEnabled = config.musicBeatSway
            BuiltInMusicAssets.ensureTestTrackInstalled(appContext)
            val p = player()
            p.setBeatEnabled(beatSwayEnabled)
            p.refreshPlaylist()
            resolveLive2d()
            _uiState.update {
                it.copy(
                    musicBeatSway = beatSwayEnabled,
                    musicDirHint = p.musicDirPath(),
                    trackNames = p.currentTracks().map { f -> f.name },
                    trackCount = p.currentTracks().size,
                    musicVolume = p.currentVolume()
                )
            }
            bumpWeb()
        }
    }

    fun onLeavePage() {
        musicPlayer?.release()
        musicPlayer = null
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

    fun sendText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, statusLine = "思考中…") }
            if (!petSettings.getConfig().enabled) {
                petSettings.setEnabled(true)
            }
            val result = sessionCoordinator.runRound(
                VoiceSessionInput(
                    asrText = trimmed,
                    isStub = true,
                    source = "companion_text"
                )
            )
            val reply = result.subtitle.ifBlank { result.replyText }
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

    fun setMusicBeatSway(enabled: Boolean) {
        viewModelScope.launch {
            petSettings.setMusicBeatSway(enabled)
            beatSwayEnabled = enabled
            player().setBeatEnabled(enabled)
            if (!enabled) {
                lastBeatLevel = 0f
                lastPushedBeatLevel = 0f
            }
            // 开关切换立即推一次（关=0），不受限频阻塞
            lastBeatPushMs = 0L
            _uiState.update {
                it.copy(
                    musicBeatSway = enabled,
                    beatLevel = if (enabled) lastBeatLevel else 0f,
                    beatPushTicket = it.beatPushTicket + 1
                )
            }
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
        val pose = PetExpressionController.poseFor(snap.phase, mode)
        return bridge.encodeExpression(pose, snap.phase)
    }

    fun encodeBubbleRaw(): String? {
        val snap = sessionCoordinator.current()
        val bubble = snap.subtitle.ifBlank { snap.replyText }
            .ifBlank { _uiState.value.lastReply }
        return if (bubble.isBlank()) null else bridge.encodeBubble(bubble)
    }

    fun encodeLoadLive2dRaw(): String? {
        val decision = lastDecision ?: Live2dDisplayController.decide(modelPath)
        lastDecision = decision
        return bridge.encodeLoadLive2d(decision)
    }

    fun encodeMusicBeatRaw(): String? {
        return bridge.encodeMusicBeat(
            level01 = if (beatSwayEnabled) lastBeatLevel else 0f,
            enabled = beatSwayEnabled
        )
    }

    private suspend fun resolveLive2d() {
        val pet = petSettings.getConfig()
        val filesDir = appContext.filesDir
        val isDebug = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val openSourceBase = DebugAssetStorage.resolve(appContext).baseDir
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
