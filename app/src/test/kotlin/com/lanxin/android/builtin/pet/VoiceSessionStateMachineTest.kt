package com.lanxin.android.builtin.pet

import com.lanxin.android.builtin.pet.domain.VoiceSessionPhase
import com.lanxin.android.builtin.pet.domain.VoiceSessionSnapshot
import com.lanxin.android.builtin.pet.domain.VoiceSessionStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionStateMachineTest {

    @Test
    fun `happy path idle listen think speak idle`() {
        var s = VoiceSessionSnapshot()
        assertTrue(VoiceSessionStateMachine.canTransition(VoiceSessionPhase.IDLE, VoiceSessionPhase.LISTENING))
        s = VoiceSessionStateMachine.startListening(s)
        assertEquals(VoiceSessionPhase.LISTENING, s.phase)
        assertEquals(1L, s.roundId)

        s = VoiceSessionStateMachine.onAsrDone(s, "你好")
        assertEquals(VoiceSessionPhase.THINKING, s.phase)
        assertEquals("你好", s.asrText)

        s = VoiceSessionStateMachine.onThinkDone(s, "嗯嗯")
        assertEquals(VoiceSessionPhase.SPEAKING, s.phase)
        assertEquals("嗯嗯", s.replyText)
        assertEquals("嗯嗯", s.subtitle)

        s = VoiceSessionStateMachine.onSpeakDone(s)
        assertEquals(VoiceSessionPhase.IDLE, s.phase)
        assertNull(s.lastError)
    }

    @Test
    fun `illegal transition becomes ERROR`() {
        val s = VoiceSessionSnapshot(phase = VoiceSessionPhase.IDLE)
        assertFalse(VoiceSessionStateMachine.canTransition(VoiceSessionPhase.IDLE, VoiceSessionPhase.SPEAKING))
        val next = VoiceSessionStateMachine.transition(s, VoiceSessionPhase.SPEAKING)
        assertEquals(VoiceSessionPhase.ERROR, next.phase)
        assertTrue(next.lastError!!.contains("illegal_transition"))
    }

    @Test
    fun `reset clears texts`() {
        val s = VoiceSessionSnapshot(
            phase = VoiceSessionPhase.SPEAKING,
            asrText = "a",
            replyText = "b",
            subtitle = "c"
        )
        val r = VoiceSessionStateMachine.reset(s)
        assertEquals(VoiceSessionPhase.IDLE, r.phase)
        assertEquals("", r.asrText)
        assertEquals("", r.replyText)
        assertEquals("", r.subtitle)
    }

    @Test
    fun `listening can cancel to idle`() {
        assertTrue(
            VoiceSessionStateMachine.canTransition(
                VoiceSessionPhase.LISTENING,
                VoiceSessionPhase.IDLE
            )
        )
    }
}
