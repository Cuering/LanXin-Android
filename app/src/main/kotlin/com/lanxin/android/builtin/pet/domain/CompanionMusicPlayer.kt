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

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * 陪伴页背景音乐：MediaPlayer + Visualizer → 节拍回调。
 *
 * 页内播放优先；[release] 在离开页时调用。
 */
class CompanionMusicPlayer(
    private val appContext: Context,
    private val onBeat: (level01: Float) -> Unit,
    private val onState: (MusicPlayerState) -> Unit = {}
) {
    data class MusicPlayerState(
        val playing: Boolean = false,
        val path: String = "",
        val title: String = "",
        val error: String? = null,
        val trackIndex: Int = -1,
        val trackCount: Int = 0,
        val volume01: Float = 0.7f
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    private var tracks: List<File> = emptyList()
    private var index: Int = -1
    private var smoothLevel = 0f
    private val released = AtomicBoolean(false)
    private var beatEnabled = true
    private var startedAtMs = 0L
    /** 0..1 媒体音量（MediaPlayer 左右声道）。 */
    private var volume01: Float = 0.7f

    private val beatPoll = object : Runnable {
        override fun run() {
            if (released.get()) return
            val p = player
            if (p != null && p.isPlaying && beatEnabled) {
                // Visualizer 回调为主；此处仅作无 capture 时的兜底脉冲
                if (visualizer == null) {
                    val level = MusicBeatAnalyzer.fallbackPulse(
                        System.currentTimeMillis() - startedAtMs,
                        smoothLevel
                    )
                    smoothLevel = level
                    onBeat(level)
                }
            } else if (smoothLevel > 0.01f) {
                smoothLevel *= 0.85f
                onBeat(smoothLevel)
            }
            mainHandler.postDelayed(this, 50L)
        }
    }

    fun setBeatEnabled(enabled: Boolean) {
        beatEnabled = enabled
        if (!enabled) {
            smoothLevel = 0f
            onBeat(0f)
        }
    }

    fun setVolume(level01: Float) {
        volume01 = level01.coerceIn(0f, 1f)
        runCatching {
            player?.setVolume(volume01, volume01)
        }
        emitState()
    }

    fun currentVolume(): Float = volume01

    fun refreshPlaylist(): List<File> {
        BuiltInMusicAssets.ensureTestTrackInstalled(appContext)
        val dir = BuiltInMusicAssets.musicDirFromStorage(appContext)
        tracks = BuiltInMusicAssets.listTracks(dir)
        emitState()
        return tracks
    }

    fun currentTracks(): List<File> = tracks

    fun musicDirPath(): String = BuiltInMusicAssets.musicDirFromStorage(appContext).absolutePath

    fun playPath(path: String): Boolean {
        val file = File(path)
        if (!BuiltInMusicAssets.isAudioFile(file)) {
            onState(
                MusicPlayerState(
                    playing = false,
                    path = path,
                    title = file.name,
                    error = "不支持的音频文件",
                    trackIndex = index,
                    trackCount = tracks.size,
                    volume01 = volume01
                )
            )
            return false
        }
        ensureInPlaylist(file)
        return startPlayer(file)
    }

    fun playIndex(i: Int): Boolean {
        if (tracks.isEmpty()) refreshPlaylist()
        if (tracks.isEmpty() || i !in tracks.indices) return false
        return startPlayer(tracks[i])
    }

    fun togglePlayPause() {
        val p = player
        if (p == null) {
            if (tracks.isEmpty()) refreshPlaylist()
            if (tracks.isNotEmpty()) {
                val i = if (index in tracks.indices) index else 0
                playIndex(i)
            }
            return
        }
        try {
            if (p.isPlaying) {
                p.pause()
                emitState(playing = false)
            } else {
                p.start()
                startedAtMs = System.currentTimeMillis()
                emitState(playing = true)
            }
        } catch (e: Exception) {
            emitState(error = e.message)
        }
    }

    fun next() {
        if (tracks.isEmpty()) refreshPlaylist()
        if (tracks.isEmpty()) return
        val next = if (index < 0) 0 else (index + 1) % tracks.size
        playIndex(next)
    }

    fun previous() {
        if (tracks.isEmpty()) refreshPlaylist()
        if (tracks.isEmpty()) return
        val prev = if (index <= 0) tracks.lastIndex else index - 1
        playIndex(prev)
    }

    fun stop() {
        runCatching {
            player?.stop()
        }
        releaseVisualizer()
        player?.reset()
        player?.release()
        player = null
        smoothLevel = 0f
        onBeat(0f)
        emitState(playing = false)
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        mainHandler.removeCallbacks(beatPoll)
        stop()
    }

    private fun ensureInPlaylist(file: File) {
        val abs = file.absolutePath
        val existing = tracks.indexOfFirst { it.absolutePath == abs }
        if (existing >= 0) {
            index = existing
            return
        }
        tracks = (tracks + file).sortedBy { it.name.lowercase() }
        index = tracks.indexOfFirst { it.absolutePath == abs }
    }

    private fun startPlayer(file: File): Boolean {
        return try {
            releaseVisualizer()
            player?.reset()
            player?.release()
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(appContext, Uri.fromFile(file))
                isLooping = true
                setOnErrorListener { _, what, extra ->
                    emitState(playing = false, error = "播放失败($what/$extra)")
                    true
                }
                setOnCompletionListener {
                    // looping=true 一般不触发；保留
                    emitState(playing = false)
                }
                setVolume(volume01, volume01)
                prepare()
                start()
            }
            player = mp
            index = tracks.indexOfFirst { it.absolutePath == file.absolutePath }
            startedAtMs = System.currentTimeMillis()
            attachVisualizer(mp.audioSessionId)
            if (!mainHandler.hasCallbacks(beatPoll)) {
                mainHandler.post(beatPoll)
            }
            emitState(playing = true, error = null)
            true
        } catch (e: Exception) {
            emitState(playing = false, error = e.message ?: "无法播放")
            false
        }
    }

    private fun attachVisualizer(sessionId: Int) {
        releaseVisualizer()
        if (sessionId == 0) return
        runCatching {
            val v = Visualizer(sessionId)
            v.captureSize = Visualizer.getCaptureSizeRange()[1]
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (!beatEnabled || released.get()) return
                        val level = MusicBeatAnalyzer.levelFromCapture(
                            waveform = waveform,
                            previous = smoothLevel
                        )
                        smoothLevel = level
                        mainHandler.post { onBeat(level) }
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        // 波形优先；FFT 备用（部分机型波形全零）
                        if (!beatEnabled || released.get()) return
                        if (smoothLevel < 0.05f && fft != null) {
                            val level = MusicBeatAnalyzer.levelFromCapture(
                                waveform = null,
                                fft = fft,
                                previous = smoothLevel
                            )
                            smoothLevel = level
                            mainHandler.post { onBeat(level) }
                        }
                    }
                },
                Visualizer.getMaxCaptureRate() / 2,
                true,
                true
            )
            v.enabled = true
            visualizer = v
        }.onFailure {
            visualizer = null
        }
    }

    private fun releaseVisualizer() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
    }

    private fun emitState(
        playing: Boolean? = null,
        error: String? = null
    ) {
        val p = player
        val isPlaying = playing ?: (p?.isPlaying == true)
        val path = tracks.getOrNull(index)?.absolutePath.orEmpty()
        val title = tracks.getOrNull(index)?.nameWithoutExtension
            ?: tracks.getOrNull(index)?.name.orEmpty()
        onState(
            MusicPlayerState(
                playing = isPlaying,
                path = path,
                title = title,
                error = error,
                trackIndex = index,
                trackCount = tracks.size,
                volume01 = volume01
            )
        )
    }
}

/** 将 0..1 能量格式化为 bridge 用整数字符串（0..100）。 */
fun Float.toBeatWire(): String = (this.coerceIn(0f, 1f) * 100f).roundToInt().toString()
