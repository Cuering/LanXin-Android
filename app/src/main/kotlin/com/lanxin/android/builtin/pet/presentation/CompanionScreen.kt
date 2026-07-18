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
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lanxin.android.builtin.pet.data.DesktopPetBridge
import com.lanxin.android.builtin.pet.domain.BuiltInLive2dAssets
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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * 妹居式 App 内全屏陪伴页：Live2D（内置 Mao）+ 底部文本输入。
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

    LaunchedEffect(Unit) {
        viewModel.ensureReady()
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
        CompanionLive2dWebView(
            modifier = Modifier.fillMaxSize(),
            pushTicket = state.webPushTicket,
            onWebReady = viewModel::onWebReady,
            onBridgeCommand = viewModel::onBridgeCommand,
            encodeSession = viewModel::encodeSessionRaw,
            encodeExpression = viewModel::encodeExpressionRaw,
            encodeBubble = viewModel::encodeBubbleRaw,
            encodeLoadLive2d = viewModel::encodeLoadLive2dRaw
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
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

            Spacer(modifier = Modifier.weight(1f))

            if (state.busy) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(8.dp),
                    color = Color(0xFFE85D8E)
                )
            }

            if (state.statusLine.isNotBlank()) {
                Text(
                    text = state.statusLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5A2038),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.92f),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
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
    encodeBubble: () -> String?,
    encodeLoadLive2d: () -> String?
) {
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

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
            // ticket 变化时推送最新会话/模型
            if (pushTicket > 0L) {
                fun push(raw: String?) {
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
                push(encodeLoadLive2d())
                push(encodeSession())
                push(encodeExpression())
                push(encodeBubble())
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
    val webPushTicket: Long = 0L
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

    fun ensureReady() {
        viewModelScope.launch {
            // 全屏陪伴不强制悬浮开关；临时允许会话（若总开关关则打开本会话轮次前 ensure）
            val config = petSettings.getConfig()
            if (!config.enabled) {
                petSettings.setEnabled(true)
            }
            resolveLive2d()
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
            PetBridgeCommand.CLOSE_PET -> {
                // 全屏页忽略关闭悬浮语义
            }
            PetBridgeCommand.LIVE2D_STATUS -> {
                // HTML 实际渲染结果：若降级则同步底部状态，避免谎称 Live2D 壳
                val mode = msg.payload[PetBridgeProtocol.KEY_LIVE2D_MODE].orEmpty()
                val reason = msg.payload[PetBridgeProtocol.KEY_LIVE2D_REASON].orEmpty()
                val label = when (mode.uppercase()) {
                    "FALLBACK" -> "降级" + if (reason.isNotBlank()) "（$reason）" else ""
                    "PLACEHOLDER" -> "占位"
                    "LIVE2D_SHELL" -> lastDecision?.shortLabel ?: "Live2D 壳"
                    else -> lastDecision?.shortLabel ?: mode.ifBlank { "未知" }
                }
                _uiState.update {
                    // 保留「思考中/已回复」等瞬时状态
                    if (it.busy || it.statusLine.startsWith("思考") || it.statusLine.startsWith("已回复") ||
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
            // 确保 enabled
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
                    java.io.File(resolved).isFile -> resolved
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
