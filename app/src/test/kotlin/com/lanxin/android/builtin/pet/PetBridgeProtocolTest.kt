package com.lanxin.android.builtin.pet

import com.lanxin.android.builtin.pet.domain.PetBridgeCommand
import com.lanxin.android.builtin.pet.domain.PetBridgeMessage
import com.lanxin.android.builtin.pet.domain.PetBridgeProtocol
import com.lanxin.android.builtin.pet.domain.VoiceSessionPhase
import com.lanxin.android.builtin.pet.domain.VoiceSessionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetBridgeProtocolTest {

    @Test
    fun `encode decode roundtrip`() {
        val msg = PetBridgeMessage(
            command = PetBridgeCommand.SESSION_STATE,
            payload = mapOf(
                "phase" to "LISTENING",
                "asrText" to "你好"
            ),
            timestampMs = 42L
        )
        val wire = PetBridgeProtocol.encode(msg)
        assertTrue(wire.contains("command=SESSION_STATE"))
        assertTrue(wire.contains("phase=LISTENING"))
        val back = PetBridgeProtocol.decode(wire)
        assertEquals(PetBridgeCommand.SESSION_STATE, back.command)
        assertEquals("LISTENING", back.payload["phase"])
        assertEquals("你好", back.payload["asrText"])
        assertEquals(42L, back.timestampMs)
    }

    @Test
    fun `parse unknown command`() {
        assertEquals(PetBridgeCommand.UNKNOWN, PetBridgeMessage.parseCommand("nope"))
        assertEquals(PetBridgeCommand.START_VOICE, PetBridgeMessage.parseCommand("start_voice"))
    }

    @Test
    fun `sessionStateMessage fields`() {
        val snap = VoiceSessionSnapshot(
            phase = VoiceSessionPhase.SPEAKING,
            asrText = "q",
            replyText = "a",
            subtitle = "a",
            roundId = 3
        )
        val msg = PetBridgeProtocol.sessionStateMessage(snap, timestampMs = 1L)
        assertEquals(PetBridgeCommand.SESSION_STATE, msg.command)
        assertEquals("SPEAKING", msg.payload[PetBridgeProtocol.KEY_PHASE])
        assertEquals("3", msg.payload[PetBridgeProtocol.KEY_ROUND])
        val wire = PetBridgeProtocol.encode(msg)
        val decoded = PetBridgeProtocol.decode(wire)
        assertEquals("a", decoded.payload[PetBridgeProtocol.KEY_SUBTITLE])
    }

    @Test
    fun `showBubbleMessage`() {
        val msg = PetBridgeProtocol.showBubbleMessage("气泡", 9L)
        assertEquals(PetBridgeCommand.SHOW_BUBBLE, msg.command)
        assertEquals("气泡", msg.payload[PetBridgeProtocol.KEY_BUBBLE])
    }
}
