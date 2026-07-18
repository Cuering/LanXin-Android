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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 真机 MNN 本地 LLM 引擎。
 *
 * - native 可用时：load → [MnnNativeBridge.loadModel] / generate
 * - native 不可用或 load 失败：路径合法则 **降级 stub 文本**（与 [StubLocalLlmEngine] 一致），
 *   保证设置页与无 so 环境不崩；[LocalGenerateResult.isStub] 标明降级
 * - 模型外置：`LanXin/models/local-llm/light/`（不进 git）
 */
@Singleton
class MnnLocalLlmEngine @Inject constructor(
    private val nativeBridge: MnnNativeBridge
) : LocalLlmEngine {

    private val mutex = Mutex()
    private val _state = MutableStateFlow(LocalEngineState.DISABLED)
    private var config: LocalInferenceConfig = LocalInferenceConfig()
    private var error: String? = null
    private var loadedPath: String? = null

    /** true = 当前 READY 会话走 native；false = stub 降级 */
    @Volatile
    private var usingNative: Boolean = false

    override val state: StateFlow<LocalEngineState> = _state.asStateFlow()

    override val isReady: Boolean
        get() = _state.value == LocalEngineState.READY

    override val isAvailable: Boolean
        get() = config.enabled && config.modelPath.isNotBlank()

    override val lastError: String?
        get() = error

    /** 调试：是否正在使用 native 推理。 */
    val isUsingNative: Boolean
        get() = usingNative

    override suspend fun load(config: LocalInferenceConfig): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            this@MnnLocalLlmEngine.config = config
            usingNative = false
            if (!config.enabled) {
                nativeBridge.unload()
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
            val pathOk = nativeBridge.validateModelPath(config.modelPath)
            if (!pathOk) {
                error = "model_path_missing:${config.modelPath}"
                loadedPath = null
                _state.value = LocalEngineState.ERROR
                return@withLock false
            }

            // stub:// 永远走 stub READY（单测 / 无真模型）
            if (config.modelPath.startsWith(MnnNativeBridge.STUB_SCHEME)) {
                loadedPath = config.modelPath
                usingNative = false
                error = null
                _state.value = LocalEngineState.READY
                return@withLock true
            }

            val nativeOk = nativeBridge.isNativeAvailable() &&
                nativeBridge.loadModel(config.modelPath)
            if (nativeOk) {
                loadedPath = config.modelPath
                usingNative = true
                error = null
                _state.value = LocalEngineState.READY
                return@withLock true
            }

            // 降级：路径合法但 native 失败 → stub READY，记录原因
            val reason = nativeBridge.lastError()
                ?: nativeBridge.nativeLoadError()
                ?: "native_load_failed"
            loadedPath = config.modelPath
            usingNative = false
            error = "native_degraded:$reason"
            _state.value = LocalEngineState.READY
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
        val prompt = buildPrompt(request)
        val modelName = loadedPath?.let { File(it).name } ?: "?"

        if (usingNative) {
            val text = withContext(Dispatchers.IO) {
                nativeBridge.generate(prompt, maxTokens)
            }
            if (text != null) {
                return LocalGenerateResult(
                    text = text,
                    finishReason = "stop",
                    isStub = false
                )
            }
            // native 生成失败 → 仍返回可诊断 stub 行，避免崩溃
        }

        val preview = request.prompt.trim().take(80).replace('\n', ' ')
        val text = buildString {
            append("[local-stub] ")
            request.systemPrompt?.takeIf { it.isNotBlank() }?.let {
                append("(sys=${it.take(24)}) ")
            }
            append("echo: ")
            append(preview)
            if (request.prompt.trim().length > 80) append("…")
            append(" | maxTokens=")
            append(maxTokens)
            append(" | model=")
            append(modelName)
            if (usingNative) {
                append(" · native_generate_null")
            } else if (error != null) {
                append(" · ")
                append(error)
            }
        }
        return LocalGenerateResult(text = text, finishReason = "stop", isStub = true)
    }

    override fun stream(request: LocalGenerateRequest): Flow<String> = flow {
        // 整段 generate 后一次 emit；真实 token 流式后续可扩 native 回调
        val result = generate(request)
        emit(result.text)
    }

    private fun buildPrompt(request: LocalGenerateRequest): String {
        val user = request.prompt.trim()
        val sys = request.systemPrompt?.trim().orEmpty()
        return if (sys.isNotEmpty()) {
            // 简单拼接；模型侧 chat template 由 MNN config 负责
            "System: $sys\n\nUser: $user"
        } else {
            user
        }
    }
}
