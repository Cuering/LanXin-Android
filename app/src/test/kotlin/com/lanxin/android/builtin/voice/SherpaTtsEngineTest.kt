package com.lanxin.android.builtin.voice

import com.lanxin.android.builtin.voice.data.SherpaOnnxBridge
import com.lanxin.android.builtin.voice.data.SherpaTtsBridge
import com.lanxin.android.builtin.voice.data.SherpaTtsEngine
import com.lanxin.android.builtin.voice.domain.TtsConfig
import com.lanxin.android.builtin.voice.domain.TtsEngineState
import com.lanxin.android.builtin.voice.domain.TtsSynthesizeRequest
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * JVM：无 so 时 [SherpaTtsEngine] 对 stub:// / 合法路径降级 stub，不崩。
 */
class SherpaTtsEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun resetNative() {
        SherpaOnnxBridge.resetNativeLoadStateForTests()
    }

    private fun engine() = SherpaTtsEngine(SherpaTtsBridge())

    @Test
    fun `load disabled stays DISABLED`() = runBlocking {
        val e = engine()
        val ok = e.load(TtsConfig(enabled = false, modelDir = "stub://x"))
        assertFalse(ok)
        assertEquals(TtsEngineState.DISABLED, e.state.value)
        assertFalse(e.isAvailable)
        assertFalse(e.isReady)
        assertFalse(e.isUsingNative)
    }

    @Test
    fun `load enabled without path becomes READY stub`() = runBlocking {
        val e = engine()
        val ok = e.load(TtsConfig(enabled = true, modelDir = "", modelPath = ""))
        assertTrue(ok)
        assertTrue(e.isReady)
        assertTrue(e.isAvailable)
        assertFalse(e.isUsingNative)
    }

    @Test
    fun `load stub scheme becomes READY without native`() = runBlocking {
        val e = engine()
        val ok = e.load(TtsConfig(enabled = true, modelDir = "stub://demo-tts"))
        assertTrue(ok)
        assertTrue(e.isReady)
        assertEquals(TtsEngineState.READY, e.state.value)
        assertFalse(e.isUsingNative)
    }

    @Test
    fun `load real dir without native degrades READY with lastError`() = runBlocking {
        val dir = tmp.newFolder("matcha-icefall-zh-baker")
        File(dir, "tokens.txt").writeText("a\n")
        File(dir, "model-steps-3.onnx").writeBytes(ByteArray(64))
        File(dir, "vocos-22khz-univ.onnx").writeBytes(ByteArray(64))
        val e = engine()
        val ok = e.load(TtsConfig(enabled = true, modelDir = dir.absolutePath))
        assertTrue(ok)
        assertTrue(e.isReady)
        assertFalse(e.isUsingNative)
        assertTrue(e.lastError!!.startsWith("native_degraded:"))
    }

    @Test
    fun `synthesize returns stub subtitle when no native`() = runBlocking {
        val e = engine()
        e.load(TtsConfig(enabled = true, modelDir = "stub://m"))
        val r = e.synthesize(TtsSynthesizeRequest(text = "你好兰心"))
        assertTrue(r.isStub)
        assertEquals("你好兰心", r.subtitle)
        assertTrue(r.durationMs >= 400L)
        assertEquals(0, r.pcm16leMono.size)
        assertEquals(TtsEngineState.READY, e.state.value)
    }

    @Test
    fun `synthesize without load throws`() = runBlocking {
        val e = engine()
        try {
            e.synthesize(TtsSynthesizeRequest(text = "x"))
            throw AssertionError("expected IllegalStateException")
        } catch (ex: IllegalStateException) {
            assertTrue(ex.message!!.contains("not ready"))
        }
    }

    @Test
    fun `unload after ready goes IDLE when still enabled`() = runBlocking {
        val e = engine()
        e.load(TtsConfig(enabled = true, modelDir = "stub://m"))
        e.unload()
        assertEquals(TtsEngineState.IDLE, e.state.value)
        assertFalse(e.isReady)
        assertFalse(e.isUsingNative)
    }

    @Test
    fun `modelPath fallback used when modelDir empty`() = runBlocking {
        val e = engine()
        val ok = e.load(TtsConfig(enabled = true, modelDir = "", modelPath = "stub://from-path"))
        assertTrue(ok)
        assertTrue(e.isReady)
    }
}
