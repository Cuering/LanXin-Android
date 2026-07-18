package com.lanxin.android.builtin.pet

import com.lanxin.android.builtin.pet.domain.MaoOfficialMotionCatalog
import com.lanxin.android.builtin.pet.domain.PetExpressionController
import com.lanxin.android.builtin.pet.domain.TextExpressionMotionMapper
import com.lanxin.android.builtin.pet.domain.VoiceSessionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextExpressionMotionMapperTest {

    @Test
    fun `empty and unknown return null`() {
        assertNull(TextExpressionMotionMapper.match(""))
        assertNull(TextExpressionMotionMapper.match("   "))
        assertNull(TextExpressionMotionMapper.match("今天天气不错去散步吧"))
    }

    @Test
    fun `apology keywords map to APOLOGY without motion`() {
        val m = TextExpressionMotionMapper.match("抱歉，我没听清")
        assertNotNull(m)
        assertEquals("apology", m!!.ruleId)
        assertEquals(PetExpressionController.Expression.APOLOGY, m.expression)
        assertNull(m.motionGroup)
        assertEquals("抱歉", m.shortLabel)
    }

    @Test
    fun `joy keywords map to TAP_REACTION and TapBody special`() {
        val m = TextExpressionMotionMapper.match("哈哈太棒了！")
        assertNotNull(m)
        assertEquals("joy", m!!.ruleId)
        assertEquals(PetExpressionController.Expression.TAP_REACTION, m.expression)
        assertEquals(MaoOfficialMotionCatalog.GROUP_TAP_BODY, m.motionGroup)
        assertEquals(3, m.motionIndex)
        assertTrue(MaoOfficialMotionCatalog.isKnownExpression(
            PetExpressionController.cubismExpressionName(m.expression)
        ))
    }

    @Test
    fun `music keywords map to MUSIC_PEAK and Idle motion`() {
        val m = TextExpressionMotionMapper.match("放点音乐听歌吧")
        assertNotNull(m)
        assertEquals("music", m!!.ruleId)
        assertEquals(PetExpressionController.Expression.MUSIC_PEAK, m.expression)
        assertEquals(MaoOfficialMotionCatalog.GROUP_IDLE, m.motionGroup)
        assertEquals(1, m.motionIndex)
    }

    @Test
    fun `greeting case insensitive english`() {
        val m = TextExpressionMotionMapper.match("Hello there")
        assertNotNull(m)
        assertEquals("greeting", m!!.ruleId)
        assertEquals(PetExpressionController.Expression.IDLE_SMILE, m.expression)
    }

    @Test
    fun `priority apology over joy when both present`() {
        // apology 规则在 joy 前
        val m = TextExpressionMotionMapper.match("抱歉哈哈我搞砸了")
        assertNotNull(m)
        assertEquals("apology", m!!.ruleId)
    }

    @Test
    fun `overlaySpeakingPose keeps mouth animating`() {
        val base = PetExpressionController.poseFor(VoiceSessionPhase.SPEAKING)
        assertTrue(base.mouthAnimating)
        val over = TextExpressionMotionMapper.overlaySpeakingPose(
            base,
            VoiceSessionPhase.SPEAKING,
            "对不起啦"
        )
        assertEquals(PetExpressionController.Expression.APOLOGY, over.expression)
        assertTrue(over.mouthAnimating)
        assertTrue(over.mouthOpen > 0f)
        assertEquals("抱歉", over.shortLabel)
    }

    @Test
    fun `overlaySpeakingPose ignores non-speaking phase`() {
        val base = PetExpressionController.poseFor(VoiceSessionPhase.LISTENING)
        val over = TextExpressionMotionMapper.overlaySpeakingPose(
            base,
            VoiceSessionPhase.LISTENING,
            "哈哈开心"
        )
        assertEquals(base.expression, over.expression)
    }

    @Test
    fun `all rule ids stable and motions in catalog`() {
        val ids = TextExpressionMotionMapper.knownRuleIds()
        assertTrue(ids.contains("apology"))
        assertTrue(ids.contains("joy"))
        assertTrue(ids.contains("music"))
        // 用若干样本文本跑一遍，motion 合法
        listOf(
            "抱歉", "哈哈", "听歌", "点我", "让我想", "你好", "难过", "摸鱼"
        ).forEach { sample ->
            val m = TextExpressionMotionMapper.match(sample)
            assertNotNull("expected hit for $sample", m)
            m!!.motionGroup?.let { g ->
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
}
