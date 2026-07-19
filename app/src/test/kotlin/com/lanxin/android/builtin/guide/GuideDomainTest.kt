/*
 * Copyright 2025 LanXin Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lanxin.android.builtin.guide

import com.lanxin.android.builtin.guide.domain.GuideConfig
import com.lanxin.android.builtin.guide.domain.GuideGate
import com.lanxin.android.builtin.guide.domain.GuideLocationContext
import com.lanxin.android.builtin.guide.domain.GuideNavHandoff
import com.lanxin.android.builtin.guide.domain.GuidePromptBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导游 Guide 纯逻辑单测（与导航 Navigate 拆开，禁止 ScenicGuide monlith）。
 */
class GuideDomainTest {

    @Test
    fun `config is guide-scoped not scenic monolith`() {
        assertEquals("guide", GuideConfig.FEATURE_ID)
        assertEquals("companion_vision_explain", GuideConfig.VISION_FEATURE)
        assertEquals("explain_sight", GuideConfig.EXPLAIN_SIGHT_TOOL)
        assertEquals("open_navigation", GuideConfig.NAV_HANDOFF_TOOL)
    }

    @Test
    fun `location augment requires master and location prefs`() {
        assertTrue(GuideGate.canAugmentWithLocation(true, true))
        assertFalse(GuideGate.canAugmentWithLocation(false, true))
        assertFalse(GuideGate.canAugmentWithLocation(true, false))
        assertFalse(GuideGate.canAugmentWithLocation(false, false))
    }

    @Test
    fun `vision explain requires looking consent and camera`() {
        assertTrue(GuideGate.canExplainWithVision(true, true, true))
        assertFalse(GuideGate.canExplainWithVision(false, true, true))
        assertFalse(GuideGate.canExplainWithVision(true, false, true))
        assertFalse(GuideGate.canExplainWithVision(true, true, false))
    }

    @Test
    fun `location fix validates coords`() {
        assertNotNull(GuideLocationContext.fromMap(39.9, 116.4, 30.0, "gps"))
        assertNull(GuideLocationContext.fromMap(null, 116.4))
        assertNull(GuideLocationContext.fromMap(100.0, 0.0))
        assertNull(GuideLocationContext.fromMap(0.0, 200.0))
    }

    @Test
    fun `prompt snippet contains last known note`() {
        val fix = GuideLocationContext.Fix(39.9042, 116.4074, 25.0, "network")
        val s = GuideLocationContext.toPromptSnippet(fix)
        assertTrue(s.contains("39.9042"))
        assertTrue(s.contains("116.4074"))
        assertTrue(s.contains("last known"))
        assertTrue(s.contains("供参考"))
        assertEquals("", GuideLocationContext.snippetOrEmpty(null))
    }

    @Test
    fun `nav handoff detects intent`() {
        assertTrue(GuideNavHandoff.wantsNavigation("带我去那里"))
        assertTrue(GuideNavHandoff.wantsNavigation("怎么走过去"))
        assertTrue(GuideNavHandoff.wantsNavigation("open navigation please"))
        assertFalse(GuideNavHandoff.wantsNavigation("这是什么建筑"))
        assertFalse(GuideNavHandoff.wantsNavigation(""))
    }

    @Test
    fun `append handoff only when needed`() {
        val base = "这是一座古塔。"
        assertEquals(base, GuideNavHandoff.appendIfNeeded(base, "这是什么"))
        val with = GuideNavHandoff.appendIfNeeded(base, "导航过去", "古塔")
        assertTrue(with.contains("open_navigation") || with.contains("导航"))
        assertTrue(with.contains("古塔"))
        // 已含提示不重复
        val again = GuideNavHandoff.appendIfNeeded(with, "再导航一次")
        assertEquals(with, again)
    }

    @Test
    fun `prompt builder injects location and question`() {
        val q = GuidePromptBuilder.buildExplainQuestion(
            userQuestion = "这是哪",
            locationSnippet = "用户约在 1.0°N, 2.0°E 附近"
        )
        assertTrue(q.contains("兰心导游"))
        assertTrue(q.contains("位置上下文"))
        assertTrue(q.contains("这是哪"))
        assertTrue(q.contains("1.0°N"))
    }

    @Test
    fun `prompt builder default question when blank`() {
        val q = GuidePromptBuilder.buildExplainQuestion("", includeRoleHint = false)
        assertTrue(q.contains("请描述你现在看到的画面"))
    }
}
