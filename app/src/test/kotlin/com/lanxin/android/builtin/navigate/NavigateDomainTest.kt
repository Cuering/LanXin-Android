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
import com.lanxin.android.builtin.navigate.domain.HotelPriceHints
import com.lanxin.android.builtin.navigate.domain.LocalAssistIntentRouter
import com.lanxin.android.builtin.navigate.domain.LocalAssistIntentRouter.AssistIntent
import com.lanxin.android.builtin.navigate.domain.NavigateConfig
import com.lanxin.android.builtin.navigate.domain.NavigateGate
import com.lanxin.android.builtin.navigate.domain.NavigationUriBuilder
import com.lanxin.android.builtin.navigate.domain.OverpassPoiParser
import com.lanxin.android.builtin.navigate.domain.PoiCategory
import com.lanxin.android.plugin.ToolDef
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 导航 Navigate 纯逻辑单测（与导游 Guide 拆开，禁止 ScenicGuide monlith 命名）。
 */
class NavigateDomainTest {

    private fun tool(name: String) = ToolDef(
        name = name,
        description = "d",
        parameters = buildJsonObject { },
        handler = { buildJsonObject { put("ok", true) } }
    )

    @Test
    fun `tool names are navigate-scoped not scenic monolith`() {
        assertEquals("nearby_poi", NavigateConfig.NEARBY_POI_TOOL)
        assertEquals("open_navigation", NavigateConfig.OPEN_NAVIGATION_TOOL)
        assertEquals("hotel_price_lookup", NavigateConfig.HOTEL_PRICE_TOOL)
        assertEquals(3, NavigateConfig.ALL_TOOL_NAMES.size)
    }

    @Test
    fun `haversine known short distance roughly 1km`() {
        val d = GeoMath.distanceMeters(0.0, 0.0, 0.0, 0.009)
        assertTrue("got $d", d in 900.0..1100.0)
    }

    @Test
    fun `bearing north is near 0`() {
        val b = GeoMath.bearingDegrees(0.0, 0.0, 1.0, 0.0)
        assertTrue("bearing=$b", b < 5.0 || b > 355.0)
        assertEquals("正北", GeoMath.bearingLabel(b))
    }

    @Test
    fun `bearing east is near 90`() {
        val b = GeoMath.bearingDegrees(0.0, 0.0, 0.0, 1.0)
        assertTrue("bearing=$b", abs(b - 90.0) < 5.0)
        assertEquals("正东", GeoMath.bearingLabel(b))
    }

    @Test
    fun `formatDistance switches at 1km`() {
        assertEquals("500 米", GeoMath.formatDistance(500.0))
        assertTrue(GeoMath.formatDistance(1500.0).contains("公里"))
    }

    @Test
    fun `coord validation`() {
        assertTrue(GeoMath.isValidCoord(39.9, 116.4))
        assertFalse(GeoMath.isValidCoord(100.0, 0.0))
        assertFalse(GeoMath.isValidCoord(0.0, 200.0))
    }

    @Test
    fun `parse category aliases`() {
        assertEquals(PoiCategory.RESTROOM, PoiCategory.parse("洗手间"))
        assertEquals(PoiCategory.RESTROOM, PoiCategory.parse("toilet"))
        assertEquals(PoiCategory.EXIT, PoiCategory.parse("出口"))
        assertEquals(PoiCategory.ELEVATOR, PoiCategory.parse("电梯"))
        assertEquals(PoiCategory.DINING, PoiCategory.parse("餐饮"))
        assertEquals(PoiCategory.HOTEL, PoiCategory.parse("hotel"))
        assertNull(PoiCategory.parse("spaceship"))
    }

    @Test
    fun `build auto candidates include amap baidu geo`() {
        val list = NavigationUriBuilder.buildCandidates(
            NavigationUriBuilder.NavTarget(39.9, 116.4, "天安门"),
            NavigationUriBuilder.Provider.AUTO,
            NavigationUriBuilder.Mode.WALK
        )
        assertTrue(list.any { it.provider == "amap" })
        assertTrue(list.any { it.provider == "baidu" })
        assertTrue(list.any { it.provider == "geo" })
        assertTrue(list.any { it.uri.startsWith("geo:") })
        assertTrue(list.first { it.provider == "amap" }.uri.contains("amapuri://"))
    }

    @Test
    fun `provider and mode parse`() {
        assertEquals(NavigationUriBuilder.Provider.AUTO, NavigationUriBuilder.Provider.parse(null))
        assertEquals(NavigationUriBuilder.Provider.AMAP, NavigationUriBuilder.Provider.parse("amap"))
        assertEquals(NavigationUriBuilder.Mode.WALK, NavigationUriBuilder.Mode.parse("步行"))
        assertEquals(NavigationUriBuilder.Mode.DRIVE, NavigationUriBuilder.Mode.parse("drive"))
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

    @Test
    fun `plugin id and default off`() {
        assertEquals("lanxin.navigate", NavigateConfig.PLUGIN_ID)
        assertFalse(NavigateConfig.DEFAULT_ENABLED)
    }

    @Test
    fun `gate filters poi when web off`() {
        val tools = listOf(
            tool(NavigateConfig.NEARBY_POI_TOOL),
            tool(NavigateConfig.OPEN_NAVIGATION_TOOL),
            tool(NavigateConfig.HOTEL_PRICE_TOOL),
            tool("web_search")
        )
        val filtered = NavigateGate.filterTools(
            tools = tools,
            pluginEnabled = true,
            masterEnabled = true,
            locationPrefsOpen = true,
            webSearchEnabled = false
        )
        assertFalse(filtered.any { it.name == NavigateConfig.NEARBY_POI_TOOL })
        assertFalse(filtered.any { it.name == NavigateConfig.HOTEL_PRICE_TOOL })
        assertTrue(filtered.any { it.name == NavigateConfig.OPEN_NAVIGATION_TOOL })
        assertTrue(filtered.any { it.name == "web_search" })
    }

    @Test
    fun `gate removes all when plugin off`() {
        val tools = listOf(
            tool(NavigateConfig.NEARBY_POI_TOOL),
            tool(NavigateConfig.OPEN_NAVIGATION_TOOL),
            tool("other")
        )
        val filtered = NavigateGate.filterTools(
            tools = tools,
            pluginEnabled = false,
            masterEnabled = true,
            locationPrefsOpen = true,
            webSearchEnabled = true
        )
        assertEquals(listOf("other"), filtered.map { it.name })
    }

    @Test
    fun `gate removes all when master off`() {
        val tools = listOf(
            tool(NavigateConfig.NEARBY_POI_TOOL),
            tool(NavigateConfig.OPEN_NAVIGATION_TOOL),
            tool("other")
        )
        val filtered = NavigateGate.filterTools(
            tools = tools,
            pluginEnabled = true,
            masterEnabled = false,
            locationPrefsOpen = true,
            webSearchEnabled = true
        )
        assertEquals(listOf("other"), filtered.map { it.name })
    }

    @Test
    fun `denyPoi when location closed`() {
        val denied = NavigateGate.denyPoiIfDisabled(
            pluginEnabled = true,
            masterEnabled = true,
            locationPrefsOpen = false,
            webSearchEnabled = true
        )
        assertNotNull(denied)
        assertEquals(NavigateGate.DENIED_LOCATION, denied!!["code"]?.toString()?.trim('"'))
    }

    @Test
    fun `denyPoi when plugin closed`() {
        val denied = NavigateGate.denyPoiIfDisabled(
            pluginEnabled = false,
            masterEnabled = true,
            locationPrefsOpen = true,
            webSearchEnabled = true
        )
        assertNotNull(denied)
        assertEquals(NavigateGate.DENIED_PLUGIN, denied!!["code"]?.toString()?.trim('"'))
    }

    @Test
    fun `denyNav requires plugin and master`() {
        assertNull(NavigateGate.denyNavIfDisabled(pluginEnabled = true, masterEnabled = true))
        assertNotNull(NavigateGate.denyNavIfDisabled(pluginEnabled = false, masterEnabled = true))
        assertNotNull(NavigateGate.denyNavIfDisabled(pluginEnabled = true, masterEnabled = false))
    }

    @Test
    fun `overpass query contains around and toilets`() {
        val q = OverpassPoiParser.buildQuery(PoiCategory.RESTROOM, 39.9, 116.4, 500)
        assertTrue(q.contains("around:500,39.9,116.4"))
        assertTrue(q.contains("toilets"))
        assertTrue(q.contains("[out:json]"))
    }

    @Test
    fun `parse overpass elements`() {
        val body = """
            {
              "elements": [
                {
                  "type": "node",
                  "id": 2,
                  "lat": 39.905,
                  "lon": 116.400,
                  "tags": { "name": "远厕所", "amenity": "toilets", "opening_hours": "24/7" }
                },
                {
                  "type": "node",
                  "id": 1,
                  "lat": 39.901,
                  "lon": 116.401,
                  "tags": { "name": "近厕所", "amenity": "toilets" }
                },
                {
                  "type": "way",
                  "id": 9,
                  "center": { "lat": 39.902, "lon": 116.402 },
                  "tags": { "amenity": "toilets" }
                }
              ]
            }
        """.trimIndent()
        val hits = OverpassPoiParser.parseElements(
            body = body,
            category = PoiCategory.RESTROOM,
            originLat = 39.900,
            originLon = 116.400
        )
        assertEquals(3, hits.size)
        assertEquals("近厕所", hits.minBy { it.distanceM }.name)
        assertTrue(hits.any { it.openingHours == "24/7" })
        assertTrue(hits.any { it.id == "way/9" })
    }

    @Test
    fun `hotel price hints extract`() {
        val hints = HotelPriceHints.extract("大床房 ¥328起 含早 299元/晚 RMB 400")
        assertTrue(hints.isNotEmpty())
        assertTrue(hints.any { it.contains("328") || it.contains("299") || it.contains("400") })
    }
}
