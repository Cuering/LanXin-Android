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
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsMatchaModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Sherpa-ONNX Offline TTS 接入点（与 [SherpaOnnxBridge] 共用 AAR / so）。
 *
 * - 运行时 so：`libsherpa-onnx-jni.so`（P0 已进 APK）
 * - 模型外置：`LanXin/tts/<model-dir>/`（不进 git）
 * - Matcha 还需 vocoder（如 `vocos-22khz-univ.onnx`），可放在模型目录或上一级
 * - JVM 无 so：`isNativeAvailable()==false`，load/synthesize 安全失败
 */
@Singleton
class SherpaTtsBridge @Inject constructor() {

    @Volatile
    private var tts: OfflineTts? = null

    @Volatile
    private var mode: Mode = Mode.NONE

    @Volatile
    private var lastLoadError: String? = null

    @Volatile
    private var sampleRateHz: Int = 0

    enum class Mode {
        NONE,
        MATCHA,
        VITS
    }

    fun isNativeAvailable(): Boolean = SherpaOnnxBridge.tryLoadNative()

    fun nativeLoadError(): String? = SherpaOnnxBridge.companionNativeLoadError()

    fun currentMode(): Mode = mode

    fun lastError(): String? = lastLoadError

    fun sampleRate(): Int = sampleRateHz

    /**
     * 校验 TTS 模型路径。
     * - `stub://…` 虚拟路径（单测）
     * - 目录存在即可（布局细节在 load 时再解析）
     */
    fun validateModelPath(path: String): Boolean {
        if (path.isBlank()) return false
        if (path.startsWith(SherpaOnnxBridge.STUB_SCHEME)) return true
        return File(path).exists()
    }

    /**
     * 加载 OfflineTts。
     *
     * @param modelDir 模型目录（matcha / vits）
     * @return true 仅当 native OfflineTts 构造成功
     */
    @Synchronized
    fun loadModel(modelDir: String): Boolean {
        lastLoadError = null
        if (modelDir.startsWith(SherpaOnnxBridge.STUB_SCHEME)) {
            lastLoadError = "stub_path_no_native"
            return false
        }
        if (!isNativeAvailable()) {
            lastLoadError = nativeLoadError() ?: "native_unavailable"
            return false
        }
        val root = File(modelDir)
        if (!root.isDirectory) {
            lastLoadError = "model_dir_missing:$modelDir"
            return false
        }
        unloadInternal()
        return try {
            val layout = detectLayout(root)
            if (layout == null) {
                lastLoadError = "unsupported_tts_layout:$modelDir"
                return false
            }
            val config = buildConfig(layout)
            val engine = OfflineTts(assetManager = null, config = config)
            tts = engine
            mode = when (layout) {
                is TtsLayout.Matcha -> Mode.MATCHA
                is TtsLayout.Vits -> Mode.VITS
            }
            sampleRateHz = try {
                engine.sampleRate()
            } catch (_: Throwable) {
                0
            }
            Log.i(TAG, "loaded tts mode=$mode rate=$sampleRateHz dir=${root.name}")
            true
        } catch (t: Throwable) {
            unloadInternal()
            lastLoadError = "load_failed:${t.javaClass.simpleName}:${t.message}"
            Log.e(TAG, "loadModel failed", t)
            false
        }
    }

    /**
     * 文本合成。
     *
     * @return float samples + sampleRate；失败 null
     */
    @Synchronized
    fun synthesize(text: String, speakerId: Int = 0, speed: Float = 1.0f): SynthAudio? {
        val engine = tts ?: run {
            lastLoadError = "tts_not_loaded"
            return null
        }
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return SynthAudio(floatArrayOf(), sampleRateHz.coerceAtLeast(1))
        }
        return try {
            val audio = engine.generate(
                text = trimmed,
                sid = speakerId.coerceAtLeast(0),
                speed = speed.coerceIn(0.5f, 2.0f)
            )
            val rate = if (audio.sampleRate > 0) audio.sampleRate else sampleRateHz
            if (rate > 0) sampleRateHz = rate
            SynthAudio(samples = audio.samples, sampleRateHz = rate)
        } catch (t: Throwable) {
            lastLoadError = "synthesize_failed:${t.javaClass.simpleName}:${t.message}"
            Log.e(TAG, "synthesize failed", t)
            null
        }
    }

    @Synchronized
    fun unload() {
        unloadInternal()
    }

    private fun unloadInternal() {
        try {
            tts?.release()
        } catch (_: Throwable) {
        }
        tts = null
        mode = Mode.NONE
        sampleRateHz = 0
    }

    private sealed class TtsLayout {
        data class Matcha(
            val acousticModel: String,
            val vocoder: String,
            val tokens: String,
            val lexicon: String,
            val ruleFsts: String,
            val dictDir: String
        ) : TtsLayout()

        data class Vits(
            val model: String,
            val tokens: String,
            val lexicon: String,
            val dataDir: String,
            val dictDir: String,
            val ruleFsts: String
        ) : TtsLayout()
    }

    private fun detectLayout(root: File): TtsLayout? {
        val tokens = firstExisting(File(root, "tokens.txt"), File(root, "tokens")) ?: return null
        val lexicon = firstExisting(File(root, "lexicon.txt"), File(root, "lexicon"))
        val dictDir = firstExistingDir(File(root, "dict"), File(root, "espeak-ng-data"))
        val ruleFsts = joinExisting(
            File(root, "phone.fst"),
            File(root, "date.fst"),
            File(root, "number.fst")
        )

        // Matcha: model-steps*.onnx / *matcha*.onnx + vocoder
        val acoustic = findNamedOnnx(
            root,
            preferredSubstrings = listOf("model-steps", "matcha", "acoustic")
        ) ?: root.listFiles()
            ?.firstOrNull {
                it.isFile &&
                    it.name.endsWith(".onnx", true) &&
                    !it.name.contains("vocos", true) &&
                    !it.name.contains("hifigan", true) &&
                    !it.name.contains("vocoder", true)
            }

        val vocoder = findVocoder(root)
        if (acoustic != null && vocoder != null && looksLikeMatcha(acoustic, root)) {
            return TtsLayout.Matcha(
                acousticModel = acoustic.absolutePath,
                vocoder = vocoder.absolutePath,
                tokens = tokens.absolutePath,
                lexicon = lexicon?.absolutePath.orEmpty(),
                ruleFsts = ruleFsts,
                dictDir = dictDir?.absolutePath.orEmpty()
            )
        }

        // VITS: single model.onnx / *vits* / *melo*
        val vitsModel = findNamedOnnx(
            root,
            preferredSubstrings = listOf("model", "vits", "melo")
        ) ?: root.listFiles()
            ?.firstOrNull {
                it.isFile &&
                    it.name.endsWith(".onnx", true) &&
                    !it.name.contains("vocos", true) &&
                    !it.name.contains("hifigan", true)
            }
        if (vitsModel != null) {
            val dataDir = firstExistingDir(
                File(root, "espeak-ng-data"),
                File(root, "data")
            )
            return TtsLayout.Vits(
                model = vitsModel.absolutePath,
                tokens = tokens.absolutePath,
                lexicon = lexicon?.absolutePath.orEmpty(),
                dataDir = dataDir?.absolutePath.orEmpty(),
                dictDir = dictDir?.absolutePath.orEmpty(),
                ruleFsts = ruleFsts
            )
        }
        return null
    }

    private fun looksLikeMatcha(acoustic: File, root: File): Boolean {
        val n = acoustic.name.lowercase()
        if (n.contains("model-steps") || n.contains("matcha")) return true
        // 有 vocoder 文件且目录名含 matcha
        if (root.name.contains("matcha", ignoreCase = true)) return true
        return findVocoder(root) != null && n.contains("model")
    }

    private fun findVocoder(root: File): File? {
        val names = listOf(
            "vocos-22khz-univ.onnx",
            "vocos.onnx",
            "hifigan_v2.onnx",
            "hifigan_v1.onnx",
            "hifigan_v3.onnx",
            "hifigan.onnx",
            "vocoder.onnx"
        )
        val searchDirs = listOf(
            root,
            root.parentFile,
            File(root, "vocoder"),
            File(root, "vocoders")
        ).filterNotNull()
        for (dir in searchDirs) {
            for (name in names) {
                val f = File(dir, name)
                if (f.isFile && f.length() > 10_000L) return f
            }
            dir.listFiles()
                ?.firstOrNull { f ->
                    f.isFile &&
                        f.name.endsWith(".onnx", true) &&
                        (
                            f.name.contains("vocos", true) ||
                                f.name.contains("hifigan", true) ||
                                f.name.contains("vocoder", true)
                            )
                }
                ?.let { return it }
        }
        return null
    }

    private fun findNamedOnnx(dir: File, preferredSubstrings: List<String>): File? {
        val onnx = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".onnx", ignoreCase = true) }
            .orEmpty()
        for (sub in preferredSubstrings) {
            onnx.firstOrNull { it.name.contains(sub, ignoreCase = true) }?.let { return it }
        }
        return null
    }

    private fun buildConfig(layout: TtsLayout): OfflineTtsConfig {
        return when (layout) {
            is TtsLayout.Matcha -> {
                val matcha = OfflineTtsMatchaModelConfig(
                    acousticModel = layout.acousticModel,
                    vocoder = layout.vocoder,
                    lexicon = layout.lexicon,
                    tokens = layout.tokens,
                    dataDir = "",
                    dictDir = layout.dictDir,
                    noiseScale = 1.0f,
                    lengthScale = 1.0f
                )
                OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        matcha = matcha,
                        numThreads = 2,
                        debug = false,
                        provider = "cpu"
                    ),
                    ruleFsts = layout.ruleFsts,
                    ruleFars = "",
                    maxNumSentences = 1,
                    silenceScale = 0.2f
                )
            }
            is TtsLayout.Vits -> {
                val vits = OfflineTtsVitsModelConfig(
                    model = layout.model,
                    lexicon = layout.lexicon,
                    tokens = layout.tokens,
                    dataDir = layout.dataDir,
                    dictDir = layout.dictDir,
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                )
                OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = vits,
                        numThreads = 2,
                        debug = false,
                        provider = "cpu"
                    ),
                    ruleFsts = layout.ruleFsts,
                    ruleFars = "",
                    maxNumSentences = 1,
                    silenceScale = 0.2f
                )
            }
        }
    }

    private fun firstExisting(vararg files: File): File? = files.firstOrNull { it.exists() }

    private fun firstExistingDir(vararg files: File): File? =
        files.firstOrNull { it.isDirectory }

    private fun joinExisting(vararg files: File): String =
        files.filter { it.isFile }.joinToString(",") { it.absolutePath }

    data class SynthAudio(
        val samples: FloatArray,
        val sampleRateHz: Int
    )

    companion object {
        private const val TAG = "SherpaTtsBridge"

        /** float [-1,1] → PCM16 LE mono */
        fun floatToPcm16le(samples: FloatArray): ByteArray {
            val out = ByteArray(samples.size * 2)
            val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) {
                val clipped = max(-1.0f, min(1.0f, s))
                buf.putShort((clipped * 32767.0f).roundToInt().toShort())
            }
            return out
        }

        fun durationMs(sampleCount: Int, sampleRateHz: Int): Long {
            if (sampleRateHz <= 0 || sampleCount <= 0) return 0L
            return (sampleCount * 1000L) / sampleRateHz
        }
    }
}
