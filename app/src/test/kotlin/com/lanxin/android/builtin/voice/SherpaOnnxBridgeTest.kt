package com.lanxin.android.builtin.voice

import com.lanxin.android.builtin.voice.data.SherpaOnnxBridge
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM 单测：无 libsherpa-onnx-jni.so 时 bridge 不崩。
 */
class SherpaOnnxBridgeTest {

    private val bridge = SherpaOnnxBridge()

    @Before
    fun resetNative() {
        SherpaOnnxBridge.resetNativeLoadStateForTests()
    }

    @Test
    fun `stub scheme path is valid`() {
        assertTrue(bridge.validateModelPath("stub://demo-asr"))
    }

    @Test
    fun `blank path invalid`() {
        assertFalse(bridge.validateModelPath(""))
        assertFalse(bridge.validateModelPath("   "))
    }

    @Test
    fun `missing real path invalid`() {
        assertFalse(bridge.validateModelPath("/no/such/asr/model/path-xyz"))
    }

    @Test
    fun `loadModel stub scheme returns false`() {
        assertFalse(bridge.loadModel("stub://x", "zh"))
        assertNotNull(bridge.lastError())
    }

    @Test
    fun `transcribe without load returns null`() {
        assertNull(bridge.transcribe(ByteArray(8), 16_000))
    }

    @Test
    fun `isNativeAvailable safe on jvm`() {
        // JVM 无 so：false；不抛 UnsatisfiedLinkError 到测试
        val ok = bridge.isNativeAvailable()
        assertFalse(ok)
        assertNotNull(bridge.nativeLoadError())
    }

    @Test
    fun `pcm16leToFloat converts`() {
        // 0x00 0x40 = 16384 LE → ~0.5
        val pcm = byteArrayOf(0x00, 0x40)
        val f = SherpaOnnxBridge.pcm16leToFloat(pcm)
        assertTrue(f.size == 1)
        assertTrue(f[0] > 0.4f && f[0] < 0.6f)
    }

    @Test
    fun `unload is safe`() {
        bridge.unload()
    }
}
