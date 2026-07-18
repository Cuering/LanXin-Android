package com.lanxin.android.builtin.pet

import com.lanxin.android.builtin.pet.data.AndroidVoiceBridge
import com.lanxin.android.builtin.pet.data.DesktopPetBridge
import com.lanxin.android.builtin.pet.domain.PetBridgeCommand
import com.lanxin.android.builtin.pet.domain.PetBridgeMessage
import com.lanxin.android.builtin.pet.domain.PetBridgeProtocol
import com.lanxin.android.builtin.pet.domain.VoiceSessionPhase
import com.lanxin.android.builtin.pet.domain.VoiceSessionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(out.contains("expression=THINKING"))
        assertEquals(out, bridge.lastOutbound)
    }

    @Test
    fun `encodeExpression writes outbound`() {
        val bridge = DesktopPetBridge {}
        val pose = com.lanxin.android.builtin.pet.domain.PetExpressionController.poseFor(
            VoiceSessionPhase.SPEAKING
        )
        val out = bridge.encodeExpression(pose, VoiceSessionPhase.SPEAKING)
        assertTrue(out.contains("command=SET_EXPRESSION"))
        assertTrue(out.contains("expression=SPEAKING"))
        assertTrue(out.contains("mouthAnimating=true"))
        assertTrue(out.contains("cubismExpression=exp_04"))
        assertEquals(out, bridge.lastOutbound)
    }

    @Test
    fun `encodePlayMotion writes Idle and TapBody`() {
        val bridge = DesktopPetBridge {}
        val idle = bridge.encodePlayMotion("Idle", 0)
        assertTrue(idle.contains("command=PLAY_MOTION"))
        assertTrue(idle.contains("motionGroup=Idle"))
        assertTrue(idle.contains("motionIndex=0"))
        val tap = bridge.encodePlayMotion("TapBody", null)
        assertTrue(tap.contains("motionGroup=TapBody"))
        assertFalse(tap.contains("motionIndex="))
        assertEquals(tap, bridge.lastOutbound)
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
    fun `encodeLoadLive2d includes model3 b64 when injected`() {
        val bridge = DesktopPetBridge {}
        val json = """{"Version":3,"FileReferences":{"Textures":["t.png"]}}"""
        val decision = com.lanxin.android.builtin.pet.domain.Live2dDisplayController.Decision(
            mode = com.lanxin.android.builtin.pet.domain.Live2dDisplayController.Live2dDisplayMode.LIVE2D_SHELL,
            model3Path = "/tmp/Mao.model3.json",
            model3FileUrl = "file:///tmp/Mao.model3.json",
            modelDirFileUrl = "file:///tmp",
            reason = "live2d_shell_ready",
            shortLabel = "Live2D 壳",
            model3Json = json
        )
        val out = bridge.encodeLoadLive2d(decision)
        assertTrue(out.contains("live2dModel3B64="))
        val decoded = PetBridgeProtocol.decode(out)
        val b64 = decoded.payload[PetBridgeProtocol.KEY_LIVE2D_MODEL3_B64]
        assertTrue(!b64.isNullOrBlank())
        assertTrue(PetBridgeProtocol.decodeModel3B64(b64!!)?.contains("t.png") == true)
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
