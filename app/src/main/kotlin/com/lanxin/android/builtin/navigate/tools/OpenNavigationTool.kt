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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.lanxin.android.builtin.navigate.domain.GeoMath
import com.lanxin.android.builtin.navigate.domain.NavigationUriBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 一键调起系统/高德/百度/Google 导航（外链 Intent）。
 *
 * **不做** App 内 turn-by-turn。
 */
@Singleton
class OpenNavigationTool @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun open(
        lat: Double,
        lon: Double,
        name: String? = null,
        provider: String? = null,
        mode: String? = null
    ): JsonObject {
        if (!GeoMath.isValidCoord(lat, lon)) {
            return buildJsonObject {
                put("ok", false)
                put("error", "无效坐标 latitude/longitude")
            }
        }
        val target = NavigationUriBuilder.NavTarget(
            lat = lat,
            lon = lon,
            name = name?.trim().orEmpty()
        )
        val prov = NavigationUriBuilder.Provider.parse(provider)
        val travel = NavigationUriBuilder.Mode.parse(mode)
        val candidates = NavigationUriBuilder.buildCandidates(target, prov, travel)
        val tried = mutableListOf<String>()

        for (c in candidates) {
            tried += c.provider
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(c.uri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!c.packageHint.isNullOrBlank() && isPackageInstalled(c.packageHint)) {
                    setPackage(c.packageHint)
                }
            }
            // App scheme 无包时仍尝试；失败则下一候选
            val resolved = intent.resolveActivity(context.packageManager)
            if (resolved == null && c.uri.startsWith("http", ignoreCase = true).not() &&
                c.uri.startsWith("geo:", ignoreCase = true).not()
            ) {
                // 清 package 再试一次
                intent.setPackage(null)
                if (intent.resolveActivity(context.packageManager) == null) {
                    continue
                }
            } else if (resolved == null) {
                continue
            }
            return try {
                context.startActivity(intent)
                buildJsonObject {
                    put("ok", true)
                    put("started", true)
                    put("provider", c.provider)
                    put("uri", c.uri)
                    put("lat", lat)
                    put("lon", lon)
                    put("name", target.name)
                    put("mode", travel.id)
                    put(
                        "tried_providers",
                        buildJsonArray { tried.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                    )
                    put("note", "已调起外链导航；App 内不做逐步路引")
                }
            } catch (e: ActivityNotFoundException) {
                continue
            } catch (e: SecurityException) {
                return buildJsonObject {
                    put("ok", false)
                    put("started", false)
                    put("error", "权限不足：${e.message}")
                }
            } catch (e: Exception) {
                continue
            }
        }

        return buildJsonObject {
            put("ok", false)
            put("started", false)
            put("error", "没有可处理导航的应用；请安装高德/百度/Google 地图，或允许打开浏览器")
            put(
                "tried_providers",
                buildJsonArray { tried.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
            )
            put(
                "fallback_geo",
                "geo:$lat,$lon?q=$lat,$lon"
            )
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
