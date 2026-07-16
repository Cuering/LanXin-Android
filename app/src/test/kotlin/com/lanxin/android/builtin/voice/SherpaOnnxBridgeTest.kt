package com.lanxin.android.builtin.voice

import com.lanxin.android.builtin.voice.data.SherpaOnnxBridge
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaOnnxBridgeTest {

    private val bridge = SherpaOnnxBridge()

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
    fun `loadModel stub returns false`() {
        assertFalse(bridge.loadModel("stub://x", "zh"))
    }

    @Test
    fun `transcribe stub returns null`() {
        assertNull(bridge.transcribe(ByteArray(8), 16_000))
    }
}
