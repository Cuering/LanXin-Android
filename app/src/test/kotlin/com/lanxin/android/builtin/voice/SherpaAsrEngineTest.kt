package com.lanxin.android.builtin.voice

import com.lanxin.android.builtin.voice.data.SherpaAsrEngine
import com.lanxin.android.builtin.voice.data.SherpaOnnxBridge
import com.lanxin.android.builtin.voice.domain.AsrConfig
import com.lanxin.android.builtin.voice.domain.AsrEngineState
import com.lanxin.android.builtin.voice.domain.AsrTranscribeRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM：无 so 时 [SherpaAsrEngine] 对 stub:// 与合法路径降级 stub，不崩。
 */
class SherpaAsrEngineTest {

    @Before
    fun resetNative() {
        SherpaOnnxBridge.resetNativeLoadStateForTests()
    }

    private fun engine() = SherpaAsrEngine(SherpaOnnxBridge())

    @Test
    fun `load disabled stays DISABLED`() = runBlocking {
        val e = engine()
        val ok = e.load(AsrConfig(enabled = false, modelPath = "stub://x"))
        assertFalse(ok)
        assertEquals(AsrEngineState.DISABLED, e.state.value)
        assertFalse(e.isAvailable)
        assertFalse(e.isReady)
        assertFalse(e.isUsingNative)
    }

    @Test
    fun `load empty path errors`() = runBlocking {
        val e = engine()
        val ok = e.load(AsrConfig(enabled = true, modelPath = "  "))
        assertFalse(ok)
        assertEquals(AsrEngineState.ERROR, e.state.value)
        assertEquals("model_path_empty", e.lastError)
    }

    @Test
    fun `load stub scheme becomes READY without native`() = runBlocking {
        val e = engine()
        val ok = e.load(AsrConfig(enabled = true, modelPath = "stub://demo-asr"))
        assertTrue(ok)
        assertTrue(e.isReady)
        assertTrue(e.isAvailable)
        assertEquals(AsrEngineState.READY, e.state.value)
        assertFalse(e.isUsingNative)
    }

    @Test
    fun `transcribe returns stub text when no native`() = runBlocking {
        val e = engine()
        e.load(AsrConfig(enabled = true, modelPath = "stub://m", language = "zh"))
        val pcm = ByteArray(3200)
        val result = e.transcribe(AsrTranscribeRequest(pcm16leMono = pcm, sampleRateHz = 16_000))
        assertTrue(result.isStub)
        assertTrue(result.text.contains("[asr-stub]"))
        assertTrue(result.text.contains("lang=zh"))
        assertTrue(result.text.contains("3200B"))
    }

    @Test
    fun `transcribe without load throws`() = runBlocking {
        val e = engine()
        try {
            e.transcribe(AsrTranscribeRequest(pcm16leMono = ByteArray(4)))
            throw AssertionError("expected IllegalStateException")
        } catch (ex: IllegalStateException) {
            assertTrue(ex.message!!.contains("not ready"))
        }
    }

    @Test
    fun `streamPartial emits partial then final`() = runBlocking {
        val e = engine()
        e.load(AsrConfig(enabled = true, modelPath = "stub://m"))
        val chunks = e.streamPartial(
            AsrTranscribeRequest(pcm16leMono = ByteArray(100))
        ).toList()
        assertEquals(2, chunks.size)
        assertTrue(chunks[0].isPartial)
        assertFalse(chunks[1].isPartial)
    }

    @Test
    fun `unload after ready goes IDLE when still enabled`() = runBlocking {
        val e = engine()
        e.load(AsrConfig(enabled = true, modelPath = "stub://m"))
        e.unload()
        assertEquals(AsrEngineState.IDLE, e.state.value)
        assertFalse(e.isReady)
        assertFalse(e.isUsingNative)
    }
}
