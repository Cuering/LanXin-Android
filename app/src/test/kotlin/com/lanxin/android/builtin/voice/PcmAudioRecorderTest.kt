package com.lanxin.android.builtin.voice

import com.lanxin.android.builtin.voice.data.PcmAudioRecorder
import com.lanxin.android.builtin.voice.domain.AsrConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
}
