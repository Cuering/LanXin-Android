package com.lanxin.android.builtin.pet

import com.lanxin.android.builtin.pet.domain.MaoOfficialMotionCatalog
import com.lanxin.android.builtin.pet.domain.MoodTagMapper
import com.lanxin.android.builtin.pet.domain.PetExpressionController
import com.lanxin.android.builtin.pet.domain.TextExpressionMotionMapper
import com.lanxin.android.builtin.pet.domain.VoiceSessionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * mood 标签只映射 Mao 现有 exp/motion；非法 mood 忽略。
 */
class MoodTagMapperTest {

    @Test
    fun `stripTags removes mood markers`() {
        assertEquals(
            "你好呀",
            MoodTagMapper.stripTags("你好呀[[mood=joy]]")
        )
        assertEquals(
            "前 后",
            MoodTagMapper.stripTags("前[[mood=sorry]]后")
        )
        assertEquals("plain", MoodTagMapper.stripTags("plain"))
    }

    @Test
    fun `match joy maps TAP_REACTION and TapBody special`() {
        val m = MoodTagMapper.match("今天真开心[[mood=joy]]")
        assertNotNull(m)
        assertEquals("mood:joy", m!!.ruleId)
        assertEquals(PetExpressionController.Expression.TAP_REACTION, m.expression)
        assertEquals(MaoOfficialMotionCatalog.GROUP_TAP_BODY, m.motionGroup)
        assertEquals(3, m.motionIndex)
        assertTrue(
            MaoOfficialMotionCatalog.isKnownExpression(
                PetExpressionController.cubismExpressionName(m.expression)
            )
        )
    }

    @Test
    fun `match sorry maps APOLOGY without inventing motion`() {
        val m = MoodTagMapper.match("[[mood=sorry]]我没听清")
        assertNotNull(m)
        assertEquals("mood:sorry", m!!.ruleId)
        assertEquals(PetExpressionController.Expression.APOLOGY, m.expression)
        assertNull(m.motionGroup)
    }

    @Test
    fun `alias happy normalizes to joy`() {
        assertEquals("joy", MoodTagMapper.normalize("happy"))
        val m = MoodTagMapper.match("[[mood=Happy]]耶")
        assertNotNull(m)
        assertEquals("mood:joy", m!!.ruleId)
    }

    @Test
    fun `unknown mood ignored`() {
        assertNull(MoodTagMapper.normalize("tsundere"))
        assertNull(MoodTagMapper.match("[[mood=tsundere]]撒娇一下"))
        assertFalse(MoodTagMapper.isAllowedMood("worry"))
    }

    @Test
    fun `first valid mood wins`() {
        val m = MoodTagMapper.match("[[mood=bogus]][[mood=think]]后面")
        assertNotNull(m)
        assertEquals("mood:think", m!!.ruleId)
        assertEquals(PetExpressionController.Expression.THINKING, m.expression)
        assertNull(m.motionGroup)
    }

    @Test
    fun `all allowed moods map only to catalog resources`() {
        MoodTagMapper.knownMoods().forEach { mood ->
            val m = MoodTagMapper.match("x[[mood=$mood]]")
            assertNotNull("expected $mood", m)
            val expName = PetExpressionController.cubismExpressionName(m!!.expression)
            assertTrue("$mood → $expName", MaoOfficialMotionCatalog.isKnownExpression(expName))
            m.motionGroup?.let { g ->
                assertTrue(
                    g == MaoOfficialMotionCatalog.GROUP_IDLE ||
                        g == MaoOfficialMotionCatalog.GROUP_TAP_BODY
                )
                m.motionIndex?.let { idx ->
                    val max = if (g == MaoOfficialMotionCatalog.GROUP_IDLE) {
                        MaoOfficialMotionCatalog.idleMotionCount()
                    } else {
                        MaoOfficialMotionCatalog.tapBodyMotionCount()
                    }
                    assertTrue(idx in 0 until max)
                }
            }
        }
    }

    @Test
    fun `TextExpressionMotionMapper prefers mood tag over keywords`() {
        // 正文含「抱歉」关键词，但 mood=joy 应优先
        val m = TextExpressionMotionMapper.match("抱歉啦[[mood=joy]]其实没事")
        assertNotNull(m)
        assertEquals("mood:joy", m!!.ruleId)
        assertEquals(PetExpressionController.Expression.TAP_REACTION, m.expression)
    }

    @Test
    fun `keywords still work after strip when no mood`() {
        val m = TextExpressionMotionMapper.match("哈哈太棒了")
        assertNotNull(m)
        assertEquals("joy", m!!.ruleId)
    }

    @Test
    fun `overlaySpeakingPose uses mood then keeps mouth`() {
        val base = PetExpressionController.poseFor(VoiceSessionPhase.SPEAKING)
        val over = TextExpressionMotionMapper.overlaySpeakingPose(
            base,
            VoiceSessionPhase.SPEAKING,
            "[[mood=music]]放首歌"
        )
        assertEquals(PetExpressionController.Expression.MUSIC_PEAK, over.expression)
        assertTrue(over.mouthAnimating)
        assertEquals("律动", over.shortLabel)
    }
}
