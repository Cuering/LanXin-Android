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

package com.lanxin.android.builtin.unifiedsearch.domain

/**
 * 统一搜索四路路由。
 */
enum class SearchRoute(val label: String, val sectionTitle: String) {
    MEMORY("memory", "记忆"),
    KNOWLEDGE("knowledge", "知识"),
    CHAT("chat", "聊天历史"),
    UNIFIED_INBOX("unified_inbox", "跨会话")
}

/**
 * 单条命中（已规范化）。
 */
data class UnifiedHit(
    val key: String,
    val route: SearchRoute,
    val text: String,
    val subtitle: String = "",
    val score: Double = 0.0
)

/**
 * 单路检索结果。
 */
data class RouteResult(
    val route: SearchRoute,
    val hits: List<UnifiedHit>,
    val elapsedMs: Long = 0L,
    val timedOut: Boolean = false,
    val error: String? = null
) {
    val hitCount: Int get() = hits.size
}

/**
 * 四路融合后的总结果。
 */
data class UnifiedSearchResult(
    val query: String,
    val fused: List<UnifiedHit>,
    val routes: Map<SearchRoute, RouteResult>,
    val injectedPrompt: String,
    val totalElapsedMs: Long
) {
    fun hitCount(route: SearchRoute): Int = routes[route]?.hitCount ?: 0

    val routeHitCounts: Map<SearchRoute, Int>
        get() = SearchRoute.entries.associateWith { hitCount(it) }
}

/**
 * 注入结果：增强后的用户消息 + 本轮可点引用（记忆/知识）。
 */
data class InjectOutcome(
    val enrichedQuestion: String,
    val memoryHits: List<UnifiedHit> = emptyList(),
    val knowledgeHits: List<UnifiedHit> = emptyList()
) {
    val hasHits: Boolean get() = memoryHits.isNotEmpty() || knowledgeHits.isNotEmpty()
}
