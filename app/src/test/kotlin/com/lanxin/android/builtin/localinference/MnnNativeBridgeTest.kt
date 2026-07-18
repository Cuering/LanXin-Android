package com.lanxin.android.builtin.localinference

import com.lanxin.android.builtin.localinference.data.MnnNativeBridge
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM 单测：无 libmnn_lanxin.so 时 bridge 不崩。
 */
class MnnNativeBridgeTest {

    private val bridge = MnnNativeBridge()

    @Before
    fun resetNative() {
        MnnNativeBridge.resetNativeLoadStateForTests()
    }

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
        assertTrue(bridge.lastError()!!.contains("stub"))
    }

    @Test
    fun `isNativeAvailable false without so`() {
        // JVM 单测无 APK jniLibs
        assertFalse(bridge.isNativeAvailable())
    }

    @Test
    fun `generate without load returns null`() {
        assertNull(bridge.generate("hi", 16))
    }

    @Test
    fun `unload is safe without native`() {
        bridge.unload()
        assertFalse(bridge.isSessionLoaded())
    }
}
