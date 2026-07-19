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

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 构造「一键外链导航」URI（纯函数）。
 *
 * App 内**不做**完整 turn-by-turn；只调起系统/高德/百度/Google 地图。
 */
object NavigationUriBuilder {

    enum class Provider(val id: String) {
        AUTO("auto"),
        GEO("geo"),
        AMAP("amap"),
        BAIDU("baidu"),
        GOOGLE("google"),
        SYSTEM("system");

        companion object {
            fun parse(raw: String?): Provider {
                val k = raw?.trim()?.lowercase().orEmpty()
                return entries.firstOrNull { it.id == k } ?: AUTO
            }
        }
    }

    enum class Mode(val id: String) {
        WALK("walk"),
        DRIVE("drive"),
        TRANSIT("transit"),
        RIDE("ride");

        companion object {
            fun parse(raw: String?): Mode {
                val k = raw?.trim()?.lowercase().orEmpty()
                return when (k) {
                    "walk", "walking", "foot", "步行", "走路" -> WALK
                    "drive", "driving", "car", "驾车", "开车" -> DRIVE
                    "transit", "bus", "subway", "公交", "地铁" -> TRANSIT
                    "ride", "bike", "骑行", "自行车" -> RIDE
                    else -> WALK
                }
            }
        }
    }

    data class NavTarget(
        val lat: Double,
        val lon: Double,
        val name: String = ""
    )

    data class NavUri(
        val provider: String,
        val uri: String,
        val packageHint: String? = null
    )

    fun encode(s: String): String =
        URLEncoder.encode(s, StandardCharsets.UTF_8.name())

    /**
     * 按 provider 生成候选 URI 列表（auto 时按 高德 App → 百度 App → geo → 高德 Web → Google 顺序）。
     */
    fun buildCandidates(
        target: NavTarget,
        provider: Provider = Provider.AUTO,
        mode: Mode = Mode.WALK
    ): List<NavUri> {
        require(GeoMath.isValidCoord(target.lat, target.lon)) {
            "无效坐标"
        }
        val name = target.name.ifBlank { "目的地" }
        val all = listOf(
            amapApp(target, name, mode),
            baiduApp(target, name, mode),
            geoUri(target, name),
            amapWeb(target, name, mode),
            googleMaps(target, name, mode),
            baiduWeb(target, name, mode)
        )
        return when (provider) {
            Provider.AUTO, Provider.SYSTEM -> all
            Provider.GEO -> listOf(geoUri(target, name))
            Provider.AMAP -> listOf(amapApp(target, name, mode), amapWeb(target, name, mode), geoUri(target, name))
            Provider.BAIDU -> listOf(baiduApp(target, name, mode), baiduWeb(target, name, mode), geoUri(target, name))
            Provider.GOOGLE -> listOf(googleMaps(target, name, mode), geoUri(target, name))
        }
    }

    private fun geoUri(t: NavTarget, name: String): NavUri =
        NavUri(
            provider = "geo",
            uri = "geo:${t.lat},${t.lon}?q=${t.lat},${t.lon}(${encode(name)})"
        )

    private fun amapApp(t: NavTarget, name: String, mode: Mode): NavUri {
        // t=0 驾车 1 公交 2 步行 3 骑行
        val tMode = when (mode) {
            Mode.DRIVE -> 0
            Mode.TRANSIT -> 1
            Mode.WALK -> 2
            Mode.RIDE -> 3
        }
        val uri =
            "amapuri://route/plan/?dlat=${t.lat}&dlon=${t.lon}&dname=${encode(name)}&dev=0&t=$tMode"
        return NavUri(provider = "amap", uri = uri, packageHint = "com.autonavi.minimap")
    }

    private fun amapWeb(t: NavTarget, name: String, mode: Mode): NavUri {
        val m = when (mode) {
            Mode.DRIVE -> "car"
            Mode.WALK -> "walk"
            Mode.RIDE -> "ride"
            Mode.TRANSIT -> "bus"
        }
        // 高德 Web：to=lon,lat,name
        val uri =
            "https://uri.amap.com/navigation?to=${t.lon},${t.lat},${encode(name)}&mode=$m&coordinate=gaode&callnative=1"
        return NavUri(provider = "amap_web", uri = uri)
    }

    private fun baiduApp(t: NavTarget, name: String, mode: Mode): NavUri {
        val m = when (mode) {
            Mode.DRIVE -> "driving"
            Mode.WALK -> "walking"
            Mode.RIDE -> "riding"
            Mode.TRANSIT -> "transit"
        }
        // destination=lat,lng|name  （BD09 更准，但 GCJ/WGS 多数 App 可纠偏；标明 src）
        val dest = "${t.lat},${t.lon}|${name}"
        val uri =
            "baidumap://map/direction?destination=${encode(dest)}&mode=$m&coord_type=wgs84&src=lanxin"
        return NavUri(provider = "baidu", uri = uri, packageHint = "com.baidu.BaiduMap")
    }

    private fun baiduWeb(t: NavTarget, name: String, mode: Mode): NavUri {
        val m = when (mode) {
            Mode.DRIVE -> "driving"
            Mode.WALK -> "walking"
            Mode.RIDE -> "riding"
            Mode.TRANSIT -> "transit"
        }
        val uri =
            "https://api.map.baidu.com/direction?destination=latlng:${t.lat},${t.lon}|name:${encode(name)}&mode=$m&region=中国&output=html&src=lanxin"
        return NavUri(provider = "baidu_web", uri = uri)
    }

    private fun googleMaps(t: NavTarget, name: String, mode: Mode): NavUri {
        val travel = when (mode) {
            Mode.DRIVE -> "driving"
            Mode.WALK -> "walking"
            Mode.RIDE -> "bicycling"
            Mode.TRANSIT -> "transit"
        }
        val uri =
            "https://www.google.com/maps/dir/?api=1&destination=${t.lat},${t.lon}&travelmode=$travel"
        return NavUri(
            provider = "google",
            uri = uri,
            packageHint = "com.google.android.apps.maps"
        )
    }
}
