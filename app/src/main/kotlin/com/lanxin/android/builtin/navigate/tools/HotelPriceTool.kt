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

package com.lanxin.android.builtin.navigate.tools

import com.lanxin.android.builtin.navigate.domain.GeoMath
import com.lanxin.android.builtin.navigate.domain.HotelPriceHints
import com.lanxin.android.builtin.platform.tools.WebSearchTool
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 酒店/房间价位联网检索摘要（V1）。
 *
 * 基于 [WebSearchTool] 聚合公开网页摘要；**不**对接 OTA 预订 API。
 * 回答必须声明：价格供参考、以平台实时为准。
 */
@Singleton
class HotelPriceTool @Inject constructor(
    private val webSearchTool: WebSearchTool
) {

    suspend fun lookup(
        query: String,
        lat: Double? = null,
        lon: Double? = null,
        limit: Int = 6
    ): JsonObject {
        val q = query.trim()
        if (q.isEmpty()) {
            return buildJsonObject {
                put("ok", false)
                put("error", "query 必填（酒店名或「附近酒店 价位」）")
            }
        }
        val locHint = if (
            lat != null && lon != null && GeoMath.isValidCoord(lat, lon)
        ) {
            " 附近坐标${"%.4f".format(lat)},${"%.4f".format(lon)}"
        } else {
            ""
        }
        val searchQuery = buildString {
            append(q)
            if (!q.contains("酒店") && !q.contains("hotel", ignoreCase = true)) {
                append(" 酒店")
            }
            if (!q.contains("价格") && !q.contains("价") && !q.contains("price", ignoreCase = true)) {
                append(" 房价 价格")
            }
            append(locHint)
        }
        val raw = webSearchTool.search(query = searchQuery, limit = limit.coerceIn(1, 12))
        if (raw["ok"]?.jsonPrimitive?.contentOrNull == "false") {
            return buildJsonObject {
                put("ok", false)
                put("error", raw["error"]?.jsonPrimitive?.contentOrNull ?: "搜索失败")
                put("code", "hotel_price_search_failed")
            }
        }
        val results = raw["results"] as? JsonArray
        val snippets = mutableListOf<JsonObject>()
        val priceHints = mutableListOf<String>()
        if (results != null) {
            for (el in results) {
                val obj = el as? JsonObject ?: continue
                val title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val url = obj["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val snippet = obj["snippet"]?.jsonPrimitive?.contentOrNull.orEmpty()
                snippets += buildJsonObject {
                    put("title", title)
                    put("url", url)
                    put("snippet", snippet)
                }
                HotelPriceHints.extract("$title $snippet").forEach { priceHints += it }
            }
        }
        return buildJsonObject {
            put("ok", true)
            put("query", q)
            put("search_query", searchQuery)
            put("provider", raw["provider"]?.jsonPrimitive?.contentOrNull ?: "web_search")
            put("returned", snippets.size)
            put(
                "results",
                buildJsonArray { snippets.forEach { add(it) } }
            )
            put(
                "price_mentions",
                buildJsonArray {
                    priceHints.distinct().take(12).forEach {
                        add(kotlinx.serialization.json.JsonPrimitive(it))
                    }
                }
            )
            put(
                "disclaimer",
                "价格供参考、以平台实时为准；本结果来自公开网页摘要，非实时订房报价，不构成预订承诺。"
            )
            put(
                "suggest",
                "可结合 nearby_poi category=hotel 看附近酒店位置，再用 open_navigation 导航过去。"
            )
        }
    }
}

