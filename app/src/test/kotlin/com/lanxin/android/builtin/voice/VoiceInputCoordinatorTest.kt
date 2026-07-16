package com.lanxin.android.builtin.voice

import com.lanxin.android.builtin.voice.data.PcmAudioRecorder
import com.lanxin.android.builtin.voice.data.SherpaOnnxBridge
import com.lanxin.android.builtin.voice.data.StubAsrEngine
import com.lanxin.android.builtin.voice.domain.AsrConfig
import com.lanxin.android.builtin.voice.domain.AsrSettings
import com.lanxin.android.builtin.voice.domain.MicPermissionChecker
import com.lanxin.android.builtin.voice.domain.MicPermissionState
import com.lanxin.android.builtin.voice.domain.VoiceInputCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceInputCoordinatorTest {

    private class FakeSettings(
        var config: AsrConfig = AsrConfig()
    ) : AsrSettings {
        override suspend fun getConfig(): AsrConfig = config
        override suspend fun setEnabled(enabled: Boolean) {
            config = config.copy(enabled = enabled)
        }
        override suspend fun setModelPath(path: String?) {
            config = config.copy(modelPath = path.orEmpty())
        }
        override suspend fun setLanguage(language: String) {
            config = config.copy(language = language)
        }
        override suspend fun setSampleRateHz(sampleRateHz: Int) {
            config = config.copy(sampleRateHz = sampleRateHz)
        }
    }

    private fun checker(state: MicPermissionState) = MicPermissionChecker { state }

    @Test
    fun `transcribePcm fails when disabled`() = runBlocking {
        val engine = StubAsrEngine(SherpaOnnxBridge())
        val settings = FakeSettings(AsrConfig(enabled = false, modelPath = "stub://m"))
        val c = VoiceInputCoordinator(engine, settings, checker(MicPermissionState.GRANTED))
        val result = c.transcribePcm(ByteArray(16))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("未启用"))
    }

    @Test
    fun `transcribePcm succeeds when ready`() = runBlocking {
        val engine = StubAsrEngine(SherpaOnnxBridge())
        val settings = FakeSettings(
            AsrConfig(enabled = true, modelPath = "stub://demo", language = "zh")
        )
        engine.load(settings.getConfig())
        val c = VoiceInputCoordinator(engine, settings, checker(MicPermissionState.GRANTED))
        val audio = PcmAudioRecorder().recordStubPcm(durationMs = 100)
        val result = c.transcribePcm(audio.pcm16leMono, audio.sampleRateHz)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.text.contains("[asr-stub]"))
    }

    @Test
    fun `preflight blocks without mic`() = runBlocking {
        val engine = StubAsrEngine(SherpaOnnxBridge())
        val settings = FakeSettings(
            AsrConfig(enabled = true, modelPath = "stub://demo")
        )
        engine.load(settings.getConfig())
        val c = VoiceInputCoordinator(engine, settings, checker(MicPermissionState.DENIED))
        val msg = c.preflightForRecording()
        assertNotNull(msg)
        assertTrue(msg!!.contains("麦克风"))
    }

    @Test
    fun `preflight ok when ready and granted`() = runBlocking {
        val engine = StubAsrEngine(SherpaOnnxBridge())
        val settings = FakeSettings(
            AsrConfig(enabled = true, modelPath = "stub://demo")
        )
        engine.load(settings.getConfig())
        val c = VoiceInputCoordinator(engine, settings, checker(MicPermissionState.GRANTED))
        assertNull(c.preflightForRecording())
    }

    @Test
    fun `previewStatus includes fields`() = runBlocking {
        val engine = StubAsrEngine(SherpaOnnxBridge())
        val settings = FakeSettings(AsrConfig(enabled = true, modelPath = "stub://x", language = "en"))
        engine.load(settings.getConfig())
        val c = VoiceInputCoordinator(engine, settings, checker(MicPermissionState.GRANTED))
        val preview = c.previewStatus()
        assertTrue(preview.contains("enabled=true"))
        assertTrue(preview.contains("ready=true"))
        assertTrue(preview.contains("lang=en"))
        assertTrue(preview.contains("mic=GRANTED"))
        assertFalse(preview.contains("err="))
    }
}
