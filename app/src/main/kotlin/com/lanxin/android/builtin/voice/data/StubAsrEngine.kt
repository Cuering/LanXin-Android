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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Phase 6.4 stub ASR 引擎。
 *
 * - 不链接 sherpa-onnx so；验证 load/transcribe 契约与设置页对接
 * - 预留 [SherpaOnnxBridge] 接入点：真正实现时替换本类绑定即可
 * - 大模型文件不进 git，仅校验路径存在
 */
@Singleton
class StubAsrEngine @Inject constructor(
    private val nativeBridge: SherpaOnnxBridge
) : AsrEngine {

    private val mutex = Mutex()
    private val _state = MutableStateFlow(AsrEngineState.DISABLED)
    private var config: AsrConfig = AsrConfig()
    private var error: String? = null
    private var loadedPath: String? = null

    override val state: StateFlow<AsrEngineState> = _state.asStateFlow()

    override val isReady: Boolean
        get() = _state.value == AsrEngineState.READY

    override val isAvailable: Boolean
        get() = config.enabled && config.modelPath.isNotBlank()

    override val lastError: String?
        get() = error

    override suspend fun load(config: AsrConfig): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            this@StubAsrEngine.config = config
            if (!config.enabled) {
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
            // 预留：nativeBridge.loadModel(config.modelPath, config.language)
            val pathOk = nativeBridge.validateModelPath(config.modelPath)
            if (!pathOk) {
                error = "model_path_missing:${config.modelPath}"
                loadedPath = null
                _state.value = AsrEngineState.ERROR
                return@withLock false
            }
            delay(10)
            loadedPath = config.modelPath
            error = null
            _state.value = AsrEngineState.READY
            true
        }
    }

    override suspend fun unload() = withContext(Dispatchers.IO) {
        mutex.withLock {
            nativeBridge.unload()
            loadedPath = null
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
        // stub：根据 PCM 长度生成可预测文本，便于单测与试转写演示
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
        }
        return AsrTranscribeResult(
            text = text,
            isPartial = false,
            isStub = true,
            confidence = 1.0f
        )
    }

    override fun streamPartial(request: AsrTranscribeRequest): Flow<AsrTranscribeResult> = flow {
        // 6.4：整段 transcribe 后一次 emit；真实 sherpa 可按 partial 切分
        val result = transcribe(request)
        emit(result.copy(isPartial = true))
        emit(result.copy(isPartial = false))
    }
}
