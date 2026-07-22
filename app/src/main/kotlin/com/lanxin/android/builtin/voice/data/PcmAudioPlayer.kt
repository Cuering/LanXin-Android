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

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 播放 16-bit LE mono PCM（TTS 输出）。
 *
 * - 同步阻塞到播完（[Dispatchers.IO]）
 * - [stop] 可中断当前播放
 * - 空 PCM / stub 结果直接 no-op 成功
 */
@Singleton
class PcmAudioPlayer @Inject constructor() {

    private val playing = AtomicBoolean(false)

    @Volatile
    private var activeTrack: AudioTrack? = null

    /** 当前是否在播放。 */
    fun isPlaying(): Boolean = playing.get()

    /**
     * 播放 PCM；播完或 [stop] 后返回。
     *
     * @return Result.success 正常结束；failure 初始化失败
     */
    suspend fun play(
        pcm16leMono: ByteArray,
        sampleRateHz: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (pcm16leMono.isEmpty()) {
            return@withContext Result.success(Unit)
        }
        val rate = sampleRateHz.coerceIn(8_000, 48_000)
        stopInternal()
        if (!playing.compareAndSet(false, true)) {
            // 抢占：先停再播
            stopInternal()
            playing.set(true)
        }
        val minBuf = AudioTrack.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            playing.set(false)
            return@withContext Result.failure(
                IllegalStateException("设备不支持 $rate Hz PCM 播放")
            )
        }
        val bufferSize = maxOf(minBuf, pcm16leMono.size).coerceAtLeast(minBuf)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (t: Throwable) {
            playing.set(false)
            return@withContext Result.failure(
                IllegalStateException("创建 AudioTrack 失败：${t.message}")
            )
        }
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { track.release() }
            playing.set(false)
            return@withContext Result.failure(
                IllegalStateException("AudioTrack 未初始化")
            )
        }
        activeTrack = track
        try {
            track.play()
            var offset = 0
            while (offset < pcm16leMono.size && playing.get()) {
                val n = track.write(
                    pcm16leMono,
                    offset,
                    (pcm16leMono.size - offset).coerceAtMost(minBuf)
                )
                if (n < 0) break
                offset += n
            }
            // 等缓冲区播完
            if (playing.get()) {
                runCatching { track.stop() }
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(IllegalStateException("播放失败：${t.message}"))
        } finally {
            runCatching { track.release() }
            if (activeTrack === track) activeTrack = null
            playing.set(false)
        }
    }

    /** 停止当前播放。 */
    fun stop() {
        playing.set(false)
        stopInternal()
    }

    private fun stopInternal() {
        val track = activeTrack
        activeTrack = null
        if (track != null) {
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.stop() }
            runCatching { track.release() }
        }
        playing.set(false)
    }
}
