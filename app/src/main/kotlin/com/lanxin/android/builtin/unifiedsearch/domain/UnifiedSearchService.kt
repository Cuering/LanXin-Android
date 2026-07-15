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

import android.util.Log
import com.lanxin.android.builtin.knowledge.domain.VectorPipeline
import com.lanxin.android.builtin.knowledge.domain.VectorSource
import com.lanxin.android.builtin.knowledge.domain.sparse.SparseRrf
import com.lanxin.android.core.engine.ChatHistoryProvider
import com.lanxin.android.plugins.chat.data.entity.MessageV2
import com.lanxin.android.plugins.memory.data.memory.MemoryEntity
import com.lanxin.android.plugins.memory.data.memory.MemoryRepository
import com.lanxin.android.plugins.memory.data.memory.MemoryType
import com.lanxin.android.plugins.unifiedinbox.data.CrossSessionEntity
import com.lanxin.android.plugins.unifiedinbox.data.CrossSessionRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 统一搜索：memory / knowledge / chat / unified_inbox 四路并行 + RRF(k=60) 融合。
 *
 * - 超时 2s 单路降级为空
 * - 零结果路由不注入
 * - Top-8 注入 prompt
 */
@Singleton
class UnifiedSearchService @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val vectorPipeline: VectorPipeline,
    private val chatHistoryProvider: ChatHistoryProvider,
    private val crossSessionRepository: CrossSessionRepository
) {
    @Volatile
    var enabled: Boolean = true

    @Volatile
    var memoryEnabled: Boolean = true

    @Volatile
    var knowledgeEnabled: Boolean = true

    @Volatile
    var chatEnabled: Boolean = true

    @Volatile
    var unifiedInboxEnabled: Boolean = true

    /** 最近一次检索的各路由命中数（设置页展示）。 */
    @Volatile
    var lastRouteHitCounts: Map<SearchRoute, Int> = emptyMap()
        private set

    @Volatile
    var lastQuery: String = ""
        private set

    @Volatile
    var lastElapsedMs: Long = 0L
        private set

    /**
     * 执行四路检索并 RRF 融合。
     */
    suspend fun search(
        query: String,
        topK: Int = DEFAULT_TOP_K,
        perRouteLimit: Int = DEFAULT_PER_ROUTE
    ): UnifiedSearchResult = withContext(Dispatchers.Default) {
        val q = query.trim()
        val started = System.currentTimeMillis()
        if (!enabled || q.isBlank()) {
            return@withContext emptyResult(q, started)
        }

        val routes = coroutineScope {
            val memDef = async { searchMemoryRoute(q, perRouteLimit) }
            val knowDef = async { searchKnowledgeRoute(q, perRouteLimit) }
            val chatDef = async { searchChatRoute(q, perRouteLimit) }
            val inboxDef = async { searchInboxRoute(q, perRouteLimit) }
            listOf(memDef.await(), knowDef.await(), chatDef.await(), inboxDef.await())
        }

        val routeMap = routes.associateBy { it.route }
        val fused = fuseRoutes(routes, topK)
        val prompt = buildInjectPrompt(fused, routeMap)
        val elapsed = System.currentTimeMillis() - started

        lastRouteHitCounts = SearchRoute.entries.associateWith { routeMap[it]?.hitCount ?: 0 }
        lastQuery = q
        lastElapsedMs = elapsed

        UnifiedSearchResult(
            query = q,
            fused = fused,
            routes = routeMap,
            injectedPrompt = prompt,
            totalElapsedMs = elapsed
        )
    }

    /**
     * 将统一参考注入到用户问题前（对标 MemoryInjector）。
     * 无命中或关闭时原样返回。
     */
    suspend fun inject(question: String, topK: Int = DEFAULT_TOP_K): String {
        if (!enabled || question.isBlank()) return question
        val result = search(question, topK = topK)
        if (result.fused.isEmpty() || result.injectedPrompt.isBlank()) return question
        return buildString {
            append(result.injectedPrompt)
            appendLine()
            append(question)
        }
    }

    private suspend fun searchMemoryRoute(query: String, limit: Int): RouteResult {
        if (!memoryEnabled) return RouteResult(SearchRoute.MEMORY, emptyList())
        return runRoute(SearchRoute.MEMORY) {
            val hits = memoryRepository.searchForInject(query, limit)
            hits.mapIndexed { index, entity -> entity.toHit(index) }
        }
    }

    private suspend fun searchKnowledgeRoute(query: String, limit: Int): RouteResult {
        if (!knowledgeEnabled) return RouteResult(SearchRoute.KNOWLEDGE, emptyList())
        return runRoute(SearchRoute.KNOWLEDGE) {
            val hits = try {
                vectorPipeline.searchHybrid(
                    query = query,
                    topK = limit,
                    source = VectorSource.KNOWLEDGE
                )
            } catch (e: Exception) {
                Log.w(TAG, "knowledge hybrid failed, fallback dense: ${e.message}")
                try {
                    vectorPipeline.search(
                        query = query,
                        topK = limit,
                        source = VectorSource.KNOWLEDGE
                    )
                } catch (e2: Exception) {
                    Log.w(TAG, "knowledge dense failed: ${e2.message}")
                    emptyList()
                }
            }
            hits.mapIndexed { index, hit ->
                UnifiedHit(
                    key = "knowledge:${hit.externalId}:${hit.id}",
                    route = SearchRoute.KNOWLEDGE,
                    text = hit.textPreview.trim(),
                    subtitle = "score=${"%.3f".format(hit.score)}",
                    score = hit.score.toDouble()
                ).takeIf { it.text.isNotBlank() }
            }.filterNotNull()
        }
    }

    private suspend fun searchChatRoute(query: String, limit: Int): RouteResult {
        if (!chatEnabled) return RouteResult(SearchRoute.CHAT, emptyList())
        return runRoute(SearchRoute.CHAT) {
            val messages = chatHistoryProvider.search(query).take(limit)
            messages.mapIndexed { index, msg -> msg.toHit(index) }
        }
    }

    private suspend fun searchInboxRoute(query: String, limit: Int): RouteResult {
        if (!unifiedInboxEnabled) return RouteResult(SearchRoute.UNIFIED_INBOX, emptyList())
        return runRoute(SearchRoute.UNIFIED_INBOX) {
            val hits = crossSessionRepository.searchForInject(query, limit)
            hits.mapIndexed { index, entity -> entity.toHit(index) }
        }
    }

    private suspend fun runRoute(
        route: SearchRoute,
        block: suspend () -> List<UnifiedHit>
    ): RouteResult {
        val started = System.currentTimeMillis()
        val timedOut = AtomicBoolean(false)
        return try {
            val hits = withTimeoutOrNull(ROUTE_TIMEOUT_MS) {
                block()
            }
            val elapsed = System.currentTimeMillis() - started
            if (hits == null) {
                timedOut.set(true)
                Log.w(TAG, "route ${route.label} timed out after ${ROUTE_TIMEOUT_MS}ms")
                RouteResult(
                    route = route,
                    hits = emptyList(),
                    elapsedMs = elapsed,
                    timedOut = true
                )
            } else {
                RouteResult(
                    route = route,
                    hits = hits,
                    elapsedMs = elapsed,
                    timedOut = false
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "route ${route.label} failed: ${e.message}")
            RouteResult(
                route = route,
                hits = emptyList(),
                elapsedMs = System.currentTimeMillis() - started,
                error = e.message
            )
        }
    }

    private fun emptyResult(query: String, started: Long): UnifiedSearchResult {
        val emptyRoutes = SearchRoute.entries.associateWith {
            RouteResult(it, emptyList())
        }
        lastRouteHitCounts = SearchRoute.entries.associateWith { 0 }
        lastQuery = query
        lastElapsedMs = System.currentTimeMillis() - started
        return UnifiedSearchResult(
            query = query,
            fused = emptyList(),
            routes = emptyRoutes,
            injectedPrompt = "",
            totalElapsedMs = lastElapsedMs
        )
    }

    companion object {
        private const val TAG = "UnifiedSearch"

        const val RRF_K = SparseRrf.DEFAULT_K
        const val DEFAULT_TOP_K = 8
        const val DEFAULT_PER_ROUTE = 10
        const val ROUTE_TIMEOUT_MS = 2_000L

        /**
         * 多路 RRF 融合；key 带路由前缀，跨路由不合并。
         */
        fun fuseRoutes(
            routes: List<RouteResult>,
            topK: Int = DEFAULT_TOP_K,
            k: Int = RRF_K
        ): List<UnifiedHit> {
            val nonEmpty = routes.filter { it.hits.isNotEmpty() }
            if (nonEmpty.isEmpty() || topK <= 0) return emptyList()

            val hitByKey = LinkedHashMap<String, UnifiedHit>()
            val rankedLists = nonEmpty.map { routeResult ->
                routeResult.hits.map { hit ->
                    hitByKey.putIfAbsent(hit.key, hit)
                    hit.key
                }
            }

            val fusedKeys = SparseRrf.fuse(rankedLists, k = k, topK = topK)
            val scores = mutableMapOf<String, Double>()
            for (list in rankedLists) {
                list.forEachIndexed { index, key ->
                    scores[key] = scores.getOrDefault(key, 0.0) + 1.0 / (k + index + 1)
                }
            }

            return fusedKeys.mapNotNull { key ->
                hitByKey[key]?.copy(score = scores[key] ?: 0.0)
            }
        }

        /**
         * 按架构文档 13.2 注入格式拼装；零结果路由跳过。
         */
        fun buildInjectPrompt(
            fused: List<UnifiedHit>,
            routes: Map<SearchRoute, RouteResult>
        ): String {
            if (fused.isEmpty()) return ""

            // 按路由分组（保留融合排序中的相对顺序）
            val byRoute = linkedMapOf<SearchRoute, MutableList<UnifiedHit>>()
            fused.forEach { hit ->
                byRoute.getOrPut(hit.route) { mutableListOf() }.add(hit)
            }

            return buildString {
                appendLine("[统一参考]")
                SearchRoute.entries.forEach { route ->
                    val items = byRoute[route].orEmpty()
                    if (items.isEmpty()) return@forEach
                    appendLine("━━━ ${route.sectionTitle} ━━━")
                    items.forEach { hit ->
                        val line = if (hit.subtitle.isBlank()) {
                            "- ${hit.text}"
                        } else {
                            "- [${hit.subtitle}] ${hit.text}"
                        }
                        appendLine(line)
                    }
                }
                appendLine("━━━ 参考结束 ━━━")
            }.trimEnd()
        }

        private fun MemoryEntity.toHit(rank: Int): UnifiedHit {
            val typeLabel = MemoryType.displayName(type)
            return UnifiedHit(
                key = "memory:$id",
                route = SearchRoute.MEMORY,
                text = content.trim(),
                subtitle = typeLabel,
                score = 1.0 / (rank + 1)
            )
        }

        private fun MessageV2.toHit(rank: Int): UnifiedHit {
            val snippet = content.replace('\n', ' ').trim().take(200)
            val timeLabel = formatEpochSeconds(createdAt)
            return UnifiedHit(
                key = "chat:$chatId:$id",
                route = SearchRoute.CHAT,
                text = snippet,
                subtitle = timeLabel,
                score = 1.0 / (rank + 1)
            )
        }

        private fun CrossSessionEntity.toHit(rank: Int): UnifiedHit {
            val title = sessionTitle.ifBlank { sessionId }
            val snippet = content.replace('\n', ' ').trim().take(160)
            return UnifiedHit(
                key = "inbox:$id",
                route = SearchRoute.UNIFIED_INBOX,
                text = snippet,
                subtitle = "$platform/$title",
                score = 1.0 / (rank + 1)
            )
        }

        private fun formatEpochSeconds(epochSec: Long): String {
            if (epochSec <= 0L) return ""
            val ms = if (epochSec < 10_000_000_000L) epochSec * 1000 else epochSec
            return try {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
            } catch (_: Exception) {
                ""
            }
        }
    }
}
