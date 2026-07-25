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

package com.lanxin.android.builtin.voice.data

import android.util.Log
import com.lanxin.android.builtin.voice.domain.TtsConfig
import com.lanxin.android.builtin.voice.domain.TtsEngine
import com.lanxin.android.builtin.voice.domain.TtsEngineState
import com.lanxin.android.builtin.voice.domain.TtsSynthesizeRequest
import com.lanxin.android.builtin.voice.domain.TtsSynthesizeResult
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 真机 Sherpa-ONNX Offline TTS 引擎。
 *
 * - native 可用：load → [SherpaTtsBridge.loadModel] / synthesize
 * - native 不可用或 load 失败：路径合法则 **降级 stub**（空 PCM + 字幕），
 *   保证桌宠会话 / 无 so 环境不崩；[TtsSynthesizeResult.isStub] 标明降级
 * - 模型目录外置 `LanXin/tts/...`；Matcha 需同目录或上一级 vocoder
 */
@Singleton
class SherpaTtsEngine @Inject constructor(
    private val nativeBridge: SherpaTtsBridge
) : TtsEngine {

    companion object {
        private const val TAG = "SherpaTtsEngine"
    }

    private val mutex = Mutex()
    private val _state = MutableStateFlow(TtsEngineState.DISABLED)
    private var config: TtsConfig = TtsConfig()
    private var error: String? = null
    private var loadedPath: String? = null

    /** true = 当前 READY 会话走 native；false = stub 降级 */
    @Volatile
    private var usingNative: Boolean = false

    override val state: StateFlow<TtsEngineState> = _state.asStateFlow()

    override val isReady: Boolean
        get() = _state.value == TtsEngineState.READY || _state.value == TtsEngineState.SPEAKING

    override val isAvailable: Boolean
        get() = config.enabled

    override val lastError: String?
        get() = error

    val isUsingNative: Boolean
        get() = usingNative

    override suspend fun load(config: TtsConfig): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            this@SherpaTtsEngine.config = config
            usingNative = false
            if (!config.enabled) {
                nativeBridge.unload()
                loadedPath = null
                error = null
                _state.value = TtsEngineState.DISABLED
                return@withLock false
            }
            _state.value = TtsEngineState.LOADING
            val modelDir = resolveModelDir(config)
            // 无路径：仍允许 stub READY（与 StubTtsEngine 兼容；桌宠可演示字幕）
            if (modelDir.isBlank()) {
                Log.w(TAG, "load: modelDir is blank, config=${config.toDebugString()} — degraded to stub")
                loadedPath = null
                usingNative = false
                error = null
                _state.value = TtsEngineState.READY
                return@withLock true
            }
            val pathOk = nativeBridge.validateModelPath(modelDir)
            if (!pathOk) {
                // 无效/缺失目录：降级 stub READY，让上层可走 Android 系统 TTS，不硬失败
                Log.w(TAG, "load: validateModelPath failed for dir=$modelDir — degraded to stub")
                loadedPath = null
                usingNative = false
                error = "model_dir_missing:$modelDir"
                _state.value = TtsEngineState.READY
                return@withLock true
            }

            if (modelDir.startsWith(SherpaOnnxBridge.STUB_SCHEME)) {
                Log.w(TAG, "load: stub scheme path=$modelDir, not a real model")
                loadedPath = modelDir
                usingNative = false
                error = null
                _state.value = TtsEngineState.READY
                return@withLock true
            }

            val nativeAvailable = nativeBridge.isNativeAvailable()
            if (!nativeAvailable) {
                val why = nativeBridge.nativeLoadError() ?: "native_unavailable"
                Log.w(TAG, "load: native unavailable, reason=$why, modelDir=$modelDir — degraded to stub")
                loadedPath = modelDir
                usingNative = false
                error = "native_degraded:$why"
                _state.value = TtsEngineState.READY
                return@withLock true
            }

            val loaded = nativeBridge.loadModel(modelDir)
            if (loaded) {
                Log.i(
                    TAG,
                    "load: OK, mode=${nativeBridge.currentMode()} rate=${nativeBridge.sampleRate()} " +
                        "dir=$modelDir voiceId=${config.voiceId} sid=${parseSpeakerId(config.voiceId)} " +
                        "ref=${config.referenceAudio.takeLast(48)}"
                )
                loadedPath = modelDir
                usingNative = true
                error = null
                _state.value = TtsEngineState.READY
                return@withLock true
            }

            val reason = nativeBridge.lastError() ?: "loadModel_returned_false"
            Log.w(TAG, "load: native loadModel failed, reason=$reason, modelDir=$modelDir — degraded to stub")
            loadedPath = modelDir
            usingNative = false
            error = "native_degraded:$reason"
            _state.value = TtsEngineState.READY
            true
        }
    }

    override suspend fun unload() = withContext(Dispatchers.IO) {
        mutex.withLock {
            nativeBridge.unload()
            loadedPath = null
            usingNative = false
            error = null
            _state.value = if (config.enabled) {
                TtsEngineState.IDLE
            } else {
                TtsEngineState.DISABLED
            }
        }
    }

    override suspend fun synthesize(request: TtsSynthesizeRequest): TtsSynthesizeResult {
        val text = request.text.trim()
        val sampleRate = request.sampleRateHz
            ?: config.sampleRateHz.let { if (it > 0) it else TtsConfig.DEFAULT_SAMPLE_RATE_HZ }
        val sid = parseSpeakerId(request.voiceId ?: config.voiceId)

        // 未就绪：绝不抛错闪退，直接 stub 字幕结果
        if (!isReady && _state.value != TtsEngineState.SPEAKING) {
            val durationMs = (text.length * 80L).coerceAtLeast(400L).coerceAtMost(8_000L)
            val errMsg = "not_ready:state=${_state.value}"
            Log.w(TAG, "synthesize: $errMsg text=${text.take(48)} — stub result")
            error = errMsg
            return TtsSynthesizeResult(
                pcm16leMono = ByteArray(0),
                sampleRateHz = sampleRate,
                durationMs = durationMs,
                isStub = true,
                subtitle = text
            )
        }

        if (usingNative) {
            _state.value = TtsEngineState.SPEAKING
            Log.i(
                TAG,
                "synthesize: native mode=${nativeBridge.currentMode()} sid=$sid " +
                    "voiceId=${request.voiceId ?: config.voiceId} " +
                    "ref=${config.referenceAudio.takeLast(32)} text=${text.take(32)}"
            )
            val audio = withContext(Dispatchers.IO) {
                runCatching {
                    nativeBridge.synthesize(text = text, speakerId = sid, speed = 1.0f)
                }.getOrNull()
            }
            _state.value = TtsEngineState.READY
            if (audio != null) {
                val rate = if (audio.sampleRateHz > 0) audio.sampleRateHz else sampleRate
                val pcm = SherpaTtsBridge.floatToPcm16le(audio.samples)
                val durationMs = SherpaTtsBridge.durationMs(audio.samples.size, rate)
                return TtsSynthesizeResult(
                    pcm16leMono = pcm,
                    sampleRateHz = rate,
                    durationMs = durationMs.coerceAtLeast(if (pcm.isEmpty()) 0L else 1L),
                    isStub = false,
                    subtitle = text
                )
            }
            // native 失败 → 降级 stub 结果
        }

        val durationMs = (text.length * 80L).coerceAtLeast(400L).coerceAtMost(8_000L)
        _state.value = TtsEngineState.SPEAKING
        _state.value = TtsEngineState.READY
        return TtsSynthesizeResult(
            pcm16leMono = ByteArray(0),
            sampleRateHz = sampleRate,
            durationMs = durationMs,
            isStub = true,
            subtitle = text
        )
    }

    private fun resolveModelDir(config: TtsConfig): String {
        val dir = config.modelDir.trim()
        if (dir.isNotEmpty()) return dir
        return config.modelPath.trim()
    }

    /**
     * 解析说话人 id：
     * - `"0"` / `"12"` → 整数
     * - `"sid:3"` / `"speaker-2"` → 取数字
     * - `"lanxin"` 等无数字 → 0（单说话人 VITS/Melo 默认）
     * multi-speaker（如 fanchen-C）请在设置里填数字 voiceId。
     */
    private fun parseSpeakerId(voiceId: String?): Int {
        if (voiceId.isNullOrBlank()) return 0
        val trimmed = voiceId.trim()
        trimmed.toIntOrNull()?.let { return it.coerceAtLeast(0) }
        // sid:1 / speaker=2 / voice_3
        Regex("""(?i)(?:sid|speaker|spk|voice)[=:_-]?(\d+)""").find(trimmed)?.groupValues?.getOrNull(1)
            ?.toIntOrNull()?.let { return it.coerceAtLeast(0) }
        val digits = trimmed.filter { it.isDigit() }
        return digits.toIntOrNull()?.coerceAtLeast(0) ?: 0
    }
}
