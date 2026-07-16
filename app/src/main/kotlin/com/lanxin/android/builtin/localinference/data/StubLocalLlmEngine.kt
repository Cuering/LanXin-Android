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

package com.lanxin.android.builtin.localinference.data

import com.lanxin.android.builtin.localinference.domain.LocalEngineState
import com.lanxin.android.builtin.localinference.domain.LocalGenerateRequest
import com.lanxin.android.builtin.localinference.domain.LocalGenerateResult
import com.lanxin.android.builtin.localinference.domain.LocalInferenceConfig
import com.lanxin.android.builtin.localinference.domain.LocalLlmEngine
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
 * Phase 6.1 stub 本地引擎。
 *
 * - 不链接 MNN so；验证 load/generate 契约与 Chat 对接
 * - 预留 [MnnNativeBridge] 接入点：真正实现时替换本类绑定即可
 * - 大模型文件不进 git，仅校验路径存在
 */
@Singleton
class StubLocalLlmEngine @Inject constructor(
    private val nativeBridge: MnnNativeBridge
) : LocalLlmEngine {

    private val mutex = Mutex()
    private val _state = MutableStateFlow(LocalEngineState.DISABLED)
    private var config: LocalInferenceConfig = LocalInferenceConfig()
    private var error: String? = null
    private var loadedPath: String? = null

    override val state: StateFlow<LocalEngineState> = _state.asStateFlow()

    override val isReady: Boolean
        get() = _state.value == LocalEngineState.READY

    override val isAvailable: Boolean
        get() = config.enabled && config.modelPath.isNotBlank()

    override val lastError: String?
        get() = error

    override suspend fun load(config: LocalInferenceConfig): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            this@StubLocalLlmEngine.config = config
            if (!config.enabled) {
                loadedPath = null
                error = null
                _state.value = LocalEngineState.DISABLED
                return@withLock false
            }
            if (config.modelPath.isBlank()) {
                error = "model_path_empty"
                loadedPath = null
                _state.value = LocalEngineState.ERROR
                return@withLock false
            }
            _state.value = LocalEngineState.LOADING
            // 预留：nativeBridge.loadModel(config.modelPath)
            val pathOk = nativeBridge.validateModelPath(config.modelPath)
            if (!pathOk) {
                error = "model_path_missing:${config.modelPath}"
                loadedPath = null
                _state.value = LocalEngineState.ERROR
                return@withLock false
            }
            // stub 短暂延迟模拟加载
            delay(10)
            loadedPath = config.modelPath
            error = null
            _state.value = LocalEngineState.READY
            true
        }
    }

    override suspend fun unload() = withContext(Dispatchers.IO) {
        mutex.withLock {
            nativeBridge.unload()
            loadedPath = null
            error = null
            _state.value = if (config.enabled) {
                LocalEngineState.IDLE
            } else {
                LocalEngineState.DISABLED
            }
        }
    }

    override suspend fun generate(request: LocalGenerateRequest): LocalGenerateResult {
        if (!isReady) {
            error("LocalLlmEngine not ready: state=${_state.value}, error=$error")
        }
        val maxTokens = (request.maxTokens ?: config.maxTokens)
            .coerceIn(LocalInferenceConfig.MIN_MAX_TOKENS, LocalInferenceConfig.MAX_MAX_TOKENS)
        val prompt = request.prompt.trim()
        // stub 回复：标注本地 stub，截断 prompt 预览
        val preview = prompt.take(80).replace('\n', ' ')
        val text = buildString {
            append("[local-stub] ")
            request.systemPrompt?.takeIf { it.isNotBlank() }?.let {
                append("(sys=${it.take(24)}) ")
            }
            append("echo: ")
            append(preview)
            if (prompt.length > 80) append("…")
            append(" | maxTokens=")
            append(maxTokens)
            append(" | model=")
            append(loadedPath?.let { File(it).name } ?: "?")
        }
        return LocalGenerateResult(text = text, finishReason = "stop", isStub = true)
    }

    override fun stream(request: LocalGenerateRequest): Flow<String> = flow {
        // 6.1：整段 generate 后一次 emit；真实 MNN 按 token 切分
        val result = generate(request)
        emit(result.text)
    }
}
