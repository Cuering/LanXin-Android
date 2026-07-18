package com.lanxin.android.builtin.pet

import com.lanxin.android.builtin.pet.domain.DebugAssetCatalog
import com.lanxin.android.builtin.pet.domain.MaoOfficialMotionCatalog
import com.lanxin.android.builtin.pet.domain.PetBridgeCommand
import com.lanxin.android.builtin.pet.domain.PetBridgeProtocol
import com.lanxin.android.builtin.pet.domain.PetExpressionController
import com.lanxin.android.builtin.pet.domain.VoiceSessionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mao 官方 exp/motion 清单与会话映射单测。
 *
 * 只允许官方 model3 内文件名；与 DebugAssetCatalog 路径一致。
 */
class MaoOfficialMotionCatalogTest {

    @Test
    fun `all eight expressions listed`() {
        assertEquals(
            listOf(
                "exp_01", "exp_02", "exp_03", "exp_04",
                "exp_05", "exp_06", "exp_07", "exp_08"
            ),
            MaoOfficialMotionCatalog.ALL_EXPRESSIONS
        )
        MaoOfficialMotionCatalog.ALL_EXPRESSIONS.forEach {
            assertTrue(MaoOfficialMotionCatalog.isKnownExpression(it))
        }
        assertFalse(MaoOfficialMotionCatalog.isKnownExpression("exp_99"))
        assertFalse(MaoOfficialMotionCatalog.isKnownExpression("happy"))
    }

    @Test
    fun `idle and tapBody motion files match model3 groups`() {
        assertEquals(
            listOf("mtn_01.motion3.json", "sample_01.motion3.json"),
            MaoOfficialMotionCatalog.IDLE_MOTION_FILES
        )
        assertEquals(
            listOf(
                "mtn_02.motion3.json",
                "mtn_03.motion3.json",
                "mtn_04.motion3.json",
                "special_01.motion3.json",
                "special_02.motion3.json",
                "special_03.motion3.json"
            ),
            MaoOfficialMotionCatalog.TAP_BODY_MOTION_FILES
        )
        assertEquals(2, MaoOfficialMotionCatalog.idleMotionCount())
        assertEquals(6, MaoOfficialMotionCatalog.tapBodyMotionCount())
        assertEquals(8, MaoOfficialMotionCatalog.ALL_MOTION_FILES.size)
        assertEquals(MaoOfficialMotionCatalog.GROUP_IDLE, MaoOfficialMotionCatalog.idleMotionGroup())
        assertEquals(MaoOfficialMotionCatalog.GROUP_TAP_BODY, MaoOfficialMotionCatalog.tapMotionGroup())
    }

    @Test
    fun `phase expression maps to exp 01-05`() {
        assertEquals(
            PetExpressionController.Expression.IDLE_SMILE,
            MaoOfficialMotionCatalog.phaseExpression(VoiceSessionPhase.IDLE)
        )
        assertEquals(
            PetExpressionController.Expression.LISTENING,
            MaoOfficialMotionCatalog.phaseExpression(VoiceSessionPhase.LISTENING)
        )
        assertEquals(
            PetExpressionController.Expression.THINKING,
            MaoOfficialMotionCatalog.phaseExpression(VoiceSessionPhase.THINKING)
        )
        assertEquals(
            PetExpressionController.Expression.SPEAKING,
            MaoOfficialMotionCatalog.phaseExpression(VoiceSessionPhase.SPEAKING)
        )
        assertEquals(
            PetExpressionController.Expression.APOLOGY,
            MaoOfficialMotionCatalog.phaseExpression(VoiceSessionPhase.ERROR)
        )
    }

    @Test
    fun `expressionFileFor covers all enum values`() {
        val expected = mapOf(
            PetExpressionController.Expression.IDLE_SMILE to "exp_01",
            PetExpressionController.Expression.LISTENING to "exp_02",
            PetExpressionController.Expression.THINKING to "exp_03",
            PetExpressionController.Expression.SPEAKING to "exp_04",
            PetExpressionController.Expression.APOLOGY to "exp_05",
            PetExpressionController.Expression.IDLE_VARIANT_A to "exp_06",
            PetExpressionController.Expression.TAP_REACTION to "exp_07",
            PetExpressionController.Expression.MUSIC_PEAK to "exp_08",
            PetExpressionController.Expression.FALLBACK_NEUTRAL to "exp_01"
        )
        expected.forEach { (expr, file) ->
            assertEquals(file, MaoOfficialMotionCatalog.expressionFileFor(expr))
            assertEquals(file, PetExpressionController.cubismExpressionName(expr))
        }
        // enum 全覆盖（防漏加档）
        PetExpressionController.Expression.entries.forEach { expr ->
            val name = MaoOfficialMotionCatalog.expressionFileFor(expr)
            assertTrue("$expr → $name", MaoOfficialMotionCatalog.isKnownExpression(name))
        }
    }

    @Test
    fun `isKnownMotionFile accepts bare and path forms`() {
        assertTrue(MaoOfficialMotionCatalog.isKnownMotionFile("mtn_01.motion3.json"))
        assertTrue(MaoOfficialMotionCatalog.isKnownMotionFile("motions/special_03.motion3.json"))
        assertTrue(MaoOfficialMotionCatalog.isKnownMotionFile("/abs/path/sample_01.motion3.json"))
        assertFalse(MaoOfficialMotionCatalog.isKnownMotionFile("unknown.motion3.json"))
        assertFalse(MaoOfficialMotionCatalog.isKnownMotionFile("haru_01.motion3.json"))
    }

    @Test
    fun `expected paths align with DebugAssetCatalog Mao list`() {
        val expected = MaoOfficialMotionCatalog.expectedRelativePaths().toSet()
        val catalog = DebugAssetCatalog.live2dMaoRelativeFiles
            .filter { it.startsWith("expressions/") || it.startsWith("motions/") }
            .toSet()
        assertEquals(catalog, expected)
        // 全量 8 exp + 8 motion
        assertEquals(16, expected.size)
    }

    @Test
    fun `playMotionMessage encodes Idle and TapBody`() {
        val idle = PetBridgeProtocol.playMotionMessage(
            MaoOfficialMotionCatalog.GROUP_IDLE,
            index = 1,
            timestampMs = 11L
        )
        assertEquals(PetBridgeCommand.PLAY_MOTION, idle.command)
        assertEquals("Idle", idle.payload[PetBridgeProtocol.KEY_MOTION_GROUP])
        assertEquals("1", idle.payload[PetBridgeProtocol.KEY_MOTION_INDEX])
        val idleWire = PetBridgeProtocol.encode(idle)
        val idleBack = PetBridgeProtocol.decode(idleWire)
        assertEquals(PetBridgeCommand.PLAY_MOTION, idleBack.command)
        assertEquals("Idle", idleBack.payload[PetBridgeProtocol.KEY_MOTION_GROUP])
        assertEquals("1", idleBack.payload[PetBridgeProtocol.KEY_MOTION_INDEX])

        val tap = PetBridgeProtocol.playMotionMessage(
            MaoOfficialMotionCatalog.GROUP_TAP_BODY,
            index = null,
            timestampMs = 12L
        )
        assertEquals("TapBody", tap.payload[PetBridgeProtocol.KEY_MOTION_GROUP])
        assertFalse(tap.payload.containsKey(PetBridgeProtocol.KEY_MOTION_INDEX))
        val tapWire = PetBridgeProtocol.encode(tap)
        assertTrue(tapWire.contains("command=PLAY_MOTION"))
        assertTrue(tapWire.contains("motionGroup=TapBody"))
        assertFalse(tapWire.contains("motionIndex="))
    }

    @Test
    fun `modelTappedMessage roundtrip`() {
        val msg = PetBridgeProtocol.modelTappedMessage(hitArea = "Head", timestampMs = 9L)
        assertEquals(PetBridgeCommand.MODEL_TAPPED, msg.command)
        assertEquals("Head", msg.payload[PetBridgeProtocol.KEY_HIT_AREA])
        val back = PetBridgeProtocol.decode(PetBridgeProtocol.encode(msg))
        assertEquals(PetBridgeCommand.MODEL_TAPPED, back.command)
        assertEquals("Head", back.payload[PetBridgeProtocol.KEY_HIT_AREA])
    }

    @Test
    fun `sessionState includes cubismExpression for each phase`() {
        val phaseToExp = mapOf(
            VoiceSessionPhase.IDLE to "exp_01",
            VoiceSessionPhase.LISTENING to "exp_02",
            VoiceSessionPhase.THINKING to "exp_03",
            VoiceSessionPhase.SPEAKING to "exp_04",
            VoiceSessionPhase.ERROR to "exp_05"
        )
        phaseToExp.forEach { (phase, exp) ->
            val snap = com.lanxin.android.builtin.pet.domain.VoiceSessionSnapshot(phase = phase)
            val msg = PetBridgeProtocol.sessionStateMessage(snap, timestampMs = 1L)
            assertEquals(exp, msg.payload[PetBridgeProtocol.KEY_CUBISM_EXPRESSION])
            val wire = PetBridgeProtocol.encode(msg)
            assertTrue(wire.contains("cubismExpression=$exp"))
        }
    }

    @Test
    fun `encodePlayMotion on DesktopPetBridge`() {
        val bridge = com.lanxin.android.builtin.pet.data.DesktopPetBridge {}
        val out = bridge.encodePlayMotion(MaoOfficialMotionCatalog.GROUP_TAP_BODY, 3)
        assertTrue(out.contains("command=PLAY_MOTION"))
        assertTrue(out.contains("motionGroup=TapBody"))
        assertTrue(out.contains("motionIndex=3"))
        assertEquals(out, bridge.lastOutbound)
    }
}
