package com.lanxin.android.builtin.pet

import com.lanxin.android.builtin.pet.domain.Live2dDisplayController
import com.lanxin.android.builtin.pet.domain.PetBridgeCommand
import com.lanxin.android.builtin.pet.domain.PetBridgeProtocol
import com.lanxin.android.builtin.pet.domain.PetExpressionController
import com.lanxin.android.builtin.pet.domain.VoiceSessionPhase
import com.lanxin.android.builtin.pet.domain.VoiceSessionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetExpressionControllerTest {

    @Test
    fun `pose maps each phase`() {
        val listening = PetExpressionController.poseFor(VoiceSessionPhase.LISTENING)
        assertEquals(PetExpressionController.Expression.LISTENING, listening.expression)
        assertFalse(listening.mouthAnimating)
        assertEquals(0f, listening.mouthOpen, 0.001f)

        val speaking = PetExpressionController.poseFor(VoiceSessionPhase.SPEAKING)
        assertEquals(PetExpressionController.Expression.SPEAKING, speaking.expression)
        assertTrue(speaking.mouthAnimating)
        assertTrue(speaking.mouthOpen > 0f)

        val thinking = PetExpressionController.poseFor(VoiceSessionPhase.THINKING)
        assertEquals(PetExpressionController.Expression.THINKING, thinking.expression)

        val error = PetExpressionController.poseFor(VoiceSessionPhase.ERROR)
        assertEquals(PetExpressionController.Expression.APOLOGY, error.expression)
    }

    @Test
    fun `fallback idle uses FALLBACK_NEUTRAL`() {
        val pose = PetExpressionController.poseFor(
            VoiceSessionPhase.IDLE,
            Live2dDisplayController.Live2dDisplayMode.FALLBACK
        )
        assertEquals(PetExpressionController.Expression.FALLBACK_NEUTRAL, pose.expression)
        assertTrue(pose.shortLabel.contains("降级"))
    }

    @Test
    fun `mouthOpenWire clamps and formats`() {
        assertEquals("0.00", PetExpressionController.mouthOpenWire(-1f))
        assertEquals("1.00", PetExpressionController.mouthOpenWire(2f))
        assertEquals("0.55", PetExpressionController.mouthOpenWire(0.55f))
    }

    @Test
    fun `guide mentions missing resources`() {
        val g = PetExpressionController.guideForMissingResources(
            live2dReady = false,
            asrReady = true,
            ttsReady = false
        )
        assertTrue(g.contains("Live2D"))
        assertTrue(g.contains("TTS"))
        assertTrue(
            g.contains("一键下载") || g.contains("fetch-debug-assets") ||
                g.contains("LanXin") || g.contains("debug-assets")
        )
        val ok = PetExpressionController.guideForMissingResources(true, true, true)
        assertTrue(ok.contains("路径均已就绪"))
    }

    @Test
    fun `sessionStateMessage embeds expression payload`() {
        val snap = VoiceSessionSnapshot(phase = VoiceSessionPhase.SPEAKING, roundId = 2)
        val msg = PetBridgeProtocol.sessionStateMessage(snap, timestampMs = 1L)
        assertEquals(PetBridgeCommand.SESSION_STATE, msg.command)
        assertEquals("SPEAKING", msg.payload[PetBridgeProtocol.KEY_EXPRESSION])
        assertEquals("true", msg.payload[PetBridgeProtocol.KEY_MOUTH_ANIM])
        assertTrue(msg.payload[PetBridgeProtocol.KEY_MOUTH_OPEN]!!.toFloat() > 0f)
        val wire = PetBridgeProtocol.encode(msg)
        val back = PetBridgeProtocol.decode(wire)
        assertEquals("SPEAKING", back.payload[PetBridgeProtocol.KEY_EXPRESSION])
    }

    @Test
    fun `setExpressionMessage roundtrip`() {
        val pose = PetExpressionController.poseFor(VoiceSessionPhase.LISTENING)
        val msg = PetBridgeProtocol.setExpressionMessage(
            pose,
            VoiceSessionPhase.LISTENING,
            timestampMs = 7L
        )
        assertEquals(PetBridgeCommand.SET_EXPRESSION, msg.command)
        assertEquals("LISTENING", msg.payload[PetBridgeProtocol.KEY_EXPRESSION])
        val decoded = PetBridgeProtocol.decode(PetBridgeProtocol.encode(msg))
        assertEquals(PetBridgeCommand.SET_EXPRESSION, decoded.command)
        assertEquals("在听", decoded.payload[PetBridgeProtocol.KEY_EXPRESSION_LABEL])
    }
}
