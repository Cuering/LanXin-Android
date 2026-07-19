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

package com.lanxin.android.builtin.navigate

import com.lanxin.android.builtin.navigate.domain.GeoMath
import com.lanxin.android.builtin.navigate.domain.LocalAssistIntentRouter
import com.lanxin.android.builtin.navigate.domain.LocalAssistIntentRouter.AssistIntent
import com.lanxin.android.builtin.navigate.domain.NavigateConfig
import com.lanxin.android.builtin.navigate.domain.NavigationUriBuilder
import com.lanxin.android.builtin.navigate.domain.PoiCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 导航模块纯逻辑：距离、POI 类别、外链 URI、意图粗分。
 */
class NavigateDomainTest {

    @Test
    fun `tool names are navigate-scoped not scenic monolith`() {
        assertEquals("nearby_poi", NavigateConfig.NEARBY_POI_TOOL)
        assertEquals("open_navigation", NavigateConfig.OPEN_NAVIGATION_TOOL)
        assertEquals("hotel_price_lookup", NavigateConfig.HOTEL_PRICE_TOOL)
        assertEquals(3, NavigateConfig.ALL_TOOL_NAMES.size)
    }

    @Test
    fun `haversine known short distance`() {
        // 天安门附近两点约 1km 量级
        val d = GeoMath.distanceMeters(39.9087, 116.3975, 39.9163, 116.3972)
        assertTrue("got $d", d in 700.0..1200.0)
        assertEquals("约 1 公里附近", true, d < 2000)
    }

    @Test
    fun `format distance`() {
        assertEquals("500 米", GeoMath.formatDistance(500.0))
        assertTrue(GeoMath.formatDistance(1500.0).contains("公里"))
    }

    @Test
    fun `bearing label north`() {
        val b = GeoMath.bearingDegrees(0.0, 0.0, 1.0, 0.0)
        assertTrue(abs(b) < 1.0 || abs(b - 360) < 1.0)
        assertEquals("正北", GeoMath.bearingLabel(0.0))
    }

    @Test
    fun `poi category aliases`() {
        assertEquals(PoiCategory.RESTROOM, PoiCategory.parse("厕所"))
        assertEquals(PoiCategory.EXIT, PoiCategory.parse("出口"))
        assertEquals(PoiCategory.HOTEL, PoiCategory.parse("hotel"))
        assertNull(PoiCategory.parse("飞船"))
    }

    @Test
    fun `nav uri auto candidates non-empty`() {
        val list = NavigationUriBuilder.buildCandidates(
            NavigationUriBuilder.NavTarget(39.9, 116.4, "测试点"),
            NavigationUriBuilder.Provider.AUTO,
            NavigationUriBuilder.Mode.WALK
        )
        assertTrue(list.isNotEmpty())
        assertTrue(list.any { it.uri.startsWith("geo:") })
        assertTrue(list.any { it.uri.contains("amap") || it.provider.contains("amap") })
    }

    @Test
    fun `intent router splits guide vs navigate`() {
        assertEquals(AssistIntent.GUIDE, LocalAssistIntentRouter.classify("这是什么展品"))
        assertEquals(AssistIntent.GUIDE, LocalAssistIntentRouter.classify("讲讲这个景点的历史"))
        assertEquals(AssistIntent.NAVIGATE, LocalAssistIntentRouter.classify("附近洗手间在哪"))
        assertEquals(AssistIntent.NAVIGATE, LocalAssistIntentRouter.classify("酒店多少钱"))
        assertEquals(AssistIntent.NAVIGATE, LocalAssistIntentRouter.classify("带我去出口"))
        assertEquals(AssistIntent.UNKNOWN, LocalAssistIntentRouter.classify("今天天气怎么样"))
    }
}
