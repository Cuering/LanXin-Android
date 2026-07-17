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

import com.lanxin.android.builtin.platform.tools.WebSearchTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DuckDuckGo lite HTML 解析冒烟（无网络）。
 */
class WebSearchLiteHtmlTest {

    @Test
    fun `parseLiteHtml extracts nofollow links`() {
        val html = """
            <html><body>
            <a rel="nofollow" href="https://example.com/a">Alpha Title</a>
            <td class="result-snippet">Alpha snippet here</td>
            <a rel="nofollow" href="https://duckduckgo.com/x">Skip me</a>
            <a rel="nofollow" href="https://example.com/b">Beta</a>
            <td class="result-snippet">Beta snippet</td>
            </body></html>
        """.trimIndent()
        val hits = WebSearchTool.parseLiteHtml(html, limit = 8)
        assertEquals(2, hits.size)
        assertEquals("https://example.com/a", hits[0].url)
        assertEquals("Alpha Title", hits[0].title)
        assertTrue(hits[0].snippet.contains("Alpha"))
        assertEquals("https://example.com/b", hits[1].url)
    }

    @Test
    fun `parseLiteHtml respects limit`() {
        val links = (1..10).joinToString("\n") { i ->
            """<a rel="nofollow" href="https://example.com/$i">T$i</a>"""
        }
        val hits = WebSearchTool.parseLiteHtml(links, limit = 3)
        assertEquals(3, hits.size)
    }
}
