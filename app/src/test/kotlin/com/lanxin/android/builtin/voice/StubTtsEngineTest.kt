package com.lanxin.android.builtin.voice

import com.lanxin.android.builtin.voice.data.StubTtsEngine
import com.lanxin.android.builtin.voice.domain.TtsConfig
import com.lanxin.android.builtin.voice.domain.TtsEngineState
import com.lanxin.android.builtin.voice.domain.TtsSynthesizeRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StubTtsEngineTest {

    @Test
    fun `load disabled stays DISABLED`() = runBlocking {
        val e = StubTtsEngine()
        val ok = e.load(TtsConfig(enabled = false))
        assertEquals(false, ok)
        assertEquals(TtsEngineState.DISABLED, e.state.value)
    }

    @Test
    fun `synthesize after load returns stub subtitle`() = runBlocking {
        val e = StubTtsEngine()
        assertTrue(e.load(TtsConfig(enabled = true)))
        assertTrue(e.isReady)
        val r = e.synthesize(TtsSynthesizeRequest(text = "你好兰心"))
        assertTrue(r.isStub)
        assertEquals("你好兰心", r.subtitle)
        assertTrue(r.durationMs >= 400L)
        assertEquals(TtsEngineState.READY, e.state.value)
    }
}
