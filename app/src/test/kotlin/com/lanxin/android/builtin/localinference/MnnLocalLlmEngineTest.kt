package com.lanxin.android.builtin.localinference

import com.lanxin.android.builtin.localinference.data.MnnLocalLlmEngine
import com.lanxin.android.builtin.localinference.data.MnnNativeBridge
import com.lanxin.android.builtin.localinference.domain.LocalEngineState
import com.lanxin.android.builtin.localinference.domain.LocalGenerateRequest
import com.lanxin.android.builtin.localinference.domain.LocalInferenceConfig
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM 单测：无 MNN so 时 [MnnLocalLlmEngine] 走 stub:// READY 与路径合法降级。
 */
class MnnLocalLlmEngineTest {

    private fun engine() = MnnLocalLlmEngine(MnnNativeBridge())

    @Before
    fun setUp() {
        MnnNativeBridge.resetNativeLoadStateForTests()
    }

    @Test
    fun `load disabled stays DISABLED`() = runBlocking {
        val e = engine()
        val ok = e.load(LocalInferenceConfig(enabled = false, modelPath = "stub://x"))
        assertFalse(ok)
        assertEquals(LocalEngineState.DISABLED, e.state.value)
        assertFalse(e.isAvailable)
        assertFalse(e.isReady)
        assertFalse(e.isUsingNative)
    }

    @Test
    fun `load empty path errors`() = runBlocking {
        val e = engine()
        val ok = e.load(LocalInferenceConfig(enabled = true, modelPath = "  "))
        assertFalse(ok)
        assertEquals(LocalEngineState.ERROR, e.state.value)
        assertEquals("model_path_empty", e.lastError)
    }

    @Test
    fun `load stub scheme becomes READY without native`() = runBlocking {
        val e = engine()
        val ok = e.load(
            LocalInferenceConfig(enabled = true, modelPath = "stub://demo-model")
        )
        assertTrue(ok)
        assertTrue(e.isReady)
        assertTrue(e.isAvailable)
        assertEquals(LocalEngineState.READY, e.state.value)
        assertFalse(e.isUsingNative)
    }

    @Test
    fun `generate returns stub text when not native`() = runBlocking {
        val e = engine()
        e.load(LocalInferenceConfig(enabled = true, modelPath = "stub://m"))
        val result = e.generate(
            LocalGenerateRequest(prompt = "hello world", systemPrompt = "sys")
        )
        assertTrue(result.isStub)
        assertTrue(result.text.contains("[local-stub]"))
        assertTrue(result.text.contains("hello world"))
    }

    @Test
    fun `generate without load throws`() = runBlocking {
        val e = engine()
        try {
            e.generate(LocalGenerateRequest(prompt = "x"))
            throw AssertionError("expected IllegalStateException")
        } catch (ex: IllegalStateException) {
            assertTrue(ex.message!!.contains("not ready"))
        }
    }

    @Test
    fun `stream emits single chunk`() = runBlocking {
        val e = engine()
        e.load(LocalInferenceConfig(enabled = true, modelPath = "stub://m"))
        val chunks = e.stream(LocalGenerateRequest(prompt = "hi")).toList()
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].contains("hi"))
    }

    @Test
    fun `unload after ready goes IDLE when still enabled`() = runBlocking {
        val e = engine()
        e.load(LocalInferenceConfig(enabled = true, modelPath = "stub://m"))
        e.unload()
        assertEquals(LocalEngineState.IDLE, e.state.value)
        assertFalse(e.isReady)
        assertFalse(e.isUsingNative)
    }

    @Test
    fun `load missing real path errors`() = runBlocking {
        val e = engine()
        val ok = e.load(
            LocalInferenceConfig(
                enabled = true,
                modelPath = "/nonexistent/mnn/model/dir"
            )
        )
        assertFalse(ok)
        assertEquals(LocalEngineState.ERROR, e.state.value)
        assertTrue(e.lastError!!.startsWith("model_path_missing:"))
    }
}
