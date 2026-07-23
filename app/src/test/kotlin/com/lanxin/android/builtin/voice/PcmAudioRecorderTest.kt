package com.lanxin.android.builtin.voice

import com.lanxin.android.builtin.voice.data.PcmAudioRecorder
import com.lanxin.android.builtin.voice.domain.AsrConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmAudioRecorderTest {

    @Test
    fun `stub pcm non empty and size matches duration`() = runBlocking {
        val recorder = PcmAudioRecorder()
        val audio = recorder.recordStubPcm(durationMs = 500, sampleRateHz = 16_000)
        assertEquals(16_000, audio.sampleRateHz)
        assertEquals(500L, audio.durationMs)
        val expectedSamples = (16_000 * 500) / 1000
        assertEquals(expectedSamples * 2, audio.pcm16leMono.size)
        assertTrue(audio.pcm16leMono.isNotEmpty())
    }

    @Test
    fun `defaults use asr sample rate`() = runBlocking {
        val audio = PcmAudioRecorder().recordStubPcm()
        assertEquals(AsrConfig.DEFAULT_SAMPLE_RATE_HZ, audio.sampleRateHz)
    }

    @Test
    fun `start stop stub hardware yields pcm`() = runBlocking {
        val recorder = PcmAudioRecorder().apply { forceStubHardware = true }
        val start = recorder.startRecording(sampleRateHz = 16_000)
        assertTrue(start.isSuccess)
        assertTrue(recorder.isRecording())
        delay(120)
        val stop = recorder.stopRecording()
        assertTrue(stop.isSuccess)
        val audio = stop.getOrThrow()
        assertFalse(recorder.isRecording())
        assertEquals(16_000, audio.sampleRateHz)
        assertTrue(audio.pcm16leMono.isNotEmpty())
        assertTrue(audio.durationMs >= 50L)
    }

    @Test
    fun `double start fails`() = runBlocking {
        val recorder = PcmAudioRecorder().apply { forceStubHardware = true }
        assertTrue(recorder.startRecording().isSuccess)
        assertTrue(recorder.startRecording().isFailure)
        recorder.cancelRecording()
    }

    @Test
    fun `stop without start fails`() = runBlocking {
        val recorder = PcmAudioRecorder().apply { forceStubHardware = true }
        assertTrue(recorder.stopRecording().isFailure)
    }

    @Test
    fun `cancel releases recording flag`() = runBlocking {
        val recorder = PcmAudioRecorder().apply { forceStubHardware = true }
        assertTrue(recorder.startRecording().isSuccess)
        recorder.cancelRecording()
        assertFalse(recorder.isRecording())
    }

    @Test
    fun `awaitCaptureHeartbeat is null in stub mode`() = runBlocking {
        val recorder = PcmAudioRecorder().apply { forceStubHardware = true }
        assertTrue(recorder.startRecording().isSuccess)
        // stub 不碰硬件，不应报「无数据」
        assertEquals(null, recorder.awaitCaptureHeartbeat(timeoutMs = 100))
        recorder.cancelRecording()
    }

    @Test
    fun `custom factory is preferred over multi-source fallback`() = runBlocking {
        var factoryCalls = 0
        val recorder = PcmAudioRecorder().apply {
            forceStubHardware = false
            audioRecordFactory = { _, _ ->
                factoryCalls++
                null // 返回 null → start 应失败（工厂优先，不走默认多源）
            }
        }
        val start = recorder.startRecording(sampleRateHz = 16_000)
        assertTrue(start.isFailure)
        assertEquals(1, factoryCalls)
        assertFalse(recorder.isRecording())
    }
}
