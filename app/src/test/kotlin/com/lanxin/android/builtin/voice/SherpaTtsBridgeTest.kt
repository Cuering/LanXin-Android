package com.lanxin.android.builtin.voice

import com.lanxin.android.builtin.voice.data.SherpaOnnxBridge
import com.lanxin.android.builtin.voice.data.SherpaTtsBridge
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule

/**
 * JVM：无 so 时 [SherpaTtsBridge] 不崩；PCM 转换与路径校验可测。
 */
class SherpaTtsBridgeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val bridge = SherpaTtsBridge()

    @Before
    fun resetNative() {
        SherpaOnnxBridge.resetNativeLoadStateForTests()
    }

    @Test
    fun `isNativeAvailable is false on JVM without so`() {
        assertFalse(bridge.isNativeAvailable())
    }

    @Test
    fun `validateModelPath accepts stub scheme`() {
        assertTrue(bridge.validateModelPath("stub://tts-demo"))
        assertFalse(bridge.validateModelPath(""))
        assertFalse(bridge.validateModelPath("/no/such/path/xyz"))
    }

    @Test
    fun `validateModelPath accepts existing directory`() {
        val dir = tmp.newFolder("tts-model")
        assertTrue(bridge.validateModelPath(dir.absolutePath))
    }

    @Test
    fun `loadModel fails without native and records error`() {
        val dir = tmp.newFolder("matcha")
        File(dir, "tokens.txt").writeText("a\n")
        File(dir, "model-steps-3.onnx").writeBytes(ByteArray(100))
        File(dir, "vocos-22khz-univ.onnx").writeBytes(ByteArray(100))
        File(dir, "lexicon.txt").writeText("a 1\n")
        File(dir, "dict").mkdirs()
        File(File(dir, "dict"), "jieba.dict.utf8").writeText("x")
        assertFalse(bridge.loadModel(dir.absolutePath))
        assertTrue(bridge.lastError() != null)
    }

    @Test
    fun `loadModel rejects matcha without vocoder and does not crash`() {
        // 缺 vocoder 时旧逻辑会把 matcha acoustic 误识别成 VITS → native abort
        val dir = tmp.newFolder("matcha-icefall-zh-baker")
        File(dir, "tokens.txt").writeText("a\n")
        File(dir, "lexicon.txt").writeText("a 1\n")
        File(dir, "model-steps-3.onnx").writeBytes(ByteArray(100))
        File(dir, "dict").mkdirs()
        File(File(dir, "dict"), "jieba.dict.utf8").writeText("x")
        assertFalse(bridge.loadModel(dir.absolutePath))
        val err = bridge.lastError()
        assertTrue("expected layout/native error, got $err", err != null)
        // JVM 无 so → native_unavailable；有 so 时则为 unsupported_tts_layout
        assertTrue(
            err!!.contains("unsupported_tts_layout") ||
                err.contains("native_unavailable") ||
                err.contains("stub_path") ||
                err.contains("missing_files")
        )
    }

    @Test
    fun `floatToPcm16le length and zero`() {
        val pcm = SherpaTtsBridge.floatToPcm16le(floatArrayOf(0f, 1f, -1f, 0.5f))
        assertEquals(8, pcm.size)
        // 0 → 0,0 LE
        assertEquals(0, pcm[0].toInt())
        assertEquals(0, pcm[1].toInt())
    }

    @Test
    fun `durationMs computes`() {
        assertEquals(1000L, SherpaTtsBridge.durationMs(22_050, 22_050))
        assertEquals(0L, SherpaTtsBridge.durationMs(0, 22_050))
    }
}
