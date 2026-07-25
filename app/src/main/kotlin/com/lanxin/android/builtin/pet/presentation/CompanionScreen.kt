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

@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runCatching
import kotlinx.coroutines.withContext
import com.lanxin.android.builtin.pet.data.DesktopPetBridge
import com.lanxin.android.builtin.pet.data.Message
import com.lanxin.android.builtin.pet.data.ObservableEvent
import com.lanxin.android.builtin.pet.data.RawPetEvent
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
import com.lanxin.android.builtin.localinference.domain.LocalReplySanitizer
import com.lanxin.android.builtin.pet.domain.MoodTagMapper
import com.lanxin.android.builtin.pet.domain.PetEvent
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
import com.lanxin.android.builtin.pet.domain.VisionExplainResult
import com.lanxin.android.builtin.pet.domain.VisionModelCapability
import com.lanxin.android.builtin.pet.domain.VoiceSessionCoordinator
import com.lanxin.android.builtin.pet.domain.VoiceSessionInput
import com.lanxin.android.builtin.pet.domain.VoiceSessionPhase
import com.lanxin.android.builtin.pet.domain.VoiceSessionResult
import com.lanxin.android.builtin.platform.domain.SceneSensingSettings
import com.lanxin.android.builtin.voice.domain.VoiceChatPhase
import com.lanxin.android.builtin.voice.domain.VoiceChatSession
import com.lanxin.android.util.PathImportHelper

import java.io.File

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 省流版 UI 状态
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

data class CompanionUiState(
    val musicPlaying: Boolean = false,
    val musicTitle: String = "",
    val musicError: String = "",
    val trackIndex: Int = 0,
    val trackCount: Int = 0,
    val trackNames: List<String> = emptyList(),
    val musicVolume: Float = 0.6f,
    val musicDirHint: String = "",
    val voiceChatEnabled: Boolean = false,
    val visionLooking: Boolean = false,
    val visionConsentGranted: Boolean = true,
    val showVisionConsentDialog: Boolean = false,
    val visionPreviewReady: Boolean = false,
    val visionHint: String? = null,
)

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 全屏陪伴 ViewModel
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@HiltViewModel
class CompanionViewModel @Inject constructor(
    private val sessionCoordinator: VoiceSessionCoordinator,
    private val voiceChatSession: VoiceChatSession,
    private val petSettings: PetSettings,
    private val sceneSensingSettings: SceneSensingSettings,
    private val visionExplainClient: VisionExplainClient,
    private val smartCapabilitiesSettings: SmartCapabilitiesSettings,
    private val locationSettings: LocationSettings,
    private val locationTool: LocationTool,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompanionUiState())
    val uiState: StateFlow<CompanionUiState> = _uiState.asStateFlow()

    /** 语音会话 UI 状态（听/识别/说）。 */
    val voiceChatUiState = voiceChatSession.uiState

    // ── 桥 ──────────────────────────────────────────────

    private val bridge = DesktopPetBridge { outbound(it) }

    // ── Live2d ──────────────────────────────────────────

    @Volatile
    private var lastDecision: Live2dDisplayController.Decision? = null

    @Volatile
    private var modelPath: String = ""

    // ── 音乐 ────────────────────────────────────────────

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
                        musicVolume = st.volume01,
                    )
                }
            },
        )
        musicPlayer = created
        return created
    }

    // ── 导游上下文 ───────────────────────────────────────

    private val guideLocationContext = GuideLocationContext(locationTool, locationSettings)

    // ── 公开 API ────────────────────────────────────────

    fun ensureReady() {
        viewModelScope.launch {
            runCatching { BuiltInLive2dAssets.ensureInstalled(appContext) }
            runCatching { BuiltInMusicAssets.ensureInstalled(appContext) }
            val scene = sceneSensingSettings.getConfig()
            val smart = runCatching { smartCapabilitiesSettings.getConfig() }
                .getOrDefault(
                    com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesConfig()
                )
            val guideOn = GuideGate.canShowVisionEntry(
                pluginEnabled = smart.guideEnabled,
                masterEnabled = smart.masterEnabled,
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
                    visionLooking = false,
                    visionPreviewReady = false,
                )
            }
            bumpWeb()
        }
    }

    fun onLeavePage() {
        musicPlayer?.release()
        musicPlayer = null
        viewModelScope.launch {
            runCatching { voiceChatSession.cancel() }
        }
        _uiState.update {
            it.copy(
                visionLooking = false,
                visionPreviewReady = false,
                showVisionConsentDialog = false,
                voiceChatEnabled = false,
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
                        visionHint = "导游插件已关闭（设置 → 智能能力 → 导游）",
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
                    )
                }
                return@launch
            }
            if (on) {
                sceneSensingSettings.setEnabled(true)
            }
            _uiState.update {
                it.copy(
                    visionLooking = on,
                    visionConsentGranted = scene.consentGranted,
                )
            }
        }
    }

    // ── 私有的 ───────────────────────────────────────────

    private fun resolveLive2d() {
        val combined = Live2dDisplayController.resolve(
            petSettings = petSettings,
            model3Reader = Live2dModel3Reader,
            assetsDir = appContext.filesDir,
            storage = DebugAssetStorage,
            isDebug = appContext.applicationInfo?.flags?.and(ApplicationInfo.FLAG_DEBUGGABLE) != 0,
        )
        lastDecision = combined
        combined?.let {
            modelPath = it.modelPath
        }
    }

    private fun applyBackgroundFromConfig() {
        CompanionBackgrounds.applyFromConfig(appContext, petSettings)
    }

    private fun bumpWeb() {
        // 与 DesktopPetViewModel.refresh() 一致的 Live2d WebView 刷新
    }

    private fun outbound(msg: PetBridgeMessage) {
        // only outbound messages for now
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 全屏陪伴 Composable
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionScreen(
    onNavigateBack: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    audioUiViewModel: androidx.lifecycle.ViewModelStoreOwner,
) {
    val vm: CompanionViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        viewModelStoreOwner = androidx.compose.ui.platform.LocalContext.current as androidx.activity.ComponentActivity
    )
    // 临时简化：直接使用平台的 ViewModelProvider
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val ctx = LocalContext.current

    // ── 进入/离开 ────────────────────────────────────────

    LaunchedEffect(Unit) {
        vm.ensureReady()
    }
    DisposableEffect(Unit) {
        onDispose { vm.onLeavePage() }
    }

    // ── UI 订阅 ──────────────────────────────────────────

    val state by vm.uiState.collectAsStateWithLifecycle()
    val voiceUi by vm.voiceChatUiState.collectAsStateWithLifecycle()

    // ── 语音会话框架 ─────────────────────────────────────

    var chatInput by rememberSaveable { mutableStateOf("") }
    var showChatSheet by rememberSaveable { mutableStateOf(false) }

    // ── 选歌 ─────────────────────────────────────────────

    var showPickTrackDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("全屏陪伴") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showChatSheet = true }) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "语音")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── 音乐播放器 ──────────────────────────────────
            item {
                MusicPlayerCard(
                    state = state,
                    onPlayPause = {
                        scope.launch {
                            vm.player().toggle()
                        }
                    },
                    onPrev = {
                        scope.launch {
                            vm.player().prev()
                        }
                    },
                    onNext = {
                        scope.launch {
                            vm.player().next()
                        }
                    },
                    onPickTrack = { showPickTrackDialog = true },
                    onVolumeChange = { vol ->
                        scope.launch {
                            vm.player().setVolume(vol)
                        }
                    },
                    onRefresh = {
                        scope.launch {
                            vm.player().refreshPlaylist()
                        }
                    },
                )
            }
            // ── 视觉 ──────────────────────────────────────
            item {
                VisionCard(
                    state = state,
                    onToggle = { vm.setVisionLooking(!state.visionLooking) },
                )
            }
            // ── 语音 ──────────────────────────────────────
            item {
                VoiceChatCard(
                    voiceUi = voiceUi,
                    onToggle = {
                        scope.launch {
                            vm.voiceChatSession.toggle()
                        }
                    },
                )
            }
        }
    }
}

// ── 子组件 ──────────────────────────────────────────

@Composable
private fun MusicPlayerCard(
    state: CompanionUiState,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPickTrack: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (state.musicPlaying) state.musicTitle else "未播放",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
            if (state.musicError.isNotEmpty()) {
                Text(
                    text = state.musicError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(4.dp))
            // 进度占位
            LinearProgressIndicator(
                progress = { if (state.trackCount > 0) state.trackIndex.toFloat() / state.trackCount else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledIconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(18.dp))
                }
                FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (state.musicPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.musicPlaying) "暂停" else "播放",
                        modifier = Modifier.size(18.dp),
                    )
                }
                FilledIconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                FilledIconButton(onClick = onPickTrack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.List, contentDescription = "选歌", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("音量", style = MaterialTheme.typography.labelSmall)
            Slider(
                value = state.musicVolume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.musicDirHint.isNotEmpty()) {
                Text(
                    text = "音乐目录: ${state.musicDirHint}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VisionCard(
    state: CompanionUiState,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("视觉感知", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilledIconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (state.visionLooking) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (state.visionLooking) "关闭" else "开启",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (state.visionHint != null) {
                Text(
                    text = state.visionHint,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun VoiceChatCard(
    state: com.lanxin.android.builtin.voice.domain.VoiceChatUiState,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("语音会话", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilledIconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (state.isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (state.isListening) "关闭" else "开启",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
