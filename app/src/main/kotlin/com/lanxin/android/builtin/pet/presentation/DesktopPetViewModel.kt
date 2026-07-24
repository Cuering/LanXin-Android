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

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lanxin.android.builtin.localinference.domain.LocalInferenceSettings
import com.lanxin.android.builtin.pet.data.FloatingPetService
import com.lanxin.android.builtin.pet.data.OverlayPermissionHelper
import com.lanxin.android.builtin.pet.domain.BuiltInLive2dAssets
import com.lanxin.android.builtin.pet.domain.DebugAssetCatalog
import com.lanxin.android.builtin.pet.domain.DebugAssetDownloadEvent
import com.lanxin.android.builtin.pet.domain.DebugAssetDownloader
import com.lanxin.android.builtin.pet.domain.DebugAssetItemUi
import com.lanxin.android.builtin.pet.domain.DebugAssetKind
import com.lanxin.android.builtin.pet.domain.DebugAssetLicense
import com.lanxin.android.builtin.pet.domain.DebugAssetMirror
import com.lanxin.android.builtin.pet.domain.DebugAssetStorage
import com.lanxin.android.builtin.pet.domain.DebugOpenSourcePaths
import com.lanxin.android.builtin.pet.domain.LanXinSafTree
import com.lanxin.android.builtin.pet.domain.Live2dDisplayController
import com.lanxin.android.builtin.pet.domain.Live2dModelCatalog
import com.lanxin.android.builtin.pet.domain.MeijuDebugPaths
import com.lanxin.android.builtin.pet.domain.MoodTagMapper
import com.lanxin.android.builtin.pet.domain.PetExpressionController
import com.lanxin.android.builtin.pet.domain.PetPathReadiness
import com.lanxin.android.builtin.pet.domain.PetResourceResolver
import com.lanxin.android.builtin.pet.domain.PetSettings
import com.lanxin.android.builtin.pet.domain.TextExpressionMotionMapper
import com.lanxin.android.builtin.pet.domain.VoiceSessionCoordinator
import com.lanxin.android.builtin.pet.domain.VoiceSessionPhase
import com.lanxin.android.builtin.voice.data.AndroidTtsFallback
import com.lanxin.android.builtin.voice.domain.AsrSettings
import com.lanxin.android.builtin.voice.domain.TtsEngine
import com.lanxin.android.builtin.voice.domain.TtsSettings
import com.lanxin.android.util.LocalPathImporter
import com.lanxin.android.util.PathImportHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DesktopPetUiState(
    val enabled: Boolean = false,
    val overlayRunning: Boolean = false,
    val canDrawOverlays: Boolean = false,
    val phase: VoiceSessionPhase = VoiceSessionPhase.IDLE,
    val asrText: String = "",
    val replyText: String = "",
    val subtitle: String = "",
    val lastError: String? = null,
    val sessionPreview: String = "",
    val isBusy: Boolean = false,
    val snackbarMessage: String? = null,
    val ttsEnabled: Boolean = false,
    /** DataStore 原始 live2d 路径（用户配置）。 */
    val live2dModelPathConfigured: String = "",
    /** 解析后的运行时路径。 */
    val live2dModelPathResolved: String = "",
    /** DataStore 原始配置（用于选择器摘要）。 */
    val asrModelPathConfigured: String = "",
    val ttsModelDirConfigured: String = "",
    val ttsReferenceConfigured: String = "",
    val ttsModelDirResolved: String = "",
    val ttsReferenceResolved: String = "",
    val asrModelPathResolved: String = "",
    /** 选择器导入进行中。 */
    val pathImportBusy: Boolean = false,
    /** 设置页标注：当前：自定义 / 内置示例 / Debug 开源包 / 妹居参考 / 占位。 */
    val live2dSourceLabel: String = "当前：占位 / 未配置",
    val ttsSourceLabel: String = "当前：占位 / 未配置",
    val asrSourceLabel: String = "当前：占位 / 未配置",
    /** 路径就绪短标签。 */
    val live2dReadyLabel: String = "未就绪",
    val asrReadyLabel: String = "未就绪",
    val ttsReadyLabel: String = "未就绪",
    val live2dReady: Boolean = false,
    val asrReady: Boolean = false,
    val ttsReady: Boolean = false,
    /** M2b：显示模式短标签（占位 / Live2D 壳 / 降级）。 */
    val live2dDisplayLabel: String = "占位",
    val live2dDisplayMode: String = Live2dDisplayController.Live2dDisplayMode.PLACEHOLDER.name,
    /** M2b 打磨：当前会话表情短标签（随相位）。 */
    val expressionLabel: String = "闲置",
    val expressionName: String = PetExpressionController.Expression.IDLE_SMILE.name,
    val mouthAnimating: Boolean = false,
    /** 缺资源时的用户可读引导（占位/降级仍可演示表情）。 */
    val resourceGuide: String = "",
    /** 汇总：就绪 / 缺失引导 fetch 脚本。 */
    val resourceSummary: String = "",
    /** 本地脑路径键 + 1.5B 说明（M2a 预留展示）。 */
    val localLlmPathConfigured: String = "",
    val localLlmReadyLabel: String = "未配置",
    val localLlmHint: String = DebugOpenSourcePaths.LOCAL_LLM_DEFAULT_HINT,
    val fetchScriptHint: String = DebugOpenSourcePaths.FETCH_SCRIPT_HINT,
    val isDebugBuild: Boolean = false,
    /** App 内下载：镜像偏好。 */
    val preferredMirror: DebugAssetMirror = DebugAssetMirror.MIRROR_CDN,
    /** Live2D / ASR / TTS 分项下载 UI。 */
    val downloadItems: List<DebugAssetItemUi> = emptyList(),
    val downloadBusy: Boolean = false,
    val live2dLicenseHint: String = DebugAssetLicense.LIVE2D_HINT,
    /** 内置优先说明（设置页下载区）。 */
    val live2dBuiltinHint: String = DebugAssetCatalog.LIVE2D_BUILTIN_PRIMARY_HINT,
    /** 实际落盘根（LanXin）绝对路径，下载成功后展示给用户。 */
    val downloadRootPath: String = "",
    val downloadRootFallback: Boolean = false,
    /** SAF 公共 LanXin 树是否已授权。 */
    val safGranted: Boolean = false,
    /** SAF 树可写探测。 */
    val safWritable: Boolean = false,
    val safDisplayLabel: String = "",
    val safTreeUri: String = "",
    /** Live2D 可切换模型列表（内置 Mao 与 LanXin/live2d 下各模型）。 */
    val live2dModels: List<Live2dModelCatalog.ModelEntry> = emptyList(),
    val live2dCurrentName: String = Live2dModelCatalog.BUILTIN_DISPLAY_NAME,
    /** 文件管理器提示：LanXin/live2d 绝对路径。 */
    val live2dDirHint: String = ""
)

@HiltViewModel
class DesktopPetViewModel @Inject constructor(
    application: Application,
    private val petSettings: PetSettings,
    private val sessionCoordinator: VoiceSessionCoordinator,
    private val ttsSettings: TtsSettings,
    private val ttsEngine: TtsEngine,
    private val androidTts: AndroidTtsFallback,
    private val asrSettings: AsrSettings,
    private val localInferenceSettings: LocalInferenceSettings,
    private val assetDownloader: DebugAssetDownloader,
    private val pathImporter: LocalPathImporter
) : AndroidViewModel(application) {

    private val isDebugBuild: Boolean =
        (application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val _uiState = MutableStateFlow(
        DesktopPetUiState(
            isDebugBuild = isDebugBuild,
            downloadItems = defaultDownloadItems()
        )
    )
    val uiState: StateFlow<DesktopPetUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null

    init {
        viewModelScope.launch {
            sessionCoordinator.snapshot.collect { snap ->
                val mode = runCatching {
                    Live2dDisplayController.Live2dDisplayMode.valueOf(
                        _uiState.value.live2dDisplayMode
                    )
                }.getOrDefault(Live2dDisplayController.Live2dDisplayMode.PLACEHOLDER)
                val phasePose = PetExpressionController.poseFor(snap.phase, mode)
                // 匹配优先 replyText（SPEAKING 期可能含 mood 标签）
                val rawForMatch = snap.replyText.ifBlank { snap.subtitle }
                val pose = TextExpressionMotionMapper.overlaySpeakingPose(
                    phasePose,
                    snap.phase,
                    rawForMatch
                )
                val displayReply = MoodTagMapper.stripTags(snap.replyText)
                val displaySubtitle = MoodTagMapper.stripTags(snap.subtitle)
                _uiState.update {
                    it.copy(
                        phase = snap.phase,
                        asrText = snap.asrText,
                        replyText = displayReply,
                        subtitle = displaySubtitle,
                        lastError = snap.lastError,
                        expressionLabel = pose.shortLabel,
                        expressionName = pose.expression.name,
                        mouthAnimating = pose.mouthAnimating,
                        sessionPreview = formatPreview(
                            snap.phase.name,
                            snap.asrText,
                            displayReply,
                            snap.lastError
                        )
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            // 仓内 Mao → filesDir，设置页与悬浮层共用
            BuiltInLive2dAssets.ensureInstalled(app)
            val config = petSettings.getConfig()
            val storageRoot = DebugAssetStorage.resolve(app, config.lanXinSafTreeUri)
            val tts = ttsSettings.getConfig()
            val asr = asrSettings.getConfig()
            val local = localInferenceSettings.getConfig()
            val resolved = PetResourceResolver.resolve(
                filesDir = app.filesDir,
                pet = config,
                tts = tts,
                asr = asr,
                isDebug = isDebugBuild,
                openSourceBaseDir = storageRoot.baseDir,
                localLlmConfigured = local.modelPath
            )
            // 配置路径失效但开源包已下载：把解析到的真实路径回写 DataStore，
            // 避免开关打开后引擎仍读空/失效路径
            healModelPathsIfNeeded(
                asrConfigured = asr.modelPath,
                asrResolved = resolved.asrModelPath,
                ttsConfigured = tts.modelDir.ifBlank { tts.modelPath },
                ttsResolved = resolved.ttsModelDir,
                llmConfigured = local.modelPath,
                llmResolved = resolved.localLlmModelPath
            )
            val live2dCheck = PetPathReadiness.check(
                PetPathReadiness.Kind.LIVE2D,
                resolved.live2dModelPath
            )
            val asrCheck = PetPathReadiness.check(
                PetPathReadiness.Kind.ASR,
                resolved.asrModelPath
            )
            val ttsCheck = PetPathReadiness.check(
                PetPathReadiness.Kind.TTS,
                resolved.ttsModelDir
            )
            // 无 Sherpa 模型时走 Android 系统 TTS 回退
            val ttsEffectiveReady = ttsCheck.ready || androidTts.available
            val ttsEffectiveLabel = when {
                ttsCheck.ready -> ttsCheck.label
                androidTts.available -> "已就绪（系统TTS）"
                else -> ttsCheck.label
            }
            val llmCheck = PetPathReadiness.check(
                PetPathReadiness.Kind.LOCAL_LLM,
                resolved.localLlmModelPath.ifBlank { local.modelPath }
            )
            val live2dDecision = Live2dDisplayController.decide(resolved.live2dModelPath)
            val can = OverlayPermissionHelper.canDrawOverlays(app)
            val snap = sessionCoordinator.current()
            val phasePose = PetExpressionController.poseFor(snap.phase, live2dDecision.mode)
            val pose = TextExpressionMotionMapper.overlaySpeakingPose(
                phasePose,
                snap.phase,
                snap.replyText.ifBlank { snap.subtitle }
            )
            val guide = PetExpressionController.guideForMissingResources(
                live2dReady = live2dCheck.ready,
                asrReady = asrCheck.ready,
                ttsReady = ttsEffectiveReady
            )
            // 将内置 Mao 同步到 LanXin/live2d/Mao/（用户文件管理器可找）
            withContext(Dispatchers.IO) {
                Live2dModelCatalog.ensureBuiltinExported(app, storageRoot.lanXinDir)
            }
            val live2dModels = Live2dModelCatalog.listModels(
                configuredPath = config.live2dModelPath,
                resolvedPath = resolved.live2dModelPath,
                filesDir = app.filesDir,
                lanXinDir = storageRoot.lanXinDir
            )
            val live2dName = Live2dModelCatalog.currentDisplayName(
                live2dModels,
                config.live2dModelPath,
                resolved.live2dModelPath
            )
            val live2dDir = Live2dModelCatalog.live2dRootDisplay(storageRoot.lanXinDir)
            _uiState.update {
                it.copy(
                    enabled = config.enabled,
                    overlayRunning = config.overlayRunning,
                    canDrawOverlays = can,
                    ttsEnabled = tts.enabled,
                    live2dModelPathConfigured = config.live2dModelPath,
                    live2dModelPathResolved = resolved.live2dModelPath,
                    asrModelPathConfigured = asr.modelPath,
                    ttsModelDirConfigured = tts.modelDir.ifBlank { tts.modelPath },
                    ttsReferenceConfigured = tts.referenceAudio,
                    ttsModelDirResolved = resolved.ttsModelDir,
                    ttsReferenceResolved = resolved.ttsReferenceAudio,
                    asrModelPathResolved = resolved.asrModelPath,
                    live2dSourceLabel = resolved.live2dLabel,
                    ttsSourceLabel = resolved.ttsLabel,
                    asrSourceLabel = resolved.asrLabel,
                    live2dReadyLabel = live2dCheck.label,
                    asrReadyLabel = asrCheck.label,
                    ttsReadyLabel = ttsEffectiveLabel,
                    live2dReady = live2dCheck.ready,
                    asrReady = asrCheck.ready,
                    ttsReady = ttsEffectiveReady,
                    live2dDisplayLabel = live2dDecision.shortLabel,
                    live2dDisplayMode = live2dDecision.mode.name,
                    expressionLabel = pose.shortLabel,
                    expressionName = pose.expression.name,
                    mouthAnimating = pose.mouthAnimating,
                    resourceGuide = guide,
                    resourceSummary = if (ttsEffectiveReady && !ttsCheck.ready) {
                        "Live2D：${live2dCheck.label} · ASR：${asrCheck.label} · TTS：$ttsEffectiveLabel 注：TTS 使用 Android 系统引擎（无需下载模型）"
                    } else {
                        PetPathReadiness.summaryMessage(
                            live2dCheck, asrCheck, ttsCheck, llmCheck
                        )
                    },
                    localLlmPathConfigured = resolved.localLlmModelPath.ifBlank { local.modelPath },
                    localLlmReadyLabel = when {
                        resolved.localLlmModelPath.isBlank() && local.modelPath.isBlank() ->
                            "未配置"
                        else -> llmCheck.label
                    },
                    localLlmHint = DebugOpenSourcePaths.LOCAL_LLM_DEFAULT_HINT,
                    fetchScriptHint = DebugOpenSourcePaths.FETCH_SCRIPT_HINT,
                    isDebugBuild = isDebugBuild,
                    downloadItems = buildDownloadItems(storageRoot.baseDir),
                    downloadRootPath = storageRoot.displayPath,
                    downloadRootFallback = storageRoot.usedFallback,
                    safGranted = storageRoot.safGranted,
                    safWritable = storageRoot.safWritable,
                    safDisplayLabel = storageRoot.safDisplayLabel,
                    safTreeUri = storageRoot.safTreeUri,
                    live2dModels = live2dModels,
                    live2dCurrentName = live2dName,
                    live2dDirHint = live2dDir,
                    phase = snap.phase,
                    asrText = snap.asrText,
                    replyText = MoodTagMapper.stripTags(snap.replyText),
                    subtitle = MoodTagMapper.stripTags(snap.subtitle),
                    lastError = snap.lastError,
                    sessionPreview = formatPreview(
                        snap.phase.name,
                        snap.asrText,
                        MoodTagMapper.stripTags(snap.replyText),
                        snap.lastError
                    )
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            petSettings.setEnabled(enabled)
            if (!enabled) {
                FloatingPetService.stop(getApplication())
                petSettings.setOverlayRunning(false)
                sessionCoordinator.reset()
            }
            if (enabled && !ttsSettings.getConfig().enabled) {
                ttsSettings.setEnabled(true)
                ttsEngine.load(ttsSettings.getConfig())
            }
            _uiState.update { it.copy(enabled = enabled) }
            refresh()
        }
    }

    fun setLive2dModelPath(path: String) {
        viewModelScope.launch {
            petSettings.setLive2dModelPath(path.ifBlank { null })
            notifyLive2dReloadIfRunning()
            refresh()
            _uiState.update {
                it.copy(
                    snackbarMessage = if (path.isBlank()) {
                        "已清除 Live2D 自定义路径"
                    } else {
                        "Live2D 路径已保存"
                    }
                )
            }
        }
    }

    /**
     * 设置页一键切换：写入 [live2d_model_path] 并在桌宠运行时立即 RELOAD。
     * 内置 Mao：优先写已安装绝对路径；未安装则清空走默认解析。
     */
    fun selectLive2dModel(entry: Live2dModelCatalog.ModelEntry) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val path = Live2dModelCatalog.resolveSwitchPath(entry, app.filesDir)
            petSettings.setLive2dModelPath(path)
            notifyLive2dReloadIfRunning()
            refresh()
            _uiState.update {
                it.copy(snackbarMessage = "已切换 Live2D：${entry.displayName}")
            }
        }
    }

    /** 将内置 Mao 导出到 LanXin/live2d/Mao/（用户主动点「同步到目录」）。 */
    fun exportBuiltinLive2dToLanXin() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val cfg = petSettings.getConfig()
            val root = DebugAssetStorage.resolve(app, cfg.lanXinSafTreeUri)
            val path = withContext(Dispatchers.IO) {
                Live2dModelCatalog.ensureBuiltinExported(app, root.lanXinDir)
            }
            refresh()
            _uiState.update {
                it.copy(
                    snackbarMessage = if (path != null) {
                        "内置 Mao 已同步到 $path"
                    } else {
                        "同步失败：内置 Sample 不可用"
                    }
                )
            }
        }
    }

    fun showLive2dDirHint() {
        val hint = _uiState.value.live2dDirHint.ifBlank {
            "LanXin/live2d/"
        }
        val message = buildString {
            append("请在文件管理器中打开：")
            append(hint)
            append('\n')
            append("每个模型一个文件夹，内含 *.model3.json。导入或下载后会出现在此列表。")
        }
        _uiState.update {
            it.copy(snackbarMessage = message)
        }
    }

    private fun notifyLive2dReloadIfRunning() {
        val app = getApplication<Application>()
        if (_uiState.value.overlayRunning) {
            FloatingPetService.reloadLive2d(app)
        }
    }

    fun setAsrModelPath(path: String) {
        viewModelScope.launch {
            asrSettings.setModelPath(path.ifBlank { null })
            refresh()
            _uiState.update {
                it.copy(snackbarMessage = if (path.isBlank()) "已清除 ASR 模型路径" else "ASR 路径已保存")
            }
        }
    }

    fun setTtsModelDir(path: String) {
        viewModelScope.launch {
            ttsSettings.setModelDir(path.ifBlank { null })
            refresh()
            _uiState.update {
                it.copy(snackbarMessage = if (path.isBlank()) "已清除 TTS 模型目录" else "TTS 目录已保存")
            }
        }
    }

    fun setTtsReferenceAudio(path: String) {
        viewModelScope.launch {
            ttsSettings.setReferenceAudio(path.ifBlank { null })
            refresh()
            _uiState.update {
                it.copy(snackbarMessage = if (path.isBlank()) "已清除 TTS 参考音频" else "TTS 参考音已保存")
            }
        }
    }

    fun setLocalLlmModelPath(path: String) {
        viewModelScope.launch {
            localInferenceSettings.setModelPath(path.ifBlank { null })
            refresh()
            _uiState.update {
                it.copy(snackbarMessage = if (path.isBlank()) "已清除本地脑路径" else "本地脑路径已保存")
            }
        }
    }

    /** SAF：选择单个 `*.model3.json` 并导入私有目录。 */
    fun importLive2dFromDocument(uriString: String) {
        importPath("Live2D") {
            val result = pathImporter.importLive2dModel3(uriString)
            result.getOrNull()?.let { petSettings.setLive2dModelPath(it.absolutePath) }
            result
        }
    }

    /** SAF：选择文件夹，自动定位 model3.json。 */
    fun importLive2dFromTree(uriString: String) {
        importPath("Live2D") {
            val result = pathImporter.importLive2dTree(uriString)
            result.getOrNull()?.let { petSettings.setLive2dModelPath(it.absolutePath) }
            result
        }
    }

    fun importAsrFromTree(uriString: String) {
        importPath("ASR") {
            val result = pathImporter.importTree(uriString, PathImportHelper.Kind.ASR)
            result.getOrNull()?.let { asrSettings.setModelPath(it.absolutePath) }
            result
        }
    }

    fun importTtsDirFromTree(uriString: String) {
        importPath("TTS") {
            val result = pathImporter.importTree(uriString, PathImportHelper.Kind.TTS_DIR)
            result.getOrNull()?.let { ttsSettings.setModelDir(it.absolutePath) }
            result
        }
    }

    fun importTtsReferenceFromDocument(uriString: String) {
        importPath("TTS 参考音") {
            val result = pathImporter.importFile(uriString, PathImportHelper.Kind.TTS_REF)
            result.getOrNull()?.let { ttsSettings.setReferenceAudio(it.absolutePath) }
            result
        }
    }

    private fun importPath(label: String, block: suspend () -> Result<*>) {
        if (_uiState.value.pathImportBusy) {
            _uiState.update { it.copy(snackbarMessage = "正在导入，请稍候") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(pathImportBusy = true) }
            val result: Result<*> = try {
                block()
            } catch (e: Exception) {
                Result.failure<Any>(e)
            }
            if (label == "Live2D" && result.isSuccess) {
                notifyLive2dReloadIfRunning()
            }
            _uiState.update {
                it.copy(
                    pathImportBusy = false,
                    snackbarMessage = result.fold(
                        onSuccess = {
                            if (label == "Live2D") {
                                val dir = _uiState.value.live2dDirHint
                                "Live2D 已导入到 $dir 并可切换"
                            } else {
                                "$label 已导入并就绪"
                            }
                        },
                        onFailure = { e -> "$label 导入失败：${e.message ?: e}" }
                    )
                )
            }
            refresh()
        }
    }

    fun requestOverlayPermission() {
        val app = getApplication<Application>()
        app.startActivity(OverlayPermissionHelper.createManageOverlayIntent(app))
    }

    fun startPet() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            if (!petSettings.getConfig().enabled) {
                _uiState.update { it.copy(snackbarMessage = "请先打开桌宠总开关") }
                return@launch
            }
            if (!OverlayPermissionHelper.canDrawOverlays(app)) {
                _uiState.update { it.copy(snackbarMessage = OverlayPermissionHelper.DENIED_HINT) }
                return@launch
            }
            FloatingPetService.start(app)
            petSettings.setOverlayRunning(true)
            refresh()
            _uiState.update { it.copy(snackbarMessage = "桌宠已启动") }
        }
    }

    fun stopPet() {
        viewModelScope.launch {
            FloatingPetService.stop(getApplication())
            petSettings.setOverlayRunning(false)
            refresh()
            _uiState.update { it.copy(snackbarMessage = "桌宠已停止") }
        }
    }

    /** stub 一轮听→想→说（无需真 so / 真麦）。 */
    fun runDemoRound() {
        viewModelScope.launch {
            runCatching {
                if (!petSettings.getConfig().enabled) {
                    _uiState.update { it.copy(snackbarMessage = "请先打开桌宠总开关") }
                    return@launch
                }
                _uiState.update { it.copy(isBusy = true) }
                if (!ttsEngine.isReady) {
                    runCatching {
                        ttsSettings.setEnabled(true)
                        ttsEngine.load(ttsSettings.getConfig())
                    }.onFailure { e ->
                        // TTS 加载失败不阻断 stub 演示（协调器会 skip/degrade）
                        _uiState.update {
                            it.copy(snackbarMessage = "TTS 未就绪，将仅文字：${e.message}")
                        }
                    }
                }
                val result = sessionCoordinator.runDemoRound()
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        snackbarMessage = if (result.error != null) {
                            "演示失败：${result.error}"
                        } else {
                            val demoText = MoodTagMapper.stripTags(
                                result.subtitle.ifBlank { result.replyText }
                            )
                            "演示完成：$demoText"
                        }
                    )
                }
                refresh()
            }.getOrElse { e ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        snackbarMessage = "演示异常：${e.message ?: e.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    /** 全屏陪伴页：确保总开关打开（不强制悬浮权限）。 */
    fun ensureEnabledForCompanion() {
        viewModelScope.launch {
            if (!petSettings.getConfig().enabled) {
                petSettings.setEnabled(true)
                refresh()
            }
        }
    }

    /**
     * 全屏陪伴：用户底部输入框文本 → 听→想→说。
     * TTS 未就绪时仍展示字幕，不阻断 Live2D。
     */
    fun runTextRound(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                if (!petSettings.getConfig().enabled) {
                    petSettings.setEnabled(true)
                }
                _uiState.update { it.copy(isBusy = true) }
                val result = sessionCoordinator.runRound(
                    input = com.lanxin.android.builtin.pet.domain.VoiceSessionInput(
                        asrText = trimmed,
                        isStub = true,
                        source = "companion"
                    )
                )
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        snackbarMessage = result.error?.let { e -> "失败：$e" }
                    )
                }
                // 输出链：TTS 有就绪则发音
                if (result.error == null && result.replyText.isNotBlank()) {
                    runCatching {
                        sessionCoordinator.speakReply(result.replyText)
                    }
                }
                refresh()
            }.getOrElse { e ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        snackbarMessage = "发送失败：${e.message ?: e.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    /**
     * 用户 OpenDocumentTree 授权公共 LanXin 目录。
     * 持久化读+写权限、写入 DataStore，并在树下自动创建标准子目录骨架。
     */
    fun grantLanXinSafTree(treeUriString: String) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val ok = withContext(Dispatchers.IO) {
                LanXinSafTree.takePersistable(app, treeUriString)
            }
            if (!ok) {
                _uiState.update {
                    it.copy(snackbarMessage = "无法持久化目录权限，请重试")
                }
                return@launch
            }
            val probe = withContext(Dispatchers.IO) {
                LanXinSafTree.probe(app, treeUriString)
            }
            // 授权成功后立刻在公共树建 live2d/asr/tts/models… 骨架
            val structureCount = if (probe.writable) {
                withContext(Dispatchers.IO) {
                    LanXinSafTree.ensureStructure(app, treeUriString)
                }
            } else {
                0
            }
            // 同步确保引擎 File 根下的骨架（回退路径）
            withContext(Dispatchers.IO) {
                val root = DebugAssetStorage.resolve(app, treeUriString)
                DebugAssetStorage.ensureLanXinStructure(root.lanXinDir)
            }
            petSettings.setLanXinSafTreeUri(treeUriString.trim())
            refresh()
            val msg = when {
                probe.writable && structureCount > 0 ->
                    "已授权公共目录：${probe.displayLabel}，并建好 $structureCount 个子目录（文件管理器可见）"
                probe.writable ->
                    "已授权公共目录：${probe.displayLabel}"
                else ->
                    "已保存授权，但当前不可写：${probe.displayLabel}。请确认选中的是 LanXin 文件夹"
            }
            _uiState.update { it.copy(snackbarMessage = msg) }
        }
    }

    /** 清除 SAF 树授权（不删用户文件）。 */
    fun clearLanXinSafTree() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val uri = petSettings.getConfig().lanXinSafTreeUri
            if (uri.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    LanXinSafTree.releasePersistable(app, uri)
                }
            }
            petSettings.setLanXinSafTreeUri(null)
            refresh()
            _uiState.update { it.copy(snackbarMessage = "已清除公共目录授权") }
        }
    }

    /** 脚本说明（可选路径）；主路径为 App 内下载。 */
    fun showFetchAssetsHint() {
        _uiState.update {
            it.copy(snackbarMessage = DebugOpenSourcePaths.FETCH_SCRIPT_HINT)
        }
    }

    fun setPreferredMirror(mirror: DebugAssetMirror) {
        _uiState.update { it.copy(preferredMirror = mirror) }
    }

    fun startDownload(kind: DebugAssetKind) {
        if (_uiState.value.downloadBusy) {
            _uiState.update { it.copy(snackbarMessage = "已有下载进行中，请先取消或等待完成") }
            return
        }
        val app = getApplication<Application>()
        val mirror = _uiState.value.preferredMirror
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            val cfg = petSettings.getConfig()
            val storageRoot = DebugAssetStorage.resolve(app, cfg.lanXinSafTreeUri)
            assetDownloader.download(storageRoot.baseDir, kind, mirror).collect { event ->
                when (event) {
                    DebugAssetDownloadEvent.Started -> {
                        patchDownloadItem(kind) {
                            it.copy(
                                downloading = true,
                                percent = 0,
                                statusText = "开始下载…",
                                lastError = null
                            )
                        }
                        _uiState.update {
                            it.copy(
                                downloadBusy = true,
                                downloadRootPath = storageRoot.displayPath,
                                downloadRootFallback = storageRoot.usedFallback,
                                safGranted = storageRoot.safGranted,
                                safWritable = storageRoot.safWritable,
                                safDisplayLabel = storageRoot.safDisplayLabel,
                                safTreeUri = storageRoot.safTreeUri
                            )
                        }
                    }
                    is DebugAssetDownloadEvent.Progress -> {
                        val src = event.sourceLabel.ifBlank { event.mirror.name }
                        val label = when (event.phase) {
                            "extracting" -> "解压中…"
                            "live2d-files" -> "拉取文件 ${event.percent}%"
                            else -> {
                                if (event.percent >= 0) "下载中 ${event.percent}%"
                                else "下载中…"
                            }
                        }
                        patchDownloadItem(kind) {
                            it.copy(
                                downloading = true,
                                percent = event.percent,
                                statusText = "$label（$src）"
                            )
                        }
                    }
                    is DebugAssetDownloadEvent.Completed -> {
                        onDownloadCompleted(kind, event.readyPath)
                        // 回退写 App 私有时：授权可写则镜像到公共树；结果可见，禁止静默
                        val mirror = withContext(Dispatchers.IO) {
                            DebugAssetStorage.mirrorReadyPathToSaf(
                                app,
                                storageRoot,
                                event.readyPath
                            )
                        }
                        val src = event.sourceLabel.ifBlank { event.mirror.name }
                        patchDownloadItem(kind) {
                            it.copy(
                                downloading = false,
                                ready = true,
                                readyPath = event.readyPath,
                                percent = 100,
                                statusText = "已就绪 · $src",
                                lastError = null
                            )
                        }
                        val where = event.readyPath
                        val fallbackNote = when {
                            !storageRoot.usedFallback -> ""
                            storageRoot.safWritable && mirror.success && mirror.attempted ->
                                "；${mirror.message}"
                            storageRoot.safWritable && mirror.attempted && !mirror.success ->
                                "；${mirror.message}"
                            storageRoot.safWritable ->
                                "（引擎写 App 目录，已授权 SAF 公共树）"
                            storageRoot.safGranted ->
                                "（SAF 已授权但不可写；引擎写 App 私有）"
                            else ->
                                "（公共目录不可写，已回退；可点「授权公共 LanXin」）"
                        }
                        _uiState.update {
                            it.copy(
                                downloadBusy = false,
                                downloadRootPath = storageRoot.displayPath,
                                downloadRootFallback = storageRoot.usedFallback,
                                safGranted = storageRoot.safGranted,
                                safWritable = storageRoot.safWritable,
                                safDisplayLabel = storageRoot.safDisplayLabel,
                                safTreeUri = storageRoot.safTreeUri,
                                snackbarMessage =
                                "${kind.name} 已保存到 $where（源：$src）$fallbackNote"
                            )
                        }
                        refresh()
                    }
                    is DebugAssetDownloadEvent.Failed -> {
                        // message 已含各源错误时不再重复拼「已尝试」
                        val alreadyDetailed = event.attemptedSources.any { src ->
                            event.message.contains("$src:")
                        }
                        val tried = if (
                            !alreadyDetailed && event.attemptedSources.isNotEmpty()
                        ) {
                            " 已尝试：${event.attemptedSources.joinToString(" → ")}"
                        } else {
                            ""
                        }
                        val full = event.message + tried
                        val status = if (kind == DebugAssetKind.LIVE2D) {
                            "更新失败（内置仍可用）"
                        } else {
                            "失败"
                        }
                        val snack = if (kind == DebugAssetKind.LIVE2D) {
                            "Live2D 在线更新失败（内置 Mao 仍可用）：$full"
                        } else {
                            "${kind.name} 失败：$full"
                        }
                        patchDownloadItem(kind) {
                            it.copy(
                                downloading = false,
                                percent = -1,
                                statusText = status,
                                lastError = full
                            )
                        }
                        _uiState.update {
                            it.copy(
                                downloadBusy = false,
                                snackbarMessage = snack
                            )
                        }
                    }
                    DebugAssetDownloadEvent.Cancelled -> {
                        patchDownloadItem(kind) {
                            it.copy(
                                downloading = false,
                                percent = -1,
                                statusText = "已取消"
                            )
                        }
                        _uiState.update {
                            it.copy(
                                downloadBusy = false,
                                snackbarMessage = "下载已取消"
                            )
                        }
                    }
                }
            }
        }
    }

    fun cancelDownload() {
        assetDownloader.cancel()
        downloadJob?.cancel()
        downloadJob = null
        _uiState.update { state ->
            state.copy(
                downloadBusy = false,
                downloadItems = state.downloadItems.map {
                    if (it.downloading) {
                        it.copy(downloading = false, statusText = "已取消", percent = -1)
                    } else {
                        it
                    }
                },
                snackbarMessage = "下载已取消"
            )
        }
    }

    /**
     * 配置路径空/失效但解析到开源包真实路径时，回写 prefs。
     * 解决：下载落在 App 私有，配置仍空或指到不存在的公共路径 → 开关开了也找不到。
     */
    private suspend fun healModelPathsIfNeeded(
        asrConfigured: String,
        asrResolved: String,
        ttsConfigured: String,
        ttsResolved: String,
        llmConfigured: String,
        llmResolved: String
    ) {
        if (asrResolved.isNotBlank() &&
            asrResolved != asrConfigured.trim() &&
            MeijuDebugPaths.pathExists(asrResolved) &&
            (asrConfigured.isBlank() || !MeijuDebugPaths.pathExists(asrConfigured))
        ) {
            asrSettings.setModelPath(asrResolved)
        }
        if (ttsResolved.isNotBlank() &&
            ttsResolved != ttsConfigured.trim() &&
            MeijuDebugPaths.pathExists(ttsResolved) &&
            (ttsConfigured.isBlank() || !MeijuDebugPaths.pathExists(ttsConfigured))
        ) {
            ttsSettings.setModelDir(ttsResolved)
        }
        if (llmResolved.isNotBlank() &&
            llmResolved != llmConfigured.trim() &&
            MeijuDebugPaths.pathExists(llmResolved) &&
            (llmConfigured.isBlank() || !MeijuDebugPaths.pathExists(llmConfigured))
        ) {
            localInferenceSettings.setModelPath(llmResolved)
        }
    }

    private suspend fun onDownloadCompleted(kind: DebugAssetKind, readyPath: String) {
        when (kind) {
            DebugAssetKind.LIVE2D -> {
                petSettings.setLive2dModelPath(readyPath)
                notifyLive2dReloadIfRunning()
            }
            DebugAssetKind.ASR -> asrSettings.setModelPath(readyPath)
            DebugAssetKind.TTS -> {
                ttsSettings.setModelDir(readyPath)
                if (ttsSettings.getConfig().enabled && !ttsEngine.isReady) {
                    runCatching { ttsEngine.load(ttsSettings.getConfig()) }
                }
            }
            DebugAssetKind.LOCAL_LLM -> localInferenceSettings.setModelPath(readyPath)
        }
    }

    private fun patchDownloadItem(
        kind: DebugAssetKind,
        transform: (DebugAssetItemUi) -> DebugAssetItemUi
    ) {
        _uiState.update { state ->
            state.copy(
                downloadItems = state.downloadItems.map {
                    if (it.kind == kind) transform(it) else it
                }
            )
        }
    }

    private fun buildDownloadItems(baseDir: java.io.File): List<DebugAssetItemUi> {
        val prev = _uiState.value.downloadItems.associateBy { it.kind }
        return DebugAssetKind.entries.map { kind ->
            val spec = DebugAssetCatalog.spec(kind)
            val ready = assetDownloader.isReady(baseDir, kind)
            val path = assetDownloader.readyPath(baseDir, kind)
            val old = prev[kind]
            DebugAssetItemUi(
                kind = kind,
                displayName = spec.displayName,
                sizeHint = spec.sizeHint,
                licenseHint = spec.licenseHint,
                ready = ready,
                readyPath = path,
                downloading = old?.downloading == true,
                percent = old?.percent ?: -1,
                statusText = when {
                    old?.downloading == true -> old.statusText
                    ready -> "已就绪"
                    old?.lastError != null -> "失败"
                    else -> "未下载"
                },
                // refresh 不得抹掉真实失败原因（仅就绪或下载中按状态覆盖）
                lastError = when {
                    old?.downloading == true -> old.lastError
                    ready -> null
                    else -> old?.lastError
                }
            )
        }
    }

    private fun defaultDownloadItems(): List<DebugAssetItemUi> =
        DebugAssetKind.entries.map { kind ->
            val spec = DebugAssetCatalog.spec(kind)
            DebugAssetItemUi(
                kind = kind,
                displayName = spec.displayName,
                sizeHint = spec.sizeHint,
                licenseHint = spec.licenseHint
            )
        }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun formatPreview(
        phase: String,
        asr: String,
        reply: String,
        err: String?
    ): String = buildString {
        append("phase=").append(phase)
        if (asr.isNotBlank()) append(" · asr=").append(asr.take(32))
        if (reply.isNotBlank()) append(" · reply=").append(reply.take(32))
        if (!err.isNullOrBlank()) append(" · err=").append(err)
    }
}
