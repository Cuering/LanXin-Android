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

import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MNN LLM JNI 接入点。
 *
 * 对齐 MNN Chat：
 * - 会话常驻（load 一次，多轮 generate）
 * - ChatMessages 多轮 [generateChat]
 * - 可选 token 流 [generateChatStream]
 * - native 侧 set_config(thread_num=4) + tuning
 *
 * - 运行时 so：官方 Android 预编译包（libMNN*.so + libllm.so）+ 自研 `libmnn_lanxin.so`
 * - 模型权重外置：`LanXin/models/local-llm/light/`（不进 git）
 * - JVM 单测：无 so 时 [isNativeAvailable] 为 false；[loadModel]/[generate] 安全降级
 */
@Singleton
class MnnNativeBridge @Inject constructor() {

    @Volatile
    private var lastLoadError: String? = null

    @Volatile
    private var sessionLoaded: Boolean = false

    fun isNativeAvailable(): Boolean = tryLoadNative()

    fun nativeLoadError(): String? = companionNativeLoadError()

    fun lastError(): String? = lastLoadError ?: companionNativeLoadError()

    fun isSessionLoaded(): Boolean = sessionLoaded

    fun validateModelPath(path: String): Boolean {
        if (path.isBlank()) return false
        if (path.startsWith(STUB_SCHEME)) return true
        return File(path).exists()
    }

    /**
     * 加载 MNN LLM 会话（含 runtime set_config + tuning）。
     */
    @Synchronized
    fun loadModel(path: String): Boolean {
        lastLoadError = null
        sessionLoaded = false
        if (path.startsWith(STUB_SCHEME)) {
            lastLoadError = "stub_path_no_native"
            return false
        }
        if (!tryLoadNative()) {
            lastLoadError = nativeLoadError ?: "native_unavailable"
            return false
        }
        val root = File(path)
        if (!root.exists()) {
            lastLoadError = "model_path_missing:$path"
            return false
        }
        return try {
            unloadNativeSafe()
            val ok = nativeLoadModel(path)
            if (ok) {
                sessionLoaded = true
                lastLoadError = null
                Log.i(TAG, "loadModel ok path=${root.name}")
                true
            } else {
                lastLoadError = nativeLastError() ?: "load_failed"
                sessionLoaded = false
                false
            }
        } catch (t: Throwable) {
            unloadNativeSafe()
            lastLoadError = "load_failed:${t.javaClass.simpleName}:${t.message}"
            sessionLoaded = false
            Log.e(TAG, "loadModel failed", t)
            false
        }
    }

    /**
     * 单字符串生成（兼容旧路径）。
     */
    @Synchronized
    fun generate(prompt: String, maxTokens: Int): String? {
        if (!sessionLoaded) {
            lastLoadError = lastLoadError ?: "not_loaded"
            return null
        }
        if (!tryLoadNative()) {
            lastLoadError = nativeLoadError ?: "native_unavailable"
            return null
        }
        return try {
            val text = nativeGenerate(prompt, maxTokens)
            if (text == null) {
                lastLoadError = nativeLastError() ?: "generate_null"
            }
            text
        } catch (t: Throwable) {
            lastLoadError = "generate_failed:${t.javaClass.simpleName}:${t.message}"
            Log.e(TAG, "generate failed", t)
            null
        }
    }

    /**
     * ChatMessages 多轮生成（对齐 MNN Chat）。
     *
     * @param roles system/user/assistant…
     * @param contents 与 roles 等长
     */
    @Synchronized
    fun generateChat(
        roles: Array<String>,
        contents: Array<String>,
        maxTokens: Int
    ): String? {
        if (!sessionLoaded) {
            lastLoadError = lastLoadError ?: "not_loaded"
            return null
        }
        if (roles.size != contents.size || roles.isEmpty()) {
            lastLoadError = "chat_args_mismatch"
            return null
        }
        if (!tryLoadNative()) {
            lastLoadError = nativeLoadError ?: "native_unavailable"
            return null
        }
        return try {
            val text = nativeGenerateChat(roles, contents, maxTokens)
            if (text == null) {
                lastLoadError = nativeLastError() ?: "generate_chat_null"
            }
            text
        } catch (t: Throwable) {
            lastLoadError = "generate_chat_failed:${t.javaClass.simpleName}:${t.message}"
            Log.e(TAG, "generateChat failed", t)
            null
        }
    }

    /**
     * 流式 Chat 生成。
     *
     * @param onToken 每段增量；返回 true 表示请求停止
     * @return 完整文本（context.generate_str）；失败 null
     */
    @Synchronized
    fun generateChatStream(
        roles: Array<String>,
        contents: Array<String>,
        maxTokens: Int,
        onToken: (String) -> Boolean
    ): String? {
        if (!sessionLoaded) {
            lastLoadError = lastLoadError ?: "not_loaded"
            return null
        }
        if (roles.size != contents.size || roles.isEmpty()) {
            lastLoadError = "chat_args_mismatch"
            return null
        }
        if (!tryLoadNative()) {
            lastLoadError = nativeLoadError ?: "native_unavailable"
            return null
        }
        return try {
            val listener = object : StreamTokenListener {
                override fun onToken(piece: String?): Boolean {
                    if (piece.isNullOrEmpty()) return false
                    return try {
                        onToken(piece)
                    } catch (_: Throwable) {
                        true
                    }
                }
            }
            val text = nativeGenerateChatStream(roles, contents, maxTokens, listener)
            if (text == null) {
                lastLoadError = nativeLastError() ?: "generate_stream_null"
            }
            text
        } catch (t: Throwable) {
            lastLoadError = "generate_stream_failed:${t.javaClass.simpleName}:${t.message}"
            Log.e(TAG, "generateChatStream failed", t)
            null
        }
    }

    /** 请求取消当前生成。 */
    fun cancel() {
        if (!tryLoadNative()) return
        runCatching { nativeCancel() }
    }

    /** 重置 KV / 历史（多轮会话新开话题时）。 */
    @Synchronized
    fun reset() {
        if (!sessionLoaded || !tryLoadNative()) return
        runCatching { nativeReset() }
    }

    @Synchronized
    fun unload() {
        unloadNativeSafe()
        sessionLoaded = false
        lastLoadError = null
    }

    private fun unloadNativeSafe() {
        if (!tryLoadNative()) return
        try {
            nativeUnload()
        } catch (_: Throwable) {
        }
    }

    /** JNI 流式监听（方法名/签名必须与 native 一致）。 */
    interface StreamTokenListener {
        /** @return true = 停止生成 */
        fun onToken(piece: String?): Boolean
    }

    // region JNI
    private external fun nativeLoadModel(path: String): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String?
    private external fun nativeGenerateChat(
        roles: Array<String>,
        contents: Array<String>,
        maxTokens: Int
    ): String?

    private external fun nativeGenerateChatStream(
        roles: Array<String>,
        contents: Array<String>,
        maxTokens: Int,
        listener: StreamTokenListener?
    ): String?

    private external fun nativeCancel()
    private external fun nativeReset()
    private external fun nativeUnload()
    private external fun nativeLastError(): String?
    private external fun nativeIsLoaded(): Boolean
    // endregion

    companion object {
        const val STUB_SCHEME = "stub://"
        private const val TAG = "MnnNativeBridge"

        private val NATIVE_LIBS = listOf(
            "c++_shared",
            "MNN",
            "MNN_Express",
            "MNN_CL",
            "MNN_Vulkan",
            "MNNOpenCV",
            "MNNAudio",
            "llm",
            "mnn_lanxin"
        )

        @Volatile
        private var nativeLoadAttempted: Boolean = false

        @Volatile
        private var nativeOk: Boolean = false

        @Volatile
        private var nativeLoadError: String? = null

        @JvmStatic
        fun tryLoadNative(): Boolean {
            if (nativeLoadAttempted) return nativeOk
            synchronized(this) {
                if (nativeLoadAttempted) return nativeOk
                nativeLoadAttempted = true
                return try {
                    for (lib in NATIVE_LIBS) {
                        try {
                            System.loadLibrary(lib)
                        } catch (e: UnsatisfiedLinkError) {
                            if (lib == "c++_shared") {
                                Log.w(TAG, "c++_shared skip: ${e.message}")
                                continue
                            }
                            throw e
                        }
                    }
                    nativeOk = true
                    nativeLoadError = null
                    true
                } catch (e: UnsatisfiedLinkError) {
                    nativeOk = false
                    nativeLoadError = "UnsatisfiedLinkError:${e.message}"
                    false
                } catch (t: Throwable) {
                    nativeOk = false
                    nativeLoadError = "${t.javaClass.simpleName}:${t.message}"
                    false
                }
            }
        }

        @JvmStatic
        fun resetNativeLoadStateForTests() {
            nativeLoadAttempted = false
            nativeOk = false
            nativeLoadError = null
        }

        @JvmStatic
        fun companionNativeLoadError(): String? = nativeLoadError
    }
}
