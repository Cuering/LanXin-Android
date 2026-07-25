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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lanxin.android.builtin.pet.domain.BuiltInLive2dAssets
import com.lanxin.android.builtin.pet.domain.BuiltInMusicAssets
import com.lanxin.android.builtin.pet.domain.CompanionMusicPlayer
import com.lanxin.android.builtin.pet.domain.CompanionVisionSession
import com.lanxin.android.builtin.pet.domain.Live2dDisplayController
import com.lanxin.android.builtin.pet.domain.PetSettings
import com.lanxin.android.builtin.pet.domain.VisionExplainClient
import com.lanxin.android.builtin.pet.domain.VoiceSessionCoordinator
import com.lanxin.android.builtin.platform.domain.SceneSensingSettings
import com.lanxin.android.builtin.voice.domain.VoiceChatSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

@HiltViewModel
class CompanionViewModel @Inject constructor(
    private val sessionCoordinator: VoiceSessionCoordinator,
    private val voiceChatSession: VoiceChatSession,
    private val petSettings: PetSettings,
    private val sceneSensingSettings: SceneSensingSettings,
    private val visionExplainClient: VisionExplainClient,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompanionUiState())
    val uiState: StateFlow<CompanionUiState> = _uiState.asStateFlow()

    val voiceChatUiState = voiceChatSession.uiState

    @Volatile
    private var lastDecision: Live2dDisplayController.Decision? = null

    private var musicPlayer: CompanionMusicPlayer? = null

    fun player(): CompanionMusicPlayer {
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
                        musicError = st.error.orEmpty(),
                        trackIndex = st.trackIndex.coerceAtLeast(0),
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

    fun ensureReady() {
        viewModelScope.launch {
            runCatching { BuiltInLive2dAssets.ensureInstalled(appContext) }
            runCatching { BuiltInMusicAssets.ensureTestTrackInstalled(appContext) }
            val scene = sceneSensingSettings.getConfig()
            val p = player()
            p.refreshPlaylist()
            resolveLive2d()
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

    fun toggleMusic() {
        player().togglePlayPause()
    }

    fun nextTrack() {
        player().next()
    }

    fun previousTrack() {
        player().previous()
    }

    fun setMusicVolume(level: Float) {
        player().setVolume(level)
    }

    private fun resolveLive2d() {
        val installed = BuiltInLive2dAssets.installedModelFile(appContext.filesDir).absolutePath
        lastDecision = Live2dDisplayController.decide(resolvedPath = installed)
    }
}

@Composable
fun CompanionScreen(
    onBackAction: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: CompanionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val voiceUi by viewModel.voiceChatUiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.ensureReady()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.onLeavePage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("全屏陪伴") },
                navigationIcon = {
                    IconButton(onClick = onBackAction) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
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
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                            text = state.musicTitle.ifBlank { "未播放" },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (state.musicError.isNotEmpty()) {
                        Text(
                            text = state.musicError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledIconButton(onClick = viewModel::previousTrack) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "上一首")
                        }
                        FilledIconButton(onClick = viewModel::toggleMusic) {
                            Icon(
                                if (state.musicPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.musicPlaying) "暂停" else "播放",
                            )
                        }
                        FilledIconButton(onClick = viewModel::nextTrack) {
                            Icon(Icons.Default.SkipNext, contentDescription = "下一首")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("音量", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = state.musicVolume,
                        onValueChange = viewModel::setMusicVolume,
                        valueRange = 0f..1f,
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

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "视觉感知",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        FilledIconButton(onClick = { viewModel.setVisionLooking(!state.visionLooking) }) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "切换视觉")
                        }
                    }
                    Text(
                        text = if (state.visionLooking) "已开启" else "已关闭",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    state.visionHint?.let { hint ->
                        Text(
                            text = hint,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("语音会话", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = voiceUi.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
