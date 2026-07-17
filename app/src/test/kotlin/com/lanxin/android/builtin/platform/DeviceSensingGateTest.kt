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

import com.lanxin.android.builtin.platform.domain.DeviceSensingConfig
import com.lanxin.android.builtin.platform.domain.DeviceSensingGate
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
 * 设备感知门闸纯逻辑：默认关、过滤工具、拒绝执行。
 * 不碰 ChatRouter / needsTools。
 */
class DeviceSensingGateTest {

    private fun tool(name: String) = ToolDef(
        name = name,
        description = name,
        parameters = buildJsonObject { },
        handler = { buildJsonObject { put("ok", true) } }
    )

    @Test
    fun `default config is disabled`() {
        val c = DeviceSensingConfig()
        assertFalse(c.enabled)
        assertFalse(DeviceSensingGate.isEnabled(c))
        assertEquals(DeviceSensingConfig.TOOL_NAME, "system_info")
    }

    @Test
    fun `filterTools removes system_info when disabled`() {
        val tools = listOf(
            tool("clipboard_get"),
            tool(DeviceSensingConfig.TOOL_NAME),
            tool("web_search")
        )
        val filtered = DeviceSensingGate.filterTools(
            tools,
            DeviceSensingConfig(enabled = false)
        )
        assertEquals(listOf("clipboard_get", "web_search"), filtered.map { it.name })
        assertFalse(filtered.any { it.name == DeviceSensingConfig.TOOL_NAME })
    }

    @Test
    fun `filterTools keeps system_info when enabled`() {
        val tools = listOf(
            tool("clipboard_get"),
            tool(DeviceSensingConfig.TOOL_NAME)
        )
        val filtered = DeviceSensingGate.filterTools(
            tools,
            DeviceSensingConfig(enabled = true)
        )
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.name == DeviceSensingConfig.TOOL_NAME })
    }

    @Test
    fun `denyIfDisabled returns error when off`() {
        val denied = DeviceSensingGate.denyIfDisabled(DeviceSensingConfig(enabled = false))
        assertNotNull(denied)
        assertEquals(false, denied!!["ok"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull())
        assertEquals(
            DeviceSensingGate.DENIED_CODE,
            denied["code"]?.jsonPrimitive?.contentOrNull
        )
    }

    @Test
    fun `denyIfDisabled allows when on`() {
        assertNull(DeviceSensingGate.denyIfDisabled(DeviceSensingConfig(enabled = true)))
    }

    @Test
    fun `chains with WebSearchGate both disabled`() {
        val tools = listOf(
            tool("clipboard_get"),
            tool(DeviceSensingConfig.TOOL_NAME),
            tool(WebSearchConfig.TOOL_NAME),
            tool("app_intent")
        )
        val afterWeb = WebSearchGate.filterTools(tools, WebSearchConfig(enabled = false))
        val afterSense = DeviceSensingGate.filterTools(
            afterWeb,
            DeviceSensingConfig(enabled = false)
        )
        assertEquals(listOf("clipboard_get", "app_intent"), afterSense.map { it.name })
    }

    @Test
    fun `chains with WebSearchGate only system_info enabled`() {
        val tools = listOf(
            tool(DeviceSensingConfig.TOOL_NAME),
            tool(WebSearchConfig.TOOL_NAME)
        )
        val afterWeb = WebSearchGate.filterTools(tools, WebSearchConfig(enabled = false))
        val afterSense = DeviceSensingGate.filterTools(
            afterWeb,
            DeviceSensingConfig(enabled = true)
        )
        assertEquals(listOf(DeviceSensingConfig.TOOL_NAME), afterSense.map { it.name })
    }
}
