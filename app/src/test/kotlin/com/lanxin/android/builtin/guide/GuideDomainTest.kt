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
import com.lanxin.android.plugin.ToolDef
import kotlinx.serialization.json.buildJsonObject
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
    fun `config is guide-scoped plugin default off`() {
        assertEquals("lanxin.guide", GuideConfig.PLUGIN_ID)
        assertEquals("guide", GuideConfig.FEATURE_ID)
        assertEquals("companion_vision_explain", GuideConfig.VISION_FEATURE)
        assertEquals("explain_sight", GuideConfig.EXPLAIN_SIGHT_TOOL)
        assertEquals("open_navigation", GuideConfig.NAV_HANDOFF_TOOL)
        assertFalse(GuideConfig.DEFAULT_ENABLED)
        assertEquals(setOf("explain_sight"), GuideConfig.ALL_TOOL_NAMES)
    }

    @Test
    fun `location augment requires plugin master and location prefs`() {
        assertTrue(GuideGate.canAugmentWithLocation(true, true, true))
        assertFalse(GuideGate.canAugmentWithLocation(false, true, true))
        assertFalse(GuideGate.canAugmentWithLocation(true, false, true))
        assertFalse(GuideGate.canAugmentWithLocation(true, true, false))
    }

    @Test
    fun `vision explain requires plugin looking consent and camera`() {
        assertTrue(GuideGate.canExplainWithVision(true, true, true, true))
        assertFalse(GuideGate.canExplainWithVision(false, true, true, true))
        assertFalse(GuideGate.canExplainWithVision(true, false, true, true))
        assertFalse(GuideGate.canExplainWithVision(true, true, false, true))
        assertFalse(GuideGate.canExplainWithVision(true, true, true, false))
    }

    @Test
    fun `vision entry hidden when plugin default off`() {
        assertFalse(
            GuideGate.canShowVisionEntry(
                pluginEnabled = GuideConfig.DEFAULT_ENABLED,
                masterEnabled = true
            )
        )
        assertTrue(GuideGate.canShowVisionEntry(true, true))
        assertFalse(GuideGate.canShowVisionEntry(true, false))
    }

    @Test
    fun `filterTools strips explain_sight when plugin off`() {
        val tools = listOf(
            ToolDef("explain_sight", "d", buildJsonObject { }, handler = { buildJsonObject { } }),
            ToolDef("other", "d", buildJsonObject { }, handler = { buildJsonObject { } })
        )
        val off = GuideGate.filterTools(tools, pluginEnabled = false, masterEnabled = true)
        assertEquals(listOf("other"), off.map { it.name })
        val on = GuideGate.filterTools(tools, pluginEnabled = true, masterEnabled = true)
        assertEquals(2, on.size)
    }

    @Test
    fun `denyExplain when plugin off`() {
        val denied = GuideGate.denyExplainIfDisabled(false, true)
        assertNotNull(denied)
        assertEquals(GuideGate.DENIED_PLUGIN, denied!!["code"]?.toString()?.trim('"'))
        assertNull(GuideGate.denyExplainIfDisabled(true, true))
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
        assertTrue(GuideNavHandoff.wantsNavigation("怎么走"))
        assertTrue(GuideNavHandoff.wantsNavigation("please navigate there"))
        assertTrue(GuideNavHandoff.wantsNavigation("open navigation"))
        assertFalse(GuideNavHandoff.wantsNavigation("这是什么建筑"))
        assertFalse(GuideNavHandoff.wantsNavigation("讲讲历史"))
        assertFalse(GuideNavHandoff.wantsNavigation(""))
    }

    @Test
    fun `append handoff only when needed`() {
        val base = "这是一座古塔。"
        assertEquals(base, GuideNavHandoff.appendIfNeeded(base, "这是什么"))
        val with = GuideNavHandoff.appendIfNeeded(base, "导航过去", "古塔")
        assertTrue(with.contains("open_navigation") || with.contains("导航"))
        assertTrue(with.contains("古塔"))
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
