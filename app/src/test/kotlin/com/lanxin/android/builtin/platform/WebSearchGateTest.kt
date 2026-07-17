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

package com.lanxin.android.builtin.platform

import com.lanxin.android.builtin.platform.domain.WebSearchConfig
import com.lanxin.android.builtin.platform.domain.WebSearchGate
import com.lanxin.android.plugin.ToolDef
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WebSearch 门闸纯逻辑：默认关、过滤工具、拒绝执行。
 * 不碰 ChatRouter / needsTools。
 */
class WebSearchGateTest {

    private fun tool(name: String) = ToolDef(
        name = name,
        description = name,
        parameters = buildJsonObject { },
        handler = { buildJsonObject { put("ok", true) } }
    )

    @Test
    fun `default config is disabled`() {
        val c = WebSearchConfig()
        assertFalse(c.enabled)
        assertEquals(WebSearchConfig.DEFAULT_LIMIT, c.defaultLimit)
        assertEquals(WebSearchConfig.DEFAULT_REGION, c.region)
        assertFalse(WebSearchGate.isEnabled(c))
    }

    @Test
    fun `filterTools removes web_search when disabled`() {
        val tools = listOf(
            tool("clipboard_get"),
            tool(WebSearchConfig.TOOL_NAME),
            tool("system_info")
        )
        val filtered = WebSearchGate.filterTools(tools, WebSearchConfig(enabled = false))
        assertEquals(listOf("clipboard_get", "system_info"), filtered.map { it.name })
        assertFalse(filtered.any { it.name == WebSearchConfig.TOOL_NAME })
    }

    @Test
    fun `filterTools keeps web_search when enabled`() {
        val tools = listOf(
            tool("clipboard_get"),
            tool(WebSearchConfig.TOOL_NAME)
        )
        val filtered = WebSearchGate.filterTools(tools, WebSearchConfig(enabled = true))
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.name == WebSearchConfig.TOOL_NAME })
    }

    @Test
    fun `denyIfDisabled returns error when off`() {
        val denied = WebSearchGate.denyIfDisabled(WebSearchConfig(enabled = false))
        assertNotNull(denied)
        assertEquals(false, denied!!["ok"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull())
        assertEquals(
            WebSearchGate.DENIED_CODE,
            denied["code"]?.jsonPrimitive?.contentOrNull
        )
    }

    @Test
    fun `denyIfDisabled allows when on`() {
        assertNull(WebSearchGate.denyIfDisabled(WebSearchConfig(enabled = true)))
    }

    @Test
    fun `limit is clamped`() {
        assertEquals(1, WebSearchConfig(defaultLimit = 0).clampedLimit())
        assertEquals(20, WebSearchConfig(defaultLimit = 999).clampedLimit())
        assertEquals(8, WebSearchConfig(defaultLimit = 8).clampedLimit())
    }

    @Test
    fun `region blank falls back`() {
        assertEquals(WebSearchConfig.DEFAULT_REGION, WebSearchConfig(region = "  ").normalizedRegion())
        assertEquals("cn-zh", WebSearchConfig(region = "cn-zh").normalizedRegion())
    }
}
