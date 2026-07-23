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

import com.lanxin.android.builtin.voice.domain.TtsConfig
import com.lanxin.android.builtin.voice.domain.TtsEngine
import com.lanxin.android.builtin.voice.domain.TtsEngineState
import com.lanxin.android.builtin.voice.domain.TtsSynthesizeRequest
import com.lanxin.android.builtin.voice.domain.TtsSynthesizeResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Phase M1 stub TTS。
 *
 * - 不链接 Bert-VITS2 so；验证 synthesize 契约与 VoiceSession 对接
 * - 返回空 PCM + 字幕；按字数估算 duration，便于 UI 气泡演示
 */
@Singleton
class StubTtsEngine @Inject constructor() : TtsEngine {

    private val mutex = Mutex()
    private val _state = MutableStateFlow(TtsEngineState.DISABLED)
    private var config: TtsConfig = TtsConfig()
    private var error: String? = null

    override val state: StateFlow<TtsEngineState> = _state.asStateFlow()

    override val isReady: Boolean
        get() = _state.value == TtsEngineState.READY || _state.value == TtsEngineState.SPEAKING

    override val isAvailable: Boolean
        get() = config.enabled

    override val lastError: String?
        get() = error

    override suspend fun load(config: TtsConfig): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            this@StubTtsEngine.config = config
            if (!config.enabled) {
                error = null
                _state.value = TtsEngineState.DISABLED
                return@withLock false
            }
            _state.value = TtsEngineState.LOADING
            delay(5)
            // stub：不校验真实模型文件；路径仅作元数据
            error = null
            _state.value = TtsEngineState.READY
            true
        }
    }

    override suspend fun unload() = withContext(Dispatchers.IO) {
        mutex.withLock {
            error = null
            _state.value = if (config.enabled) TtsEngineState.IDLE else TtsEngineState.DISABLED
        }
    }

    override suspend fun synthesize(request: TtsSynthesizeRequest): TtsSynthesizeResult {
        val text = request.text.trim()
        val sampleRate = request.sampleRateHz ?: config.sampleRateHz
        // 未就绪：不抛异常，返回 stub 字幕
        if (!isReady && _state.value != TtsEngineState.SPEAKING &&
            _state.value != TtsEngineState.READY
        ) {
            error = "not_ready:state=${_state.value}"
            val durationMs = (text.length * 80L).coerceAtLeast(400L).coerceAtMost(8_000L)
            return TtsSynthesizeResult(
                pcm16leMono = ByteArray(0),
                sampleRateHz = sampleRate,
                durationMs = durationMs,
                isStub = true,
                subtitle = text
            )
        }
        // ~80ms / CJK char, min 400ms
        val durationMs = (text.length * 80L).coerceAtLeast(400L).coerceAtMost(8_000L)
        _state.value = TtsEngineState.SPEAKING
        delay(minOf(durationMs, 50L)) // stub 不真播，仅短延时
        _state.value = TtsEngineState.READY
        return TtsSynthesizeResult(
            pcm16leMono = ByteArray(0),
            sampleRateHz = sampleRate,
            durationMs = durationMs,
            isStub = true,
            subtitle = text
        )
    }
}
