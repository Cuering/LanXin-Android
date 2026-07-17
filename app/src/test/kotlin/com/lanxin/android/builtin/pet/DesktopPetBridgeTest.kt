package com.lanxin.android.builtin.pet

import com.lanxin.android.builtin.pet.data.AndroidVoiceBridge
import com.lanxin.android.builtin.pet.data.DesktopPetBridge
import com.lanxin.android.builtin.pet.domain.PetBridgeCommand
import com.lanxin.android.builtin.pet.domain.PetBridgeMessage
import com.lanxin.android.builtin.pet.domain.PetBridgeProtocol
import com.lanxin.android.builtin.pet.domain.VoiceSessionPhase
import com.lanxin.android.builtin.pet.domain.VoiceSessionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopPetBridgeTest {

    @Test
    fun `DesktopPetBridge postMessage decodes command`() {
        var got: PetBridgeMessage? = null
        val bridge = DesktopPetBridge { got = it }
        val wire = PetBridgeProtocol.encode(
            PetBridgeMessage(
                command = PetBridgeCommand.START_VOICE,
                payload = mapOf("x" to "1"),
                timestampMs = 7L
            )
        )
        bridge.postMessage(wire)
        assertEquals(PetBridgeCommand.START_VOICE, got!!.command)
        assertEquals("1", got!!.payload["x"])
        assertEquals(wire, bridge.lastInbound)
    }

    @Test
    fun `DesktopPetBridge close and ping`() {
        var cmds = mutableListOf<PetBridgeCommand>()
        val bridge = DesktopPetBridge { cmds += it.command }
        bridge.closePet()
        assertEquals(PetBridgeCommand.CLOSE_PET, cmds.last())
        assertEquals("pong", bridge.ping())
    }

    @Test
    fun `encodeSession writes outbound`() {
        val bridge = DesktopPetBridge {}
        val out = bridge.encodeSession(
            VoiceSessionSnapshot(phase = VoiceSessionPhase.THINKING, asrText = "hi")
        )
        assertTrue(out.contains("command=SESSION_STATE"))
        assertTrue(out.contains("phase=THINKING"))
        assertEquals(out, bridge.lastOutbound)
    }

    @Test
    fun `encodeLoadLive2d writes outbound`() {
        val bridge = DesktopPetBridge {}
        val decision = com.lanxin.android.builtin.pet.domain.Live2dDisplayController.Decision(
            mode = com.lanxin.android.builtin.pet.domain.Live2dDisplayController.Live2dDisplayMode.LIVE2D_SHELL,
            model3Path = "/tmp/Mao.model3.json",
            model3FileUrl = "file:///tmp/Mao.model3.json",
            modelDirFileUrl = "file:///tmp",
            reason = "live2d_shell_ready",
            shortLabel = "Live2D 壳"
        )
        val out = bridge.encodeLoadLive2d(decision)
        assertTrue(out.contains("command=LOAD_LIVE2D"))
        assertTrue(out.contains("live2dMode=LIVE2D_SHELL"))
        assertEquals(out, bridge.lastOutbound)
    }

    @Test
    fun `AndroidVoiceBridge snapshot json and commands`() {
        var snap = VoiceSessionSnapshot(
            phase = VoiceSessionPhase.LISTENING,
            asrText = "a",
            replyText = "b",
            subtitle = "c",
            roundId = 2
        )
        var started = 0
        var stopped = 0
        val bridge = AndroidVoiceBridge(
            snapshotProvider = { snap },
            onStartVoice = { started++ },
            onStopVoice = { stopped++ }
        )
        assertEquals("LISTENING", bridge.getPhase())
        assertTrue(bridge.isListening())
        assertTrue(bridge.getSnapshotJson().contains("\"phase\":\"LISTENING\""))
        bridge.startVoiceChat()
        bridge.stopVoiceChat()
        assertEquals(1, started)
        assertEquals(1, stopped)
        assertEquals(PetBridgeCommand.STOP_VOICE, bridge.lastCommand)
    }
}
