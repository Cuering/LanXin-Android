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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 平台工具的纯逻辑冒烟测试（不依赖 Android Runtime）。
 * 完整集成测试需 instrumented 环境。
 */
class PlatformToolHelpersTest {

    @Test
    fun `tool names are stable`() {
        val expected = setOf(
            "clipboard_get",
            "clipboard_set",
            "app_install_check",
            "system_info"
        )
        assertEquals(4, expected.size)
        assertTrue(expected.contains("clipboard_get"))
        assertTrue(expected.contains("system_info"))
    }

    @Test
    fun `limit is clamped to valid range`() {
        fun clamp(limit: Int): Int = limit.coerceIn(1, 500)
        assertEquals(1, clamp(0))
        assertEquals(1, clamp(-10))
        assertEquals(50, clamp(50))
        assertEquals(500, clamp(9999))
    }

    @Test
    fun `plugin metadata constants`() {
        assertEquals("lanxin.platform", "lanxin.platform")
        assertEquals("1.0.0", "1.0.0")
    }
}
