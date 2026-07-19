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

package com.lanxin.android.builtin.navigate.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Overpass 查询与 JSON 解析（纯函数，可单测，无网络）。
 */
object OverpassPoiParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    data class PoiHit(
        val id: String,
        val name: String,
        val lat: Double,
        val lon: Double,
        val category: String,
        val distanceM: Double,
        val bearingDeg: Double,
        val bearingLabel: String,
        val openingHours: String?,
        val address: String?,
        val phone: String?,
        val website: String?,
        val tagsNote: String?
    )

    fun buildQuery(
        category: PoiCategory,
        lat: Double,
        lon: Double,
        radiusM: Int
    ): String {
        val around = "around:$radiusM,$lat,$lon"
        val body = category.overpassClauses
            .joinToString("\n  ") { it.replace("{{around}}", around) }
        val outLimit = NavigateConfig.MAX_LIMIT * 3
        return buildString {
            appendLine("[out:json][timeout:20];")
            appendLine("(")
            appendLine("  $body")
            appendLine(");")
            append("out center tags $outLimit;")
        }
    }

    fun parseElements(
        body: String,
        category: PoiCategory,
        originLat: Double,
        originLon: Double
    ): List<PoiHit> {
        val root = json.parseToJsonElement(body).jsonObject
        val elements = root["elements"]?.jsonArray ?: return emptyList()
        val hits = mutableListOf<PoiHit>()
        for (el in elements) {
            val obj = el as? JsonObject ?: continue
            val type = obj["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val idNum = obj["id"]?.jsonPrimitive?.longOrNull ?: continue
            val tags = obj["tags"]?.jsonObject
            val (plat, plon) = resolveLatLon(obj) ?: continue
            if (!GeoMath.isValidCoord(plat, plon)) continue
            val name = tags?.get("name")?.jsonPrimitive?.contentOrNull
                ?: tags?.get("name:zh")?.jsonPrimitive?.contentOrNull
                ?: tags?.get("name:en")?.jsonPrimitive?.contentOrNull
                ?: tags?.get("ref")?.jsonPrimitive?.contentOrNull
                ?: defaultName(category, tags)
            val dist = GeoMath.distanceMeters(originLat, originLon, plat, plon)
            val bearing = GeoMath.bearingDegrees(originLat, originLon, plat, plon)
            val opening = tags?.get("opening_hours")?.jsonPrimitive?.contentOrNull
            val phone = tags?.get("phone")?.jsonPrimitive?.contentOrNull
                ?: tags?.get("contact:phone")?.jsonPrimitive?.contentOrNull
            val website = tags?.get("website")?.jsonPrimitive?.contentOrNull
                ?: tags?.get("contact:website")?.jsonPrimitive?.contentOrNull
            val address = buildAddress(tags)
            val note = listOfNotNull(
                tags?.get("wheelchair")?.jsonPrimitive?.contentOrNull?.let { "无障碍:$it" },
                tags?.get("fee")?.jsonPrimitive?.contentOrNull?.let { "收费:$it" },
                tags?.get("operator")?.jsonPrimitive?.contentOrNull?.let { "运营:$it" }
            ).joinToString("；").ifBlank { null }

            hits += PoiHit(
                id = "$type/$idNum",
                name = name,
                lat = plat,
                lon = plon,
                category = category.id,
                distanceM = dist,
                bearingDeg = bearing,
                bearingLabel = GeoMath.bearingLabel(bearing),
                openingHours = opening,
                address = address,
                phone = phone,
                website = website,
                tagsNote = note
            )
        }
        return hits.distinctBy { it.id }
    }

    private fun resolveLatLon(obj: JsonObject): Pair<Double, Double>? {
        val lat = obj["lat"]?.jsonPrimitive?.doubleOrNull
        val lon = obj["lon"]?.jsonPrimitive?.doubleOrNull
        if (lat != null && lon != null) return lat to lon
        val center = obj["center"]?.jsonObject
        val clat = center?.get("lat")?.jsonPrimitive?.doubleOrNull
        val clon = center?.get("lon")?.jsonPrimitive?.doubleOrNull
        if (clat != null && clon != null) return clat to clon
        return null
    }

    private fun buildAddress(tags: JsonObject?): String? {
        if (tags == null) return null
        val full = tags["addr:full"]?.jsonPrimitive?.contentOrNull
        if (!full.isNullOrBlank()) return full
        val parts = listOfNotNull(
            tags["addr:city"]?.jsonPrimitive?.contentOrNull,
            tags["addr:district"]?.jsonPrimitive?.contentOrNull,
            tags["addr:street"]?.jsonPrimitive?.contentOrNull,
            tags["addr:housenumber"]?.jsonPrimitive?.contentOrNull
        )
        return parts.joinToString("").ifBlank { null }
    }

    private fun defaultName(category: PoiCategory, tags: JsonObject?): String {
        val amenity = tags?.get("amenity")?.jsonPrimitive?.contentOrNull
        val tourism = tags?.get("tourism")?.jsonPrimitive?.contentOrNull
        val entrance = tags?.get("entrance")?.jsonPrimitive?.contentOrNull
        return when {
            !amenity.isNullOrBlank() -> "${category.displayName}($amenity)"
            !tourism.isNullOrBlank() -> "${category.displayName}($tourism)"
            !entrance.isNullOrBlank() -> "${category.displayName}($entrance)"
            else -> category.displayName
        }
    }
}
