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

import android.content.pm.ApplicationInfo
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runCatching

import com.lanxin.android.builtin.pet.data.DesktopPetBridge
import com.lanxin.android.builtin.pet.domain.BuiltInLive2dAssets
import com.lanxin.android.builtin.pet.domain.BuiltInMusicAssets
import com.lanxin.android.builtin.pet.domain.CompanionBackgrounds
import com.lanxin.android.builtin.pet.domain.CompanionMusicPlayer
import com.lanxin.android.builtin.pet.domain.CompanionVisionSession
import com.lanxin.android.builtin.pet.domain.DebugAssetStorage
import com.lanxin.android.builtin.pet.domain.Live2dDisplayController
import com.lanxin.android.builtin.pet.domain.Live2dModel3Reader
import com.lanxin.android.builtin.pet.domain.MeijuDebugPaths
import com.lanxin.android.builtin.pet.domain.PetBridgeCommand
import com.lanxin.android.builtin.pet.domain.PetBridgeMessage
import com.lanxin.android.builtin.pet.domain.PetBridgeProtocol
import com.lanxin.android.builtin.pet.domain.PetSettings
import com.lanxin.android.builtin.pet.domain.VisionExplainClient
import com.lanxin.android.builtin.pet.domain.VoiceSessionCoordinator
import com.lanxin.android.builtin.platform.domain.SceneSensingSettings
import com.lanxin.android.builtin.voice.domain.VoiceChatSession

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

    private val bridge = DesktopPetBridge { }

    @Volatile
    private var lastDecision: Live2dDisplayController.Decision? = null

    @Volatile
    private var modelPath: String = ""

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

    fun ensureReady() {
        viewModelScope.launch {
            runCatching { BuiltInLive2dAssets.ensureInstalled(appContext) }
            runCatching { BuiltInMusicAssets.ensureInstalled(appContext) }
            val scene = sceneSensingSettings.getConfig()
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

    private fun resolveLive2d() {
        Live2dDisplayController.resolve(
            petSettings = petSettings,
            model3Reader = Live2dModel3Reader,
            assetsDir = appContext.filesDir,
            storage = DebugAssetStorage,
            isDebug = appContext.applicationInfo?.flags?.and(ApplicationInfo.FLAG_DEBUGGABLE) != 0,
        )?.let {
            lastDecision = it
            modelPath = it.modelPath
        }
    }

    private fun applyBackgroundFromConfig() {
        CompanionBackgrounds.applyFromConfig(appContext, petSettings)
    }

    private fun bumpWeb() { }
}
