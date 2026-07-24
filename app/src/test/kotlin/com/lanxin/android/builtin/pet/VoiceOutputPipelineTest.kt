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

package com.lanxin.android.builtin.pet

import com.lanxin.android.builtin.pet.domain.SpeakResult
import com.lanxin.android.builtin.pet.domain.VoiceOutputPipeline
import com.lanxin.android.builtin.voice.data.PcmAudioPlayer
import com.lanxin.android.builtin.voice.data.StubTtsEngine
import com.lanxin.android.builtin.voice.domain.TtsConfig
import com.lanxin.android.builtin.voice.domain.TtsEngine
import com.lanxin.android.builtin.voice.domain.TtsEngineState
import com.lanxin.android.builtin.voice.domain.TtsSettings
import com.lanxin.android.builtin.voice.domain.TtsSynthesizeRequest
import com.lanxin.android.builtin.voice.domain.TtsSynthesizeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VoiceOutputPipeline 单测：验证输出链三个环节（load→synth→play）与监控字段。
 */
class VoiceOutputPipelineTest {

    // ── 正常 stub（可 load，返回空 PCM + isStub=true） ──

    @Test
    fun `speak with pre-loaded stub returns success=true and isStub=true`() = runBlocking {
        val tts = StubTtsEngine()
        tts.load(TtsConfig(enabled = true))
        val pipe = VoiceOutputPipeline(tts, FakeTtsSettings(), PcmAudioPlayer())
        val r = pipe.speak("你好，兰心")
        assertTrue(r.success)
        assertTrue(r.isStub)
        assertNull(r.ttsLoadError)
        assertNull(r.synthError)
        assertNull(r.playError)
        assertTrue(r.synthDurMs >= 0)
        assertEquals("你好，兰心", r.subtitle)
    }

    @Test
    fun `speak does auto-load when engine is idle`() = runBlocking {
        val tts = StubTtsEngine()
        val pipe = VoiceOutputPipeline(
            tts, FakeTtsSettings(TtsConfig(enabled = true)), PcmAudioPlayer()
        )
        val r = pipe.speak("测试文本")
        assertTrue(r.success)
        assertTrue(r.isStub)
        // 触发过 load
        assertNull(r.ttsLoadError)
        assertTrue(r.ttsLoadDurMs >= 0)
    }

    @Test
    fun `speak with empty text returns empty_after_sanitize`() = runBlocking {
        val tts = StubTtsEngine()
        tts.load(TtsConfig(enabled = true))
        val pipe = VoiceOutputPipeline(tts, FakeTtsSettings(), PcmAudioPlayer())
        val r = pipe.speak("")
        assertEquals(false, r.success)
        assertEquals("empty_after_sanitize", r.synthError)
    }

    @Test
    fun `speak with only tags returns empty_after_sanitize`() = runBlocking {
        val tts = StubTtsEngine()
        tts.load(TtsConfig(enabled = true))
        val pipe = VoiceOutputPipeline(tts, FakeTtsSettings(), PcmAudioPlayer())
        val r = pipe.speak("[[mood=joy]]\n*笑*")
        assertEquals(false, r.success)
        assertEquals("empty_after_sanitize", r.synthError)
    }

    @Test
    fun `speak calls onPlayStarted and onPlayEnded`() = runBlocking {
        val tts = StubTtsEngine()
        tts.load(TtsConfig(enabled = true))
        val pipe = VoiceOutputPipeline(tts, FakeTtsSettings(), PcmAudioPlayer())
        var started = false
        var ended = false
        pipe.speak("铃响", onPlayStarted = { started = true }, onPlayEnded = { ended = true })
        assertTrue(started)
        assertTrue(ended)
    }

    // ── 引擎无法 load（stub 不防，用特殊 FakeTtsSettings 阻止 load） ──

    @Test
    fun `speak when tts load fails returns error`() = runBlocking {
        val tts = FailingTtsEngine()
        val pipe = VoiceOutputPipeline(tts, FakeTtsSettings(TtsConfig(enabled = true)), PcmAudioPlayer())
        val r = pipe.speak("你好")
        assertEquals(false, r.success)
        assertNotNull(r.ttsLoadError)
        assertTrue(r.ttsLoadDurMs >= 0)
    }

    @Test
    fun `speak when synthesize fails returns synthError`() = runBlocking {
        val tts = FailingSynthTtsEngine()
        tts.load(TtsConfig(enabled = true))
        val pipe = VoiceOutputPipeline(tts, FakeTtsSettings(), PcmAudioPlayer())
        val r = pipe.speak("你好")
        assertEquals(false, r.success)
        assertNotNull(r.synthError)
    }

    @Test
    fun `speak when play fails returns playError`() = runBlocking {
        val tts = PcmTtsEngine()
        tts.load(TtsConfig(enabled = true))
        // PcmAudioPlayer 在 JVM 上 play 会抛异常
        val pipe = VoiceOutputPipeline(tts, FakeTtsSettings(), PcmAudioPlayer())
        val r = pipe.speak("你好")
        assertEquals(false, r.success)
        assertNotNull(r.playError)
        assertTrue(r.playDurMs >= 0)
    }

    // ── Fake / 辅助实现 ──

    private class FakeTtsSettings(
        private var config: TtsConfig = TtsConfig(enabled = true)
    ) : TtsSettings {
        override suspend fun getConfig(): TtsConfig = config
        override suspend fun setEnabled(enabled: Boolean) {
            config = config.copy(enabled = enabled)
        }
        override suspend fun setModelPath(path: String?) { config = config.copy(modelPath = path.orEmpty()) }
        override suspend fun setModelDir(path: String?) { config = config.copy(modelDir = path.orEmpty()) }
        override suspend fun setReferenceAudio(path: String?) { config = config.copy(referenceAudio = path.orEmpty()) }
        override suspend fun setVoiceId(voiceId: String) { config = config.copy(voiceId = voiceId) }
    }

    /** load 必失败，检验 ensureTtsReady 报错路径。 */
    private class FailingTtsEngine : TtsEngine {
        private val _state = MutableStateFlow(TtsEngineState.DISABLED)
        override val state: StateFlow<TtsEngineState> = _state.asStateFlow()
        override val isReady: Boolean = false
        override val isAvailable: Boolean = true
        override val lastError: String? = "simulated_load_fail"
        override suspend fun load(config: TtsConfig): Boolean = false
        override suspend fun unload() { _state.value = TtsEngineState.DISABLED }
        override suspend fun synthesize(request: TtsSynthesizeRequest): TtsSynthesizeResult {
            error("should_not_reach")
        }
    }

    /** synthesize 抛异常。 */
    private class FailingSynthTtsEngine : TtsEngine {
        private val _state = MutableStateFlow(TtsEngineState.READY)
        override val state: StateFlow<TtsEngineState> = _state.asStateFlow()
        override val isReady: Boolean = true
        override val isAvailable: Boolean = true
        override val lastError: String? = null
        override suspend fun load(config: TtsConfig): Boolean {
            _state.value = TtsEngineState.READY; return true
        }
        override suspend fun unload() { _state.value = TtsEngineState.DISABLED }
        override suspend fun synthesize(request: TtsSynthesizeRequest): TtsSynthesizeResult {
            error("simulated_synth_fail")
        }
    }

    /** 返回非空 PCM 会触发 play 失败（JVM 无 AudioTrack）。 */
    private class PcmTtsEngine : TtsEngine {
        private val _state = MutableStateFlow(TtsEngineState.DISABLED)
        override val state: StateFlow<TtsEngineState> = _state.asStateFlow()
        override val isReady: Boolean get() = _state.value == TtsEngineState.READY
        override val isAvailable: Boolean = true
        override val lastError: String? = null
        override suspend fun load(config: TtsConfig): Boolean {
            _state.value = TtsEngineState.READY; return true
        }
        override suspend fun unload() { _state.value = TtsEngineState.DISABLED }
        override suspend fun synthesize(request: TtsSynthesizeRequest): TtsSynthesizeResult {
            return TtsSynthesizeResult(
                pcm16leMono = ByteArray(640) { 0 },
                sampleRateHz = 16_000,
                durationMs = 20L,
                isStub = false,
                subtitle = request.text
            )
        }
    }
}
