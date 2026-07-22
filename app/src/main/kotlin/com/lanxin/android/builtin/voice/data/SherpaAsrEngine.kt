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

import com.lanxin.android.builtin.voice.domain.AsrConfig
import com.lanxin.android.builtin.voice.domain.AsrEngine
import com.lanxin.android.builtin.voice.domain.AsrEngineState
import com.lanxin.android.builtin.voice.domain.AsrTranscribeRequest
import com.lanxin.android.builtin.voice.domain.AsrTranscribeResult
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 真机 Sherpa-ONNX ASR 引擎。
 *
 * - native 可用时：load → [SherpaOnnxBridge.loadModel] / transcribe
 * - native 不可用或 load 失败：路径合法则 **降级 stub 文本**（与 [StubAsrEngine] 一致），
 *   保证设置页与无 so 环境不崩；[AsrTranscribeResult.isStub] 标明降级
 */
@Singleton
class SherpaAsrEngine @Inject constructor(
    private val nativeBridge: SherpaOnnxBridge
) : AsrEngine {

    private val mutex = Mutex()
    private val _state = MutableStateFlow(AsrEngineState.DISABLED)
    private var config: AsrConfig = AsrConfig()
    private var error: String? = null
    private var loadedPath: String? = null

    /** true = 当前 READY 会话走的是 native；false = stub 降级 */
    @Volatile
    private var usingNative: Boolean = false

    override val state: StateFlow<AsrEngineState> = _state.asStateFlow()

    override val isReady: Boolean
        get() = _state.value == AsrEngineState.READY

    override val isAvailable: Boolean
        get() = config.enabled && config.modelPath.isNotBlank()

    override val lastError: String?
        get() = error

    /** 调试：是否正在使用 native 识别。 */
    val isUsingNative: Boolean
        get() = usingNative

    override suspend fun load(config: AsrConfig): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            this@SherpaAsrEngine.config = config
            usingNative = false
            if (!config.enabled) {
                nativeBridge.unload()
                loadedPath = null
                error = null
                _state.value = AsrEngineState.DISABLED
                return@withLock false
            }
            if (config.modelPath.isBlank()) {
                error = "model_path_empty"
                loadedPath = null
                _state.value = AsrEngineState.ERROR
                return@withLock false
            }
            _state.value = AsrEngineState.LOADING
            val pathOk = nativeBridge.validateModelPath(config.modelPath)
            if (!pathOk) {
                error = "model_path_missing:${config.modelPath}"
                loadedPath = null
                _state.value = AsrEngineState.ERROR
                return@withLock false
            }

            // stub:// 永远走 stub READY（单测 / 无真模型）
            if (config.modelPath.startsWith(SherpaOnnxBridge.STUB_SCHEME)) {
                loadedPath = config.modelPath
                usingNative = false
                error = null
                _state.value = AsrEngineState.READY
                return@withLock true
            }

            val nativeOk = nativeBridge.isNativeAvailable() &&
                nativeBridge.loadModel(config.modelPath, config.language)
            if (nativeOk) {
                loadedPath = config.modelPath
                usingNative = true
                error = null
                _state.value = AsrEngineState.READY
                return@withLock true
            }

            // 降级：路径合法但 native 失败 → stub READY，记录原因
            val reason = nativeBridge.lastError()
                ?: nativeBridge.nativeLoadError()
                ?: "native_load_failed"
            loadedPath = config.modelPath
            usingNative = false
            error = "native_degraded:$reason"
            _state.value = AsrEngineState.READY
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
                AsrEngineState.IDLE
            } else {
                AsrEngineState.DISABLED
            }
        }
    }

    override suspend fun transcribe(request: AsrTranscribeRequest): AsrTranscribeResult {
        if (!isReady) {
            error("AsrEngine not ready: state=${_state.value}, error=$error")
        }
        val bytes = request.pcm16leMono
        val sampleRate = request.sampleRateHz ?: config.sampleRateHz
        val lang = request.language ?: config.language
        val modelName = loadedPath?.let { File(it).name } ?: "?"

        if (usingNative) {
            val text = withContext(Dispatchers.IO) {
                nativeBridge.transcribe(bytes, sampleRate)
            }
            if (text != null) {
                return AsrTranscribeResult(
                    text = text,
                    isPartial = false,
                    isStub = false,
                    confidence = null
                )
            }
            // native 转写失败 → 仍返回可诊断 stub 行，避免崩溃
        }

        val durationMs = if (sampleRate > 0) {
            (bytes.size / 2.0 / sampleRate * 1000.0).toLong()
        } else {
            0L
        }
        val text = buildString {
            append("[asr-stub] ")
            append("lang=")
            append(lang)
            append(" · ")
            append(bytes.size)
            append("B · ~")
            append(durationMs)
            append("ms · model=")
            append(modelName)
            if (usingNative) {
                append(" · native_transcribe_null")
            } else if (error != null) {
                append(" · ")
                append(error)
            }
        }
        return AsrTranscribeResult(
            text = text,
            isPartial = false,
            isStub = true,
            confidence = 1.0f
        )
    }

    override fun streamPartial(request: AsrTranscribeRequest): Flow<AsrTranscribeResult> = flow {
        // Online transducer：若已在流式会话中，读 currentPartial；否则整段 transcribe
        if (usingNative && nativeBridge.currentMode() == SherpaOnnxBridge.Mode.ONLINE_TRANSDUCER) {
            // 一次性喂整段 PCM 做流式模拟：start → feed → finish，中间 emit partial
            val sampleRate = request.sampleRateHz ?: config.sampleRateHz
            val started = withContext(Dispatchers.IO) {
                nativeBridge.startStreaming(sampleRate)
            }
            if (started) {
                val pcm = request.pcm16leMono
                val chunkSize = (sampleRate / 10) * 2 // ~100ms
                var offset = 0
                var last = ""
                while (offset < pcm.size) {
                    val end = (offset + chunkSize).coerceAtMost(pcm.size)
                    val chunk = pcm.copyOfRange(offset, end)
                    val partial = withContext(Dispatchers.IO) {
                        nativeBridge.feedPcmChunk(chunk)
                    }.orEmpty()
                    if (partial.isNotEmpty() && partial != last) {
                        last = partial
                        emit(
                            AsrTranscribeResult(
                                text = partial,
                                isPartial = true,
                                isStub = false,
                                confidence = null
                            )
                        )
                    }
                    offset = end
                }
                val finalText = withContext(Dispatchers.IO) {
                    nativeBridge.finishStreaming()
                }.orEmpty()
                emit(
                    AsrTranscribeResult(
                        text = finalText.ifBlank { last },
                        isPartial = false,
                        isStub = false,
                        confidence = null
                    )
                )
                return@flow
            }
        }
        // 回退：整段 transcribe 后一次 partial + final
        val result = transcribe(request)
        emit(result.copy(isPartial = true))
        emit(result.copy(isPartial = false))
    }
}
