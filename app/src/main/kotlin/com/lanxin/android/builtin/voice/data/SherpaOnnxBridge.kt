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

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sherpa-ONNX / JNI 接入点。
 *
 * Phase 6.4：仅路径校验与 no-op unload / 预留 API。
 * 后续步骤：
 * 1. 引入 libsherpa-onnx-jni.so（或 AAR）+ 模型目录
 * 2. 实现 [loadModel] / [transcribe] / [unload]
 * 3. 在 Hilt 中将 [StubAsrEngine] 换为 SherpaAsrEngine
 *
 * 参考：k2-fsa/sherpa-onnx Android demo；妹居 MeiJu 语音封装。
 * 大 so / 模型文件 **禁止提交 git**，用 adb push 或本地下载。
 */
@Singleton
class SherpaOnnxBridge @Inject constructor() {

    /**
     * 校验模型路径是否存在。
     * 允许目录（tokens + encoder/decoder 等）或单文件。
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
     * 预留：加载 Sherpa-ONNX 识别器。
     *
     * @return true 成功
     */
    fun loadModel(path: String, language: String): Boolean {
        // Phase 6.4：未链接 native，始终 false；引擎层用 stub 逻辑
        return false
    }

    /**
     * 预留：native 侧整段转写。
     *
     * @param pcm16leMono 16-bit LE mono PCM
     * @param sampleRateHz 采样率
     * @return 识别文本；未实现时 null
     */
    fun transcribe(pcm16leMono: ByteArray, sampleRateHz: Int): String? = null

    /** 预留：释放 native 会话。 */
    fun unload() {
        // no-op
    }

    companion object {
        const val STUB_SCHEME = "stub://"
        // 未来：System.loadLibrary("sherpa-onnx-jni")
    }
}
