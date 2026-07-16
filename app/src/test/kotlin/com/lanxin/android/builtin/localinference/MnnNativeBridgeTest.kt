package com.lanxin.android.builtin.localinference

import com.lanxin.android.builtin.localinference.data.MnnNativeBridge
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MnnNativeBridgeTest {

    private val bridge = MnnNativeBridge()

    @Test
    fun `blank path invalid`() {
        assertFalse(bridge.validateModelPath(""))
        assertFalse(bridge.validateModelPath("   "))
    }

    @Test
    fun `stub scheme valid`() {
        assertTrue(bridge.validateModelPath("stub://unit-test"))
    }

    @Test
    fun `missing file invalid`() {
        assertFalse(bridge.validateModelPath("/nonexistent/path/to/model.bin"))
    }

    @Test
    fun `loadModel stub returns false`() {
        assertFalse(bridge.loadModel("stub://x"))
    }
}
