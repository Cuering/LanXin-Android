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
import com.lanxin.android.util.PathImportHelper
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
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

            // stub:// 永远走 stub READY（单测 / 无真模型）
            if (config.modelPath.startsWith(MnnNativeBridge.STUB_SCHEME)) {
                loadedPath = config.modelPath
                usingNative = false
                error = null
                _state.value = LocalEngineState.READY
                return@withLock true
            }

            // 裸 .mnn / 缺 config 时提前失败，避免「READY + stub 回声」误导用户
            val packageIssue = PathImportHelper.localLlmPackageIssue(config.modelPath)
            if (packageIssue != null) {
                // 与历史契约对齐：路径不存在统一 model_path_missing:
                error = when {
                    packageIssue.startsWith("path_missing:") ->
                        "model_path_missing:" + packageIssue.removePrefix("path_missing:")
                    else -> packageIssue
                }
                loadedPath = null
                usingNative = false
                _state.value = LocalEngineState.ERROR
                return@withLock false
            }

            val loadPath = PathImportHelper.resolveLocalLlmLoadPath(config.modelPath)
            val pathOk = nativeBridge.validateModelPath(loadPath)
            if (!pathOk) {
                error = "model_path_missing:$loadPath"
                loadedPath = null
                _state.value = LocalEngineState.ERROR
                return@withLock false
            }

            val nativeOk = nativeBridge.isNativeAvailable() &&
                nativeBridge.loadModel(loadPath)
            if (nativeOk) {
                loadedPath = loadPath
                usingNative = true
                error = null
                _state.value = LocalEngineState.READY
                return@withLock true
            }

            // 降级：路径合法但 native 失败 → stub READY，记录原因
            val reason = nativeBridge.lastError()
                ?: nativeBridge.nativeLoadError()
                ?: "native_load_failed"
            loadedPath = loadPath
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
        val modelName = loadedPath?.let { File(it).name } ?: "?"

        if (usingNative) {
            val (roles, contents) = buildChatMessages(request)
            val text = withContext(Dispatchers.IO) {
                if (roles.size > 1) {
                    nativeBridge.generateChat(roles, contents, maxTokens)
                } else {
                    nativeBridge.generate(request.prompt.trim(), maxTokens)
                }
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
        if (!isReady) {
            error("LocalLlmEngine not ready: state=${_state.value}, error=$error")
        }
        val maxTokens = (request.maxTokens ?: config.maxTokens)
            .coerceIn(LocalInferenceConfig.MIN_MAX_TOKENS, LocalInferenceConfig.MAX_MAX_TOKENS)

        if (usingNative) {
            val (roles, contents) = buildChatMessages(request)
            if (roles.size > 1) {
                // generateChatStream 的回调是非 suspend lambda，用 Channel 桥接 flow emit
                val channel = Channel<String>(Channel.UNLIMITED)
                withContext(Dispatchers.IO) {
                    nativeBridge.generateChatStream(roles, contents, maxTokens) { piece ->
                        channel.trySend(piece)
                        false
                    }
                    channel.close()
                }
                for (piece in channel) {
                    emit(piece)
                }
            } else {
                val text = withContext(Dispatchers.IO) {
                    nativeBridge.generate(request.prompt.trim(), maxTokens)
                }
                if (text != null) emit(text)
            }
        } else {
            val result = generate(request)
            emit(result.text)
        }
    }

    /**
     * 构建 ChatMessages 数组（对齐 MNN Chat 的多轮格式）。
     *
     * - 有 systemPrompt → ["system","user",…] 或 ["system","user","assistant","user",…]
     * - 无 systemPrompt + 单轮 → roles.size==1，调用方走 generate fast path
     */
    private fun buildChatMessages(
        request: LocalGenerateRequest
    ): Pair<Array<String>, Array<String>> {
        val roles = mutableListOf<String>()
        val contents = mutableListOf<String>()

        request.systemPrompt?.takeIf { it.isNotBlank() }?.let {
            roles.add("system")
            contents.add(it.trim())
        }

        request.history.forEach { msg ->
            roles.add(msg.role)
            contents.add(msg.content)
        }

        roles.add("user")
        contents.add(request.prompt.trim())

        return roles.toTypedArray() to contents.toTypedArray()
    }
}
