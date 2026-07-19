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

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 轻量地理计算（纯函数，可单测）。
 *
 * 仅做距离/方位粗估，**不是**完整导航引擎。
 */
object GeoMath {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /**
     * Haversine 大圆距离（米）。
     */
    fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }

    /**
     * 初始方位角 0–360°（正北为 0，顺时针）。
     */
    fun bearingDegrees(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val φ1 = Math.toRadians(lat1)
        val φ2 = Math.toRadians(lat2)
        val Δλ = Math.toRadians(lon2 - lon1)
        val y = sin(Δλ) * cos(φ2)
        val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(Δλ)
        val θ = Math.toDegrees(atan2(y, x))
        return (θ + 360.0) % 360.0
    }

    /**
     * 粗方位中文：正北 / 东北 / 东 …
     */
    fun bearingLabel(bearingDeg: Double): String {
        val dirs = listOf(
            "正北", "东北", "正东", "东南",
            "正南", "西南", "正西", "西北"
        )
        val idx = ((bearingDeg + 22.5) / 45.0).toInt() % 8
        return dirs[idx]
    }

    /**
     * 人类可读距离：&lt;1000m 用米，否则公里一位小数。
     */
    fun formatDistance(meters: Double): String {
        if (meters.isNaN() || meters < 0) return "未知"
        return if (meters < 1000) {
            "${meters.roundToInt()} 米"
        } else {
            String.format("%.1f 公里", meters / 1000.0)
        }
    }

    fun isValidLat(lat: Double): Boolean = lat in -90.0..90.0

    fun isValidLon(lon: Double): Boolean = lon in -180.0..180.0

    fun isValidCoord(lat: Double, lon: Double): Boolean =
        isValidLat(lat) && isValidLon(lon)
}
