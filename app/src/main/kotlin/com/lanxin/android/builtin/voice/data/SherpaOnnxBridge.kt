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

import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sherpa-ONNX JNI 接入点（ASR）。
 *
 * - 运行时 so 由官方 AAR 打进 APK（见 app/libs + downloadSherpaOnnxAar）
 * - ASR 模型外置：`LanXin/asr/<model-dir>/`（不进 git）
 * - TTS 见 [SherpaTtsBridge]（共用同一 `libsherpa-onnx-jni.so`）
 * - JVM 单测：无 so 时 [isNativeAvailable] 为 false；[loadModel]/[transcribe] 安全降级
 *
 * 支持目录布局：
 * 1. 流式 zipformer transducer：encoder*.onnx + decoder*.onnx + joiner*.onnx + tokens.txt
 * 2. 离线 paraformer：model*.onnx 或 paraformer*.onnx + tokens.txt
 */
@Singleton
class SherpaOnnxBridge @Inject constructor() {

    @Volatile
    private var online: OnlineRecognizer? = null

    @Volatile
    private var offline: OfflineRecognizer? = null

    @Volatile
    private var mode: Mode = Mode.NONE

    @Volatile
    private var lastLoadError: String? = null

    enum class Mode {
        NONE,
        ONLINE_TRANSDUCER,
        OFFLINE_PARAFORMER
    }

    /**
     * native 库是否可 [System.loadLibrary]。
     * 无 so 的 JVM 单测返回 false，不抛异常。
     */
    fun isNativeAvailable(): Boolean = tryLoadNative()

    fun nativeLoadError(): String? = companionNativeLoadError()

    fun currentMode(): Mode = mode

    fun lastError(): String? = lastLoadError

    /**
     * 校验模型路径是否存在。
     * 允许目录（tokens + encoder/decoder 等）或单文件。
     * 路径以 `stub://` 开头时视为合法虚拟路径（JVM 单测）。
     */
    fun validateModelPath(path: String): Boolean {
        if (path.isBlank()) return false
        if (path.startsWith(STUB_SCHEME)) return true
        val file = File(path)
        return file.exists()
    }

    /**
     * 加载 Sherpa-ONNX 识别器。
     *
     * @return true 成功（native 已 load）；false 时见 [lastError]
     */
    @Synchronized
    fun loadModel(path: String, language: String): Boolean {
        lastLoadError = null
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
        unloadInternal()
        return try {
            val layout = detectLayout(root)
            when (layout) {
                is ModelLayout.OnlineTransducer -> {
                    online = createOnline(layout)
                    mode = Mode.ONLINE_TRANSDUCER
                    Log.i(TAG, "loaded online transducer lang=$language dir=${root.name}")
                    true
                }
                is ModelLayout.OfflineParaformer -> {
                    offline = createOfflineParaformer(layout)
                    mode = Mode.OFFLINE_PARAFORMER
                    Log.i(TAG, "loaded offline paraformer lang=$language dir=${root.name}")
                    true
                }
                null -> {
                    lastLoadError = "unsupported_model_layout:$path"
                    false
                }
            }
        } catch (t: Throwable) {
            unloadInternal()
            lastLoadError = "load_failed:${t.javaClass.simpleName}:${t.message}"
            Log.e(TAG, "loadModel failed", t)
            false
        }
    }

    /**
     * native 侧整段转写。
     *
     * @param pcm16leMono 16-bit LE mono PCM
     * @param sampleRateHz 采样率
     * @return 识别文本；未实现 / 失败时 null
     */
    @Synchronized
    fun transcribe(pcm16leMono: ByteArray, sampleRateHz: Int): String? {
        if (pcm16leMono.isEmpty()) return ""
        val samples = pcm16leToFloat(pcm16leMono)
        return try {
            when (mode) {
                Mode.ONLINE_TRANSDUCER -> {
                    val rec = online ?: return null
                    val stream = rec.createStream()
                    try {
                        stream.acceptWaveform(samples, sampleRateHz)
                        stream.inputFinished()
                        while (rec.isReady(stream)) {
                            rec.decode(stream)
                        }
                        rec.getResult(stream).text.trim()
                    } finally {
                        stream.release()
                    }
                }
                Mode.OFFLINE_PARAFORMER -> {
                    val rec = offline ?: return null
                    val stream = rec.createStream()
                    try {
                        stream.acceptWaveform(samples, sampleRateHz)
                        rec.decode(stream)
                        rec.getResult(stream).text.trim()
                    } finally {
                        stream.release()
                    }
                }
                Mode.NONE -> null
            }
        } catch (t: Throwable) {
            lastLoadError = "transcribe_failed:${t.javaClass.simpleName}:${t.message}"
            Log.e(TAG, "transcribe failed", t)
            null
        }
    }

    // ------------------------------------------------------------------
    // 流式 ASR（OnlineRecognizer 边录边解）
    // ------------------------------------------------------------------

    @Volatile
    private var streamingStream: com.k2fsa.sherpa.onnx.OnlineStream? = null

    @Volatile
    private var streamingSampleRate: Int = 16_000

    @Volatile
    private var lastPartialText: String = ""

    /** 当前是否在流式会话中。 */
    fun isStreaming(): Boolean = streamingStream != null

    /**
     * 开启流式识别会话（仅 OnlineTransducer 模式可用）。
     *
     * OfflineParaformer 不支持流式，返回 false，调用方应回退整段 transcribe。
     */
    @Synchronized
    fun startStreaming(sampleRateHz: Int = 16_000): Boolean {
        stopStreamingInternal()
        lastPartialText = ""
        streamingSampleRate = sampleRateHz.coerceIn(8_000, 48_000)
        val rec = online
        if (mode != Mode.ONLINE_TRANSDUCER || rec == null) {
            lastLoadError = "streaming_requires_online_transducer"
            return false
        }
        return try {
            streamingStream = rec.createStream()
            true
        } catch (t: Throwable) {
            lastLoadError = "start_stream_failed:${t.javaClass.simpleName}:${t.message}"
            Log.e(TAG, "startStreaming failed", t)
            streamingStream = null
            false
        }
    }

    /**
     * 喂入一段 PCM chunk，返回当前 partial 文本（可能与上次相同）。
     *
     * 未开启流式 / 失败时返回 null。
     */
    @Synchronized
    fun feedPcmChunk(pcm16leMono: ByteArray): String? {
        val stream = streamingStream ?: return null
        val rec = online ?: return null
        if (pcm16leMono.isEmpty()) return lastPartialText
        return try {
            val samples = pcm16leToFloat(pcm16leMono)
            stream.acceptWaveform(samples, streamingSampleRate)
            while (rec.isReady(stream)) {
                rec.decode(stream)
            }
            val text = rec.getResult(stream).text.trim()
            lastPartialText = text
            text
        } catch (t: Throwable) {
            lastLoadError = "feed_chunk_failed:${t.javaClass.simpleName}:${t.message}"
            Log.e(TAG, "feedPcmChunk failed", t)
            null
        }
    }

    /**
     * 当前 partial 文本（不推进解码）。
     */
    fun currentPartial(): String = lastPartialText

    /**
     * 是否检测到 endpoint（说完一句，可自动停麦）。
     * 仅 OnlineTransducer + enableEndpoint=true 时有效。
     */
    @Synchronized
    fun isEndpoint(): Boolean {
        val stream = streamingStream ?: return false
        val rec = online ?: return false
        return try {
            rec.isEndpoint(stream)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 结束流式会话，返回最终文本。
     */
    @Synchronized
    fun finishStreaming(): String? {
        val stream = streamingStream
        val rec = online
        if (stream == null || rec == null) {
            stopStreamingInternal()
            return lastPartialText.ifBlank { null }
        }
        return try {
            stream.inputFinished()
            while (rec.isReady(stream)) {
                rec.decode(stream)
            }
            val text = rec.getResult(stream).text.trim()
            lastPartialText = text
            text
        } catch (t: Throwable) {
            lastLoadError = "finish_stream_failed:${t.javaClass.simpleName}:${t.message}"
            Log.e(TAG, "finishStreaming failed", t)
            lastPartialText.ifBlank { null }
        } finally {
            stopStreamingInternal()
        }
    }

    /** 取消流式会话，丢弃结果。 */
    @Synchronized
    fun cancelStreaming() {
        stopStreamingInternal()
        lastPartialText = ""
    }

    private fun stopStreamingInternal() {
        try {
            streamingStream?.release()
        } catch (_: Throwable) {
        }
        streamingStream = null
    }

    /** 释放 native 会话。 */
    @Synchronized
    fun unload() {
        unloadInternal()
    }

    private fun unloadInternal() {
        stopStreamingInternal()
        lastPartialText = ""
        try {
            online?.release()
        } catch (_: Throwable) {
        }
        try {
            offline?.release()
        } catch (_: Throwable) {
        }
        online = null
        offline = null
        mode = Mode.NONE
    }

    private sealed class ModelLayout {
        data class OnlineTransducer(
            val encoder: String,
            val decoder: String,
            val joiner: String,
            val tokens: String
        ) : ModelLayout()

        data class OfflineParaformer(
            val model: String,
            val tokens: String
        ) : ModelLayout()
    }

    private fun detectLayout(root: File): ModelLayout? {
        val dir = if (root.isDirectory) root else root.parentFile ?: return null
        val tokens = firstExisting(
            File(dir, "tokens.txt"),
            File(dir, "tokens")
        ) ?: return null

        val encoder = findModelFile(dir, listOf("encoder"))
        val decoder = findModelFile(dir, listOf("decoder"))
        val joiner = findModelFile(dir, listOf("joiner"))
        if (encoder != null && decoder != null && joiner != null) {
            return ModelLayout.OnlineTransducer(
                encoder = encoder.absolutePath,
                decoder = decoder.absolutePath,
                joiner = joiner.absolutePath,
                tokens = tokens.absolutePath
            )
        }

        val para = findModelFile(
            dir,
            listOf("model", "paraformer")
        ) ?: dir.listFiles()
            ?.firstOrNull { f ->
                f.isFile &&
                    f.name.endsWith(".onnx", ignoreCase = true) &&
                    !f.name.contains("encoder", true) &&
                    !f.name.contains("decoder", true) &&
                    !f.name.contains("joiner", true)
            }
        if (para != null) {
            return ModelLayout.OfflineParaformer(
                model = para.absolutePath,
                tokens = tokens.absolutePath
            )
        }
        return null
    }

    private fun findModelFile(dir: File, prefixes: List<String>): File? {
        val onnx = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".onnx", ignoreCase = true) }
            .orEmpty()
        // prefer int8
        for (p in prefixes) {
            onnx.firstOrNull {
                it.name.startsWith(p, ignoreCase = true) && it.name.contains("int8", true)
            }?.let { return it }
            onnx.firstOrNull { it.name.startsWith(p, ignoreCase = true) }?.let { return it }
        }
        return null
    }

    private fun firstExisting(vararg files: File): File? = files.firstOrNull { it.exists() }

    private fun createOnline(layout: ModelLayout.OnlineTransducer): OnlineRecognizer {
        val modelConfig = OnlineModelConfig()
        modelConfig.transducer = OnlineTransducerModelConfig(
            encoder = layout.encoder,
            decoder = layout.decoder,
            joiner = layout.joiner
        )
        modelConfig.tokens = layout.tokens
        modelConfig.numThreads = 2
        modelConfig.debug = false
        modelConfig.provider = "cpu"
        modelConfig.modelType = "zipformer"

        val config = OnlineRecognizerConfig()
        config.featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80)
        config.modelConfig = modelConfig
        config.enableEndpoint = true
        config.decodingMethod = "greedy_search"
        return OnlineRecognizer(assetManager = null, config = config)
    }

    private fun createOfflineParaformer(layout: ModelLayout.OfflineParaformer): OfflineRecognizer {
        val modelConfig = OfflineModelConfig()
        modelConfig.paraformer = OfflineParaformerModelConfig(model = layout.model)
        modelConfig.tokens = layout.tokens
        modelConfig.numThreads = 2
        modelConfig.debug = false
        modelConfig.provider = "cpu"
        modelConfig.modelType = "paraformer"

        val config = OfflineRecognizerConfig()
        config.featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80)
        config.modelConfig = modelConfig
        config.decodingMethod = "greedy_search"
        return OfflineRecognizer(assetManager = null, config = config)
    }

    companion object {
        const val STUB_SCHEME = "stub://"
        private const val TAG = "SherpaOnnxBridge"
        private const val NATIVE_LIB = "sherpa-onnx-jni"

        @Volatile
        private var nativeLoadAttempted: Boolean = false

        @Volatile
        private var nativeOk: Boolean = false

        @Volatile
        private var nativeLoadError: String? = null

        /**
         * 尝试加载 libsherpa-onnx-jni.so。
         * 可重复调用；失败不抛到调用方。
         */
        @JvmStatic
        fun tryLoadNative(): Boolean {
            if (nativeLoadAttempted) return nativeOk
            synchronized(this) {
                if (nativeLoadAttempted) return nativeOk
                nativeLoadAttempted = true
                return try {
                    System.loadLibrary(NATIVE_LIB)
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

        /** 供 [SherpaTtsBridge] 等读取最近一次 loadLibrary 错误。 */
        @JvmStatic
        fun companionNativeLoadError(): String? = nativeLoadError

        fun pcm16leToFloat(pcm16leMono: ByteArray): FloatArray {
            val n = pcm16leMono.size / 2
            val out = FloatArray(n)
            val buf = ByteBuffer.wrap(pcm16leMono).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until n) {
                out[i] = buf.short / 32768.0f
            }
            return out
        }
    }
}
