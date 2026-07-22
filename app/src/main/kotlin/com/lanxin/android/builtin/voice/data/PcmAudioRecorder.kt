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

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.lanxin.android.builtin.voice.domain.AsrConfig
import com.lanxin.android.builtin.voice.domain.RecordedAudio
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 录音 → PCM 链路。
 *
 * - [recordStubPcm]：设置页「试转写」与 CI，不打开麦克风
 * - [startRecording] / [stopRecording] / [cancelRecording]：Chat 麦按钮真机录音
 *
 * 产品约束：不在后台偷偷录音；仅由用户显式触发；停麦立即 release。
 *
 * 单测可注入 [audioRecordFactory] 与 [forceStubHardware] 绕过真硬件。
 */
@Singleton
class PcmAudioRecorder @Inject constructor() {

    /**
     * 真机 AudioRecord 工厂；单测可替换为 null 触发 stub 硬件路径。
     */
    @Volatile
    var audioRecordFactory: ((sampleRateHz: Int, bufferSize: Int) -> AudioRecord?)? =
        DEFAULT_AUDIO_RECORD_FACTORY

    /**
     * 为 true 时 [startRecording] 使用内存 stub 流（不触碰硬件）。
     * 默认 false；JVM 单测设为 true。
     */
    @Volatile
    var forceStubHardware: Boolean = false

    private val recording = AtomicBoolean(false)
    private var activeRecord: AudioRecord? = null
    private var captureBuffer: ByteArrayOutputStream? = null
    private var captureSampleRate: Int = AsrConfig.DEFAULT_SAMPLE_RATE_HZ
    private var captureStartedAtMs: Long = 0L
    private var captureThread: Thread? = null
    private var stubMode: Boolean = false

    /**
     * 流式 PCM chunk 回调（捕获线程调用）。
     * 设为 null 时仅整段缓冲；VoiceChat 流式 ASR 时注入。
     */
    @Volatile
    var onPcmChunk: ((ByteArray) -> Unit)? = null

    /**
     * 生成一段可预测的 stub PCM（静音帧），不触碰硬件麦克风。
     *
     * @param durationMs 时长
     * @param sampleRateHz 采样率
     */
    suspend fun recordStubPcm(
        durationMs: Long = DEFAULT_STUB_DURATION_MS,
        sampleRateHz: Int = AsrConfig.DEFAULT_SAMPLE_RATE_HZ
    ): RecordedAudio = withContext(Dispatchers.Default) {
        val clampedMs = durationMs.coerceIn(50L, 10_000L)
        val rate = sampleRateHz.coerceIn(
            AsrConfig.MIN_SAMPLE_RATE_HZ,
            AsrConfig.MAX_SAMPLE_RATE_HZ
        )
        // 模拟采集耗时（短）
        delay(5)
        val samples = ((rate * clampedMs) / 1000L).toInt().coerceAtLeast(1)
        // 16-bit mono → 2 bytes/sample；填入极低幅值正弦近似（非纯 0，便于校验非空）
        val pcm = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val sample = ((i % 32) - 16).toShort()
            pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = (sample.toInt() shr 8).toByte()
        }
        RecordedAudio(
            pcm16leMono = pcm,
            sampleRateHz = rate,
            durationMs = clampedMs
        )
    }

    /**
     * 开始录音（真机 AudioRecord，或 stub 硬件路径）。
     *
     * 调用前须已获 RECORD_AUDIO。重复 start 返回 failure。
     * 权限由 ChatMicSession / MicPermissionGate 显式校验；lint 无法跨层分析。
     */
    @SuppressLint("MissingPermission")
    suspend fun startRecording(
        sampleRateHz: Int = AsrConfig.DEFAULT_SAMPLE_RATE_HZ
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!recording.compareAndSet(false, true)) {
            return@withContext Result.failure(IllegalStateException("已在录音中"))
        }
        val rate = sampleRateHz.coerceIn(
            AsrConfig.MIN_SAMPLE_RATE_HZ,
            AsrConfig.MAX_SAMPLE_RATE_HZ
        )
        captureSampleRate = rate
        captureStartedAtMs = System.currentTimeMillis()
        captureBuffer = ByteArrayOutputStream()
        stubMode = forceStubHardware || audioRecordFactory == null

        if (stubMode) {
            // stub：不占硬件；PCM 在 stop 时按时长合成
            return@withContext Result.success(Unit)
        }

        val minBuf = AudioRecord.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            resetCaptureState()
            return@withContext Result.failure(
                IllegalStateException("设备不支持 $rate Hz PCM 录音")
            )
        }
        val bufferSize = (minBuf * 2).coerceAtLeast(minBuf)
        val record = try {
            audioRecordFactory?.invoke(rate, bufferSize)
        } catch (t: Throwable) {
            resetCaptureState()
            return@withContext Result.failure(
                IllegalStateException("打开麦克风失败：${t.message ?: t.javaClass.simpleName}")
            )
        }
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { record?.release() }
            resetCaptureState()
            return@withContext Result.failure(
                IllegalStateException("无法初始化麦克风，请检查 RECORD_AUDIO 权限。")
            )
        }
        try {
            record.startRecording()
        } catch (t: Throwable) {
            runCatching { record.release() }
            resetCaptureState()
            return@withContext Result.failure(
                IllegalStateException("开始录音失败：${t.message ?: t.javaClass.simpleName}")
            )
        }
        activeRecord = record
        val localBuffer = captureBuffer!!
        val stopFlag = recording
        val chunkListener = onPcmChunk
        captureThread = Thread(
            {
                val chunk = ByteArray(bufferSize)
                while (stopFlag.get()) {
                    val n = try {
                        record.read(chunk, 0, chunk.size)
                    } catch (_: Throwable) {
                        break
                    }
                    if (n > 0) {
                        synchronized(localBuffer) {
                            localBuffer.write(chunk, 0, n)
                        }
                        if (chunkListener != null) {
                            val copy = chunk.copyOf(n)
                            runCatching { chunkListener.invoke(copy) }
                        }
                    } else if (n < 0) {
                        break
                    }
                }
            },
            "lanxin-pcm-capture"
        ).also {
            it.isDaemon = true
            it.start()
        }
        Result.success(Unit)
    }

    /**
     * 停止录音并返回 PCM；释放麦克风。
     *
     * 未在录音时返回 failure。
     */
    suspend fun stopRecording(): Result<RecordedAudio> = withContext(Dispatchers.IO) {
        if (!recording.get()) {
            return@withContext Result.failure(IllegalStateException("当前未在录音"))
        }
        // 先翻标志，让捕获线程退出
        recording.set(false)
        val durationMs = (System.currentTimeMillis() - captureStartedAtMs)
            .coerceAtLeast(0L)
            .coerceAtMost(MAX_RECORD_MS)
        val rate = captureSampleRate

        if (stubMode) {
            val audio = synthesizeStubPcm(durationMs.coerceAtLeast(50L), rate)
            resetCaptureState()
            return@withContext Result.success(audio)
        }

        val record = activeRecord
        // 给捕获线程一点时间刷完
        withTimeoutOrNull(500L) {
            captureThread?.join(400)
        }
        try {
            record?.stop()
        } catch (_: Throwable) {
            // ignore
        }
        try {
            record?.release()
        } catch (_: Throwable) {
            // ignore
        }
        val pcm = synchronized(captureBuffer ?: ByteArrayOutputStream()) {
            captureBuffer?.toByteArray() ?: ByteArray(0)
        }
        resetCaptureState()
        Result.success(
            RecordedAudio(
                pcm16leMono = pcm,
                sampleRateHz = rate,
                durationMs = durationMs
            )
        )
    }

    /**
     * 取消录音并释放资源，不产出音频。
     */
    suspend fun cancelRecording() = withContext(Dispatchers.IO) {
        if (!recording.getAndSet(false) && activeRecord == null && captureBuffer == null) {
            return@withContext
        }
        val record = activeRecord
        withTimeoutOrNull(300L) {
            captureThread?.join(250)
        }
        try {
            record?.stop()
        } catch (_: Throwable) {
        }
        try {
            record?.release()
        } catch (_: Throwable) {
        }
        resetCaptureState()
    }

    /** 当前是否正在录音。 */
    fun isRecording(): Boolean = recording.get()

    private fun resetCaptureState() {
        recording.set(false)
        activeRecord = null
        captureBuffer = null
        captureThread = null
        stubMode = false
        captureStartedAtMs = 0L
    }

    private fun synthesizeStubPcm(durationMs: Long, sampleRateHz: Int): RecordedAudio {
        val clampedMs = durationMs.coerceIn(50L, MAX_RECORD_MS)
        val samples = ((sampleRateHz * clampedMs) / 1000L).toInt().coerceAtLeast(1)
        val pcm = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val sample = ((i % 32) - 16).toShort()
            pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = (sample.toInt() shr 8).toByte()
        }
        return RecordedAudio(
            pcm16leMono = pcm,
            sampleRateHz = sampleRateHz,
            durationMs = clampedMs
        )
    }

    companion object {
        const val DEFAULT_STUB_DURATION_MS = 500L
        const val MAX_RECORD_MS = 60_000L

        val DEFAULT_AUDIO_RECORD_FACTORY: (Int, Int) -> AudioRecord? =
            { rate, bufferSize -> createDefaultAudioRecord(rate, bufferSize) }

        /**
         * 创建默认 AudioRecord。
         * RECORD_AUDIO 由 MicPermissionGate / UI 在 [startRecording] 前校验。
         */
        @SuppressLint("MissingPermission")
        private fun createDefaultAudioRecord(rate: Int, bufferSize: Int): AudioRecord =
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
    }
}
