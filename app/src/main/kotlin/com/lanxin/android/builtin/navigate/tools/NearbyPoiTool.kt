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
import com.lanxin.android.builtin.navigate.domain.NavigateConfig
import com.lanxin.android.builtin.navigate.domain.OverpassPoiParser
import com.lanxin.android.builtin.navigate.domain.PoiCategory
import com.lanxin.android.data.network.NetworkClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 附近 POI 查询（导航 Navigate V1）。
 *
 * 数据源：OpenStreetMap Overpass API（无需 Key）。
 * 解析： [OverpassPoiParser]（纯函数可单测）。
 */
@Singleton
class NearbyPoiTool @Inject constructor(
    private val networkClient: NetworkClient
) {

    private val client get() = networkClient()

    suspend fun search(
        categoryRaw: String,
        lat: Double,
        lon: Double,
        radiusM: Int = NavigateConfig.DEFAULT_RADIUS_M,
        limit: Int = NavigateConfig.DEFAULT_LIMIT
    ): JsonObject = withContext(Dispatchers.IO) {
        if (!GeoMath.isValidCoord(lat, lon)) {
            return@withContext err("无效坐标 latitude/longitude")
        }
        val category = PoiCategory.parse(categoryRaw)
            ?: return@withContext err(
                "未知类别：$categoryRaw；可选：${PoiCategory.knownIds().joinToString()}"
            )
        val radius = radiusM.coerceIn(50, NavigateConfig.MAX_RADIUS_M)
        val safeLimit = limit.coerceIn(1, NavigateConfig.MAX_LIMIT)

        val query = OverpassPoiParser.buildQuery(category, lat, lon, radius)
        val raw = runCatching { postOverpass(query) }.getOrElse {
            return@withContext err("Overpass 请求失败：${it.message}")
        }
        val hits = runCatching {
            OverpassPoiParser.parseElements(raw, category, lat, lon)
        }.getOrElse {
            return@withContext err("解析 POI 失败：${it.message}")
        }
        val sorted = hits.sortedBy { it.distanceM }.take(safeLimit)

        buildJsonObject {
            put("ok", true)
            put("provider", "openstreetmap_overpass")
            put("category", category.id)
            put("category_label", category.displayName)
            put("origin_lat", lat)
            put("origin_lon", lon)
            put("radius_m", radius)
            put("returned", sorted.size)
            put(
                "results",
                buildJsonArray {
                    sorted.forEach { h ->
                        add(
                            buildJsonObject {
                                put("id", h.id)
                                put("name", h.name)
                                put("lat", h.lat)
                                put("lon", h.lon)
                                put("category", h.category)
                                put("distance_m", h.distanceM.toInt())
                                put("distance_label", GeoMath.formatDistance(h.distanceM))
                                put("bearing_deg", h.bearingDeg.toInt())
                                put("bearing_label", h.bearingLabel)
                                put(
                                    "direction_hint",
                                    "${h.bearingLabel}方向约 ${GeoMath.formatDistance(h.distanceM)}"
                                )
                                h.openingHours?.let { put("opening_hours", it) }
                                h.address?.let { put("address", it) }
                                h.phone?.let { put("phone", it) }
                                h.website?.let { put("website", it) }
                                h.tagsNote?.let { put("note", it) }
                                put(
                                    "nav_hint",
                                    "可用 open_navigation 调起外链导航" +
                                        "（lat=${h.lat}, lon=${h.lon}, name=${h.name}）"
                                )
                            }
                        )
                    }
                }
            )
            put(
                "disclaimer",
                "距离/方向为粗估；开放时间来自 OSM opening_hours 原文，请以现场为准。" +
                    "室内出口/电梯覆盖可能不全。"
            )
            if (sorted.isEmpty()) {
                put(
                    "hint",
                    "附近未找到「${category.displayName}」。可加大 radius_m，或换关键词用 web_search；" +
                        "出口/电梯室内数据常依赖场馆图（V2）。"
                )
            }
        }
    }

    private suspend fun postOverpass(query: String): String {
        val endpoints = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )
        var lastError: Throwable? = null
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        for (url in endpoints) {
            try {
                val response = client.post(url) {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("data=$encoded")
                    header(HttpHeaders.UserAgent, USER_AGENT)
                    header(HttpHeaders.Accept, "application/json")
                }
                if (response.status.isSuccess()) {
                    return response.bodyAsText()
                }
                lastError = IllegalStateException("HTTP ${response.status.value} @ $url")
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("Overpass 全部端点失败")
    }

    private fun err(message: String): JsonObject = buildJsonObject {
        put("ok", false)
        put("error", message)
    }

    companion object {
        private const val USER_AGENT =
            "LanXinAndroid/1.0 (navigate; +https://github.com/Cuering/LanXin-Android)"
    }
}
