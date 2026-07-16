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

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MNN / JNI 接入点。
 *
 * Phase 6.1：仅路径校验与 no-op unload。
 * 后续步骤：
 * 1. 引入 libMNN.so + JNI wrapper
 * 2. 实现 [loadModel] / [generate] / [unload]
 * 3. 在 Hilt 中将 [StubLocalLlmEngine] 换为 MnnLocalLlmEngine
 *
 * 参考：MNN 官方 Android demo；妹居 MeiJu 本地推理封装。
 */
@Singleton
class MnnNativeBridge @Inject constructor() {

    /**
     * 校验模型路径是否存在。
     * 允许目录（含多文件权重）或单文件。
     *
     * 为便于 JVM 单测，路径以 `stub://` 开头时视为合法虚拟路径。
     */
    fun validateModelPath(path: String): Boolean {
        if (path.isBlank()) return false
        if (path.startsWith(STUB_SCHEME)) return true
        val file = File(path)
        return file.exists()
    }

    /**
     * 预留：加载 MNN 会话。
     * @return true 成功
     */
    fun loadModel(path: String): Boolean {
        // Phase 6.1：未链接 native，始终 false；引擎层用 stub 逻辑
        return false
    }

    /**
     * 预留：native 侧生成。
     */
    fun generate(prompt: String, maxTokens: Int): String? = null

    /** 预留：释放 native 会话。 */
    fun unload() {
        // no-op
    }

    companion object {
        const val STUB_SCHEME = "stub://"
        // 未来：System.loadLibrary("mnn_lanxin")
    }
}
