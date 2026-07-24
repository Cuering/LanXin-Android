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

package com.lanxin.android.builtin.pet.domain

import com.lanxin.android.builtin.localinference.domain.LocalReplySanitizer
import com.lanxin.android.builtin.voice.data.PcmAudioPlayer
import com.lanxin.android.builtin.voice.domain.SystemTtsSpeaker
import com.lanxin.android.builtin.voice.domain.TtsConfig
import com.lanxin.android.builtin.voice.domain.TtsEngine
import com.lanxin.android.builtin.voice.domain.TtsSettings
import com.lanxin.android.builtin.voice.domain.TtsSynthesizeRequest
import com.lanxin.android.core.log.LogManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 输出流水线：文字 → TTS → PCM 播放。
 *
 * 这一层只负责「说」，不涉及 LLM/ASR。
 * 各环节附带耗时/报错监控（tag=`VoiceOutPLC`）。
 *
 * ## 环节
 * | 环节           | 耗时字段         | 报错字段         |
 * |---------------|-----------------|-----------------|
 * | ensureTts     | ttsLoadDurMs    | ttsLoadError    |
 * | synthesize    | synthDurMs      | synthError      |
 * | play          | playDurMs       | playError       |
 *
 * 与 [VoiceChatSession.onReplyReady] 的区别：
 * - 无状态机相位，无连续听逻辑
 * - 纯输出，调用方决定「播完做什么」
 * - 可独立测试：传入文字，等播放完成即可
 */
@Singleton
class VoiceOutputPipeline @Inject constructor(
    private val ttsEngine: TtsEngine,
    private val ttsSettings: TtsSettings,
    private val pcmPlayer: PcmAudioPlayer,
    private val androidTts: SystemTtsSpeaker,
    private val logManager: LogManager? = null
) {
    private val log = logManager?.getLogger(TAG)

    /**
     * 文字合成语音并播放。
     *
     * @param text 要说的文字（自动经 forSpeech 清洗）
     * @param onPlayStarted 播放开始回调（可选，用于 UI 状态同步）
     * @param onPlayEnded 播放结束回调（可选，用于继续听等）
     * @return [SpeakResult] 各环节耗时/报错
     */
    suspend fun speak(
        text: String,
        onPlayStarted: (() -> Unit)? = null,
        onPlayEnded: (() -> Unit)? = null
    ): SpeakResult {
        val speechText = LocalReplySanitizer.forSpeech(text, showThinking = false).trim()
        if (speechText.isEmpty()) {
            return SpeakResult(success = false, subtitle = "", synthError = "empty_after_sanitize")
        }

        // === 环节 1：ensure TTS ready ===
        val t0 = System.currentTimeMillis()
        val (ttsReady, loadErr) = ensureTtsReady()
        val ttsLoadDurMs = System.currentTimeMillis() - t0
        if (!ttsReady) {
            val err = loadErr ?: ttsEngine.lastError ?: "tts_not_ready"
            log?.w("speak: tts not ready ttsLoadDur=${ttsLoadDurMs}ms error=$err")
            return SpeakResult(
                success = false, subtitle = speechText,
                ttsLoadDurMs = ttsLoadDurMs, ttsLoadError = err
            )
        }

        // === 环节 2：synthesize ===
        val t1 = System.currentTimeMillis()
        val synth = runCatching {
            ttsEngine.synthesize(TtsSynthesizeRequest(text = speechText))
        }.getOrElse { e ->
            val dur = System.currentTimeMillis() - t1
            log?.w("speak: synthesize failed dur=${dur}ms error=${e.message}")
            return SpeakResult(
                success = false, subtitle = speechText,
                ttsLoadDurMs = ttsLoadDurMs,
                synthDurMs = dur, synthError = e.message
            )
        }
        val synthDurMs = System.currentTimeMillis() - t1

        if (synth.pcm16leMono.isEmpty()) {
            log?.i("speak: synthesize empty pcm (stub=${synth.isStub}), trying Android TTS fallback")
            // 回退到 Android 系统 TTS（不依赖离线模型）
            val fallbackOk = androidTts.speak(speechText)
            if (fallbackOk) {
                log?.i("speak: Android TTS fallback OK text=${speechText.take(48)}")
                return SpeakResult(
                    success = true,
                    subtitle = speechText,
                    isStub = true,
                    ttsLoadDurMs = ttsLoadDurMs,
                    synthDurMs = synthDurMs,
                    synthError = null
                )
            }
            log?.w("speak: Android TTS fallback also failed")
            return SpeakResult(
                success = true,
                subtitle = speechText,
                isStub = synth.isStub,
                ttsLoadDurMs = ttsLoadDurMs,
                synthDurMs = synthDurMs,
                synthError = if (synth.isStub) null else "empty_pcm"
            )
        }

        val subtitle = LocalReplySanitizer.forSpeech(
            synth.subtitle.ifBlank { speechText },
            showThinking = false
        )

        // === 环节 3：play ===
        onPlayStarted?.invoke()
        val t2 = System.currentTimeMillis()
        val playResult = runCatching {
            pcmPlayer.play(synth.pcm16leMono, synth.sampleRateHz)
        }.getOrElse { e ->
            log?.w("speak: play failed error=${e.message}")
            Result.failure(e)
        }
        val playDurMs = System.currentTimeMillis() - t2
        onPlayEnded?.invoke()

        if (playResult.isFailure) {
            return SpeakResult(
                success = false, subtitle = subtitle,
                ttsLoadDurMs = ttsLoadDurMs,
                synthDurMs = synthDurMs,
                playDurMs = playDurMs,
                playError = playResult.exceptionOrNull()?.message
            )
        }

        log?.i("speak done synthDur=${synthDurMs}ms playDur=${playDurMs}ms text=${speechText.take(48)}")
        return SpeakResult(
            success = true, subtitle = subtitle, isStub = synth.isStub,
            ttsLoadDurMs = ttsLoadDurMs,
            synthDurMs = synthDurMs,
            playDurMs = playDurMs
        )
    }

    /**
     * 确保 TTS 就绪，返回 (ready, errorOrNull)。
     *
     * 无模型路径时仍尝试 load：stub 不需要真实文件；真引擎失败后回 lastError / no_tts_model。
     */
    private suspend fun ensureTtsReady(): Pair<Boolean, String?> {
        if (ttsEngine.isReady) return Pair(true, null)
        val config = ttsSettings.getConfig()
        val toLoad = if (config.enabled) config else config.copy(enabled = true)
        return runCatching {
            val loaded = ttsEngine.load(toLoad)
            if (loaded && !config.enabled) {
                ttsSettings.setEnabled(true)
            }
            if (loaded && ttsEngine.isReady) {
                Pair(true, null)
            } else {
                val err = ttsEngine.lastError
                    ?: if (config.modelDir.isBlank() && config.modelPath.isBlank()) {
                        "no_tts_model"
                    } else {
                        "tts_not_ready"
                    }
                Pair(false, err)
            }
        }.getOrElse { e ->
            Pair(false, e.message)
        }
    }

    companion object {
        private const val TAG = "VoiceOutPLC"
    }
}

/**
 * 输出流水线结果。
 *
 * @property ttsLoadDurMs TTS 加载/就绪检查耗时（ms）
 * @property ttsLoadError TTS 加载报错，null=正常
 * @property synthDurMs 合成耗时（ms）
 * @property synthError 合成报错，null=正常
 * @property playDurMs 播放耗时（ms）
 * @property playError 播放报错，null=正常
 */
data class SpeakResult(
    val success: Boolean,
    val subtitle: String,
    val error: String? = null,
    val isStub: Boolean = false,
    val ttsLoadDurMs: Long = 0L,
    val ttsLoadError: String? = null,
    val synthDurMs: Long = 0L,
    val synthError: String? = null,
    val playDurMs: Long = 0L,
    val playError: String? = null
)
