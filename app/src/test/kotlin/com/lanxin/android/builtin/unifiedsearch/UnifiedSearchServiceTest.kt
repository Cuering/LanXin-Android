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

package com.lanxin.android.builtin.unifiedsearch

import com.lanxin.android.builtin.unifiedsearch.domain.RouteResult
import com.lanxin.android.builtin.unifiedsearch.domain.SearchRoute
import com.lanxin.android.builtin.unifiedsearch.domain.UnifiedHit
import com.lanxin.android.builtin.unifiedsearch.domain.UnifiedSearchService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UnifiedSearch 纯函数单测：RRF 融合 + 注入格式 + 零结果跳过。
 */
class UnifiedSearchServiceTest {

    private fun hit(
        key: String,
        route: SearchRoute,
        text: String,
        subtitle: String = ""
    ) = UnifiedHit(key = key, route = route, text = text, subtitle = subtitle)

    @Test
    fun fuseRoutes_prefers_items_appearing_in_multiple_routes_is_independent() {
        // 不同路由 key 前缀不同，不跨路由合并；保留各自排名
        val memory = RouteResult(
            SearchRoute.MEMORY,
            listOf(hit("memory:1", SearchRoute.MEMORY, "m1"), hit("memory:2", SearchRoute.MEMORY, "m2"))
        )
        val knowledge = RouteResult(
            SearchRoute.KNOWLEDGE,
            listOf(hit("knowledge:1", SearchRoute.KNOWLEDGE, "k1"))
        )
        val fused = UnifiedSearchService.fuseRoutes(listOf(memory, knowledge), topK = 3)
        assertEquals(3, fused.size)
        assertTrue(fused.any { it.key == "memory:1" })
        assertTrue(fused.any { it.key == "knowledge:1" })
    }

    @Test
    fun fuseRoutes_same_key_across_lists_boosts_score() {
        // 人为同一 key 出现在两路（例如测试场景）
        val a = RouteResult(
            SearchRoute.MEMORY,
            listOf(
                hit("shared", SearchRoute.MEMORY, "shared-text"),
                hit("only-a", SearchRoute.MEMORY, "a")
            )
        )
        val b = RouteResult(
            SearchRoute.KNOWLEDGE,
            listOf(
                hit("shared", SearchRoute.KNOWLEDGE, "shared-text"),
                hit("only-b", SearchRoute.KNOWLEDGE, "b")
            )
        )
        val fused = UnifiedSearchService.fuseRoutes(listOf(a, b), topK = 3, k = 60)
        assertEquals("shared", fused[0].key)
    }

    @Test
    fun fuseRoutes_skips_empty_routes() {
        val empty = RouteResult(SearchRoute.CHAT, emptyList())
        val mem = RouteResult(
            SearchRoute.MEMORY,
            listOf(hit("memory:1", SearchRoute.MEMORY, "hello"))
        )
        val fused = UnifiedSearchService.fuseRoutes(listOf(empty, mem), topK = 5)
        assertEquals(1, fused.size)
        assertEquals("memory:1", fused[0].key)
    }

    @Test
    fun fuseRoutes_respects_topK() {
        val hits = (1..12).map { hit("memory:$it", SearchRoute.MEMORY, "t$it") }
        val route = RouteResult(SearchRoute.MEMORY, hits)
        val fused = UnifiedSearchService.fuseRoutes(listOf(route), topK = 8)
        assertEquals(8, fused.size)
    }

    @Test
    fun fuseRoutes_all_empty_returns_empty() {
        val routes = SearchRoute.entries.map { RouteResult(it, emptyList()) }
        assertTrue(UnifiedSearchService.fuseRoutes(routes).isEmpty())
    }

    @Test
    fun buildInjectPrompt_skips_zero_result_routes() {
        val fused = listOf(
            hit("memory:1", SearchRoute.MEMORY, "记得哥哥喜欢猫", "偏好"),
            hit("knowledge:9", SearchRoute.KNOWLEDGE, "知识片段A")
        )
        val routes = mapOf(
            SearchRoute.MEMORY to RouteResult(SearchRoute.MEMORY, listOf(fused[0])),
            SearchRoute.KNOWLEDGE to RouteResult(SearchRoute.KNOWLEDGE, listOf(fused[1])),
            SearchRoute.CHAT to RouteResult(SearchRoute.CHAT, emptyList()),
            SearchRoute.UNIFIED_INBOX to RouteResult(SearchRoute.UNIFIED_INBOX, emptyList())
        )
        val prompt = UnifiedSearchService.buildInjectPrompt(fused, routes)
        assertTrue(prompt.contains("[统一参考]"))
        assertTrue(prompt.contains("━━━ 记忆 ━━━"))
        assertTrue(prompt.contains("━━━ 知识 ━━━"))
        assertFalse(prompt.contains("━━━ 聊天历史 ━━━"))
        assertFalse(prompt.contains("━━━ 跨会话 ━━━"))
        assertTrue(prompt.contains("记得哥哥喜欢猫"))
        assertTrue(prompt.contains("知识片段A"))
        assertTrue(prompt.contains("━━━ 参考结束 ━━━"))
    }

    @Test
    fun buildInjectPrompt_empty_fused_returns_blank() {
        assertEquals(
            "",
            UnifiedSearchService.buildInjectPrompt(emptyList(), emptyMap())
        )
    }

    @Test
    fun buildInjectPrompt_includes_subtitle_when_present() {
        val fused = listOf(
            hit("chat:1:2", SearchRoute.CHAT, "上次聊了天气", "2024-01-01 12:00")
        )
        val routes = mapOf(
            SearchRoute.CHAT to RouteResult(SearchRoute.CHAT, fused)
        )
        val prompt = UnifiedSearchService.buildInjectPrompt(fused, routes)
        assertTrue(prompt.contains("[2024-01-01 12:00]"))
        assertTrue(prompt.contains("上次聊了天气"))
    }

    @Test
    fun default_topK_is_8() {
        assertEquals(8, UnifiedSearchService.DEFAULT_TOP_K)
        assertEquals(60, UnifiedSearchService.RRF_K)
        assertEquals(2_000L, UnifiedSearchService.ROUTE_TIMEOUT_MS)
    }
}
