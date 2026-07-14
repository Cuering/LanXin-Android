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

package com.lanxin.android.builtin.platform.tools

import com.lanxin.android.data.network.NetworkClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 网络搜索工具。
 *
 * 优先请求 DuckDuckGo Instant Answer API（JSON，无需 Key）；
 * 失败时回退到 HTML 轻量解析（lite 页）。
 * 网络层复用 [NetworkClient]（Ktor），不引入 OkHttp。
 */
@Singleton
class WebSearchTool @Inject constructor(
    private val networkClient: NetworkClient
) {

    private val client get() = networkClient()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun search(
        query: String,
        limit: Int = 8,
        region: String = "wt-wt"
    ): JsonObject = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) {
            return@withContext err("query 必填")
        }
        val safeLimit = limit.coerceIn(1, 20)

        val instant = runCatching { searchInstantAnswer(q, safeLimit) }.getOrNull()
        if (instant != null && instant.isNotEmpty()) {
            return@withContext buildJsonObject {
                put("ok", true)
                put("provider", "duckduckgo_instant")
                put("query", q)
                put("returned", instant.size)
                put("results", resultsArray(instant))
            }
        }

        val htmlResults = runCatching { searchHtmlLite(q, safeLimit, region) }.getOrElse {
            return@withContext err("搜索失败：${it.message}")
        }
        if (htmlResults.isEmpty()) {
            return@withContext buildJsonObject {
                put("ok", true)
                put("provider", "duckduckgo_lite")
                put("query", q)
                put("returned", 0)
                put("results", buildJsonArray { })
                put("hint", "未解析到结果，可换关键词重试")
            }
        }
        buildJsonObject {
            put("ok", true)
            put("provider", "duckduckgo_lite")
            put("query", q)
            put("returned", htmlResults.size)
            put("results", resultsArray(htmlResults))
        }
    }

    private suspend fun searchInstantAnswer(query: String, limit: Int): List<SearchHit> {
        val response = client.get("https://api.duckduckgo.com/") {
            parameter("q", query)
            parameter("format", "json")
            parameter("no_html", "1")
            parameter("skip_disambig", "1")
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "application/json")
        }
        if (!response.status.isSuccess()) {
            error("Instant Answer HTTP ${response.status.value}")
        }
        val body = response.bodyAsText()
        val root = json.parseToJsonElement(body).jsonObject
        val hits = mutableListOf<SearchHit>()

        val abstractText = root["AbstractText"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val abstractUrl = root["AbstractURL"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val heading = root["Heading"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (abstractText.isNotBlank()) {
            hits += SearchHit(
                title = heading.ifBlank { query },
                url = abstractUrl,
                snippet = abstractText
            )
        }

        val answer = root["Answer"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (answer.isNotBlank() && hits.none { it.snippet == answer }) {
            hits += SearchHit(
                title = heading.ifBlank { "Answer" },
                url = abstractUrl,
                snippet = answer
            )
        }

        fun walkRelated(arr: JsonArray?) {
            if (arr == null) return
            for (el in arr) {
                if (hits.size >= limit) return
                val obj = el as? JsonObject ?: continue
                val topics = obj["Topics"]?.jsonArray
                if (topics != null) {
                    walkRelated(topics)
                    continue
                }
                val text = obj["Text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val firstUrl = obj["FirstURL"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (text.isBlank() && firstUrl.isBlank()) continue
                hits += SearchHit(
                    title = text.substringBefore(" - ").ifBlank { text }.take(120),
                    url = firstUrl,
                    snippet = text
                )
            }
        }
        walkRelated(root["RelatedTopics"]?.jsonArray)

        val results = root["Results"]?.jsonArray
        if (results != null) {
            for (el in results) {
                if (hits.size >= limit) break
                val obj = el as? JsonObject ?: continue
                val text = obj["Text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val firstUrl = obj["FirstURL"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (text.isBlank() && firstUrl.isBlank()) continue
                hits += SearchHit(title = text.take(120), url = firstUrl, snippet = text)
            }
        }

        return hits.distinctBy { it.url.ifBlank { it.snippet } }.take(limit)
    }

    private suspend fun searchHtmlLite(query: String, limit: Int, region: String): List<SearchHit> {
        val response = client.get("https://lite.duckduckgo.com/lite/") {
            parameter("q", query)
            parameter("kl", region)
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "text/html")
        }
        if (!response.status.isSuccess()) {
            error("DuckDuckGo lite HTTP ${response.status.value}")
        }
        val html = response.bodyAsText()
        return parseLiteHtml(html, limit)
    }

    private fun resultsArray(hits: List<SearchHit>) = buildJsonArray {
        hits.forEach { h ->
            add(
                buildJsonObject {
                    put("title", h.title)
                    put("url", h.url)
                    put("snippet", h.snippet)
                }
            )
        }
    }

    private fun err(message: String): JsonObject = buildJsonObject {
        put("ok", false)
        put("error", message)
    }

    data class SearchHit(
        val title: String,
        val url: String,
        val snippet: String
    )

    companion object {
        private const val USER_AGENT =
            "LanXinAndroid/1.0 (platform-tools; +https://github.com/Cuering/LanXin-Android)"

        /**
         * 解析 DuckDuckGo lite HTML：
         * 结果链接形如 `<a rel="nofollow" href="https://...">title</a>`，
         * 摘要在后续 `result-snippet` 类中。
         */
        fun parseLiteHtml(html: String, limit: Int): List<SearchHit> {
            val hits = mutableListOf<SearchHit>()
            val linkRegex = Regex(
                """<a[^>]*rel="nofollow"[^>]*href="(https?://[^"]+)"[^>]*>(.*?)</a>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            val snippetRegex = Regex(
                """class="result-snippet"[^>]*>(.*?)</(?:td|span|div)>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            val links = linkRegex.findAll(html).toList()
            val snippets = snippetRegex.findAll(html).map { stripTags(it.groupValues[1]) }.toList()

            var snippetIdx = 0
            for (m in links) {
                if (hits.size >= limit) break
                val url = decodeHtml(m.groupValues[1]).trim()
                if (url.contains("duckduckgo.com", ignoreCase = true)) continue
                val title = stripTags(m.groupValues[2]).ifBlank { url }
                val snippet = snippets.getOrNull(snippetIdx).orEmpty()
                snippetIdx++
                hits += SearchHit(title = title, url = url, snippet = snippet)
            }

            if (hits.isEmpty()) {
                val anyLink = Regex(
                    """href="(https?://[^"]+)"[^>]*>(.*?)</a>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                )
                for (m in anyLink.findAll(html)) {
                    if (hits.size >= limit) break
                    val url = decodeHtml(m.groupValues[1])
                    if (url.contains("duckduckgo.com", ignoreCase = true)) continue
                    val title = stripTags(m.groupValues[2]).ifBlank { url }
                    hits += SearchHit(title = title, url = url, snippet = "")
                }
            }
            return hits.distinctBy { it.url }.take(limit)
        }

        private fun stripTags(raw: String): String =
            decodeHtml(raw.replace(Regex("<[^>]+>"), " "))
                .replace(Regex("\\s+"), " ")
                .trim()

        private fun decodeHtml(s: String): String =
            s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ")
    }
}
