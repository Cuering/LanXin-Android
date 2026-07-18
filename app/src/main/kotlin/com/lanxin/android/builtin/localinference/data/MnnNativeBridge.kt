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
 * - 运行时 so：官方 Android 预编译包（libMNN*.so + libllm.so）+ 自研 `libmnn_lanxin.so`
 *   见 `app/build.gradle.kts` 的 `downloadMnnNative` 与 `app/src/main/cpp/`
 * - 模型权重外置：`LanXin/models/local-llm/light/`（config.json + *.mnn + tokenizer 等，不进 git）
 * - JVM 单测：无 so 时 [isNativeAvailable] 为 false；[loadModel]/[generate] 安全降级
 */
@Singleton
class MnnNativeBridge @Inject constructor() {

    @Volatile
    private var lastLoadError: String? = null

    @Volatile
    private var sessionLoaded: Boolean = false

    /**
     * native 库是否可 [System.loadLibrary]。
     * 无 so 的 JVM 单测返回 false，不抛异常。
     */
    fun isNativeAvailable(): Boolean = tryLoadNative()

    fun nativeLoadError(): String? = companionNativeLoadError()

    fun lastError(): String? = lastLoadError ?: companionNativeLoadError()

    fun isSessionLoaded(): Boolean = sessionLoaded

    /**
     * 校验模型路径是否存在。
     * 允许目录（含 config.json / 多文件权重）或单文件。
     * 路径以 `stub://` 开头时视为合法虚拟路径（JVM 单测）。
     */
    fun validateModelPath(path: String): Boolean {
        if (path.isBlank()) return false
        if (path.startsWith(STUB_SCHEME)) return true
        val file = File(path)
        return file.exists()
    }

    /**
     * 加载 MNN LLM 会话。
     *
     * @param path 模型目录或 config.json 路径
     * @return true 成功（native 已 load）；false 时见 [lastError]
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
     * native 侧生成完整文本。
     *
     * @return 生成文本；未实现 / 失败时 null
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

    /** 释放 native 会话。 */
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

    // region JNI (implemented in libmnn_lanxin.so)
    private external fun nativeLoadModel(path: String): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String?
    private external fun nativeUnload()
    private external fun nativeLastError(): String?
    private external fun nativeIsLoaded(): Boolean
    // endregion

    companion object {
        const val STUB_SCHEME = "stub://"
        private const val TAG = "MnnNativeBridge"

        /**
         * 依赖顺序（与 libllm.so NEEDED 一致）：
         * c++_shared → MNN → Express → CL/Vulkan/OpenCV/Audio → llm → mnn_lanxin
         */
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

        /**
         * 尝试加载 MNN 相关 so。
         * 可重复调用；失败不抛到调用方。
         * 仅 c++_shared 允许缺失（部分设备 / 系统已提供）。
         * libllm 依赖 CL/Vulkan/OpenCV/Audio，不可跳过。
         */
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
                            // c++_shared 在部分设备已由系统 / 其它 AAR 提供
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

        /** 测试钩子：重置 load 状态（仅 JVM 单测使用）。 */
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
