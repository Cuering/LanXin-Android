package com.lanxin.android.builtin.localinference

import com.lanxin.android.builtin.localinference.data.MnnNativeBridge
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MnnNativeBridgeTest {

    private val bridge = MnnNativeBridge()

    @Before
    fun setUp() {
        MnnNativeBridge.resetNativeLoadStateForTests()
        bridge.unload()
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
        assertFalse(bridge.isSessionLoaded())
    }

    @Test
    fun `isNativeAvailable false on JVM unit tests`() {
        // No MNN so on pure JVM → false, must not throw
        assertFalse(bridge.isNativeAvailable())
    }

    @Test
    fun `generate without load returns null`() {
        assertNull(bridge.generate("hello", 16))
    }

    @Test
    fun `loadModel missing path returns false`() {
        assertFalse(bridge.loadModel("/no/such/model/dir"))
        assertFalse(bridge.isSessionLoaded())
        assertTrue(bridge.lastError() != null)
    }

    @Test
    fun `unload is idempotent`() {
        bridge.unload()
        bridge.unload()
        assertFalse(bridge.isSessionLoaded())
    }

    @Test
    fun `nativeLoadError safe after failed tryLoad`() {
        bridge.isNativeAvailable()
        // may be null or UnsatisfiedLinkError string; just must not throw
        bridge.nativeLoadError()
        bridge.lastError()
    }
}
