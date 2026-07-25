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
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 播放 16-bit LE mono PCM（TTS 输出）。
 *
 * - 同步阻塞到**真正播完**（[Dispatchers.IO]）
 * - [stop] 可中断当前播放
 * - 空 PCM / stub 结果直接 no-op 成功
 *
 * 注意：MODE_STREAM 下若 buffer ≥ 整段 PCM，write 会瞬间写完；
 * 必须等 playbackHead 走到末尾，不能写完立刻 stop（否则听不到）。
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
        val totalFrames = pcm16leMono.size / 2 // 16-bit mono
        if (totalFrames <= 0) {
            return@withContext Result.success(Unit)
        }
        stopInternal()
        if (!playing.compareAndSet(false, true)) {
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
        // 流式缓冲：不要一次开到整段大小，否则 write 瞬间完成、听感被 stop 掐断
        val bufferSize = maxOf(minBuf * 2, minBuf)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // MEDIA 比 ASSISTANT 在更多机型上能真正出声
                        .setUsage(AudioAttributes.USAGE_MEDIA)
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
                .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
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
        val expectedMs = (totalFrames * 1000L) / rate
        Log.i(
            TAG,
            "play start bytes=${pcm16leMono.size} frames=$totalFrames rate=$rate " +
                "expectedMs=$expectedMs buf=$bufferSize"
        )
        try {
            // 部分机型需要先设音量
            runCatching { track.setVolume(1.0f) }
            track.play()
            var offset = 0
            while (offset < pcm16leMono.size && playing.get()) {
                val toWrite = (pcm16leMono.size - offset).coerceAtMost(minBuf)
                val n = track.write(pcm16leMono, offset, toWrite)
                if (n < 0) {
                    Log.w(TAG, "write error code=$n at offset=$offset")
                    break
                }
                if (n == 0) {
                    // 缓冲满：稍等再写
                    Thread.sleep(10)
                    continue
                }
                offset += n
            }
            // 关键：等播放头走到末尾（或超时），禁止写完立刻 stop
            if (playing.get() && offset > 0) {
                waitUntilPlayed(track, totalFrames, rate)
            }
            if (playing.get()) {
                runCatching { track.stop() }
            }
            val playedMs = if (rate > 0) {
                (track.playbackHeadPosition.coerceAtLeast(0) * 1000L) / rate
            } else {
                0L
            }
            Log.i(TAG, "play end wrote=$offset/${pcm16leMono.size} headMs~$playedMs expectedMs=$expectedMs")
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, "play failed", t)
            Result.failure(IllegalStateException("播放失败：${t.message}"))
        } finally {
            runCatching { track.release() }
            if (activeTrack === track) activeTrack = null
            playing.set(false)
        }
    }

    /**
     * 阻塞直到 playbackHead 接近 totalFrames，或超过音频时长 + 宽限。
     * stop() 会重置 head，因此必须在 stop 前等待。
     */
    private fun waitUntilPlayed(track: AudioTrack, totalFrames: Int, rate: Int) {
        val durationMs = ((totalFrames * 1000L) / rate.coerceAtLeast(1)) + 400L
        val deadline = System.currentTimeMillis() + durationMs.coerceAtLeast(200L)
        val target = (totalFrames - 1).coerceAtLeast(0)
        while (playing.get() && System.currentTimeMillis() < deadline) {
            val head = try {
                track.playbackHeadPosition
            } catch (_: Throwable) {
                -1
            }
            if (head >= target) break
            // 若已暂停/停止则退出
            val st = try {
                track.playState
            } catch (_: Throwable) {
                AudioTrack.PLAYSTATE_STOPPED
            }
            if (st != AudioTrack.PLAYSTATE_PLAYING && st != AudioTrack.PLAYSTATE_PAUSED) {
                // 仍可能刚 play，给一次机会
                if (head <= 0) {
                    Thread.sleep(20)
                    continue
                }
                break
            }
            Thread.sleep(20)
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

    companion object {
        private const val TAG = "PcmAudioPlayer"
    }
}
