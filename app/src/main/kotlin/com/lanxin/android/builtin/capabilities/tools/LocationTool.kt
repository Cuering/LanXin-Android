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

package com.lanxin.android.builtin.capabilities.tools

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 只读位置工具：按需读一次 last known，不后台持续定位。
 *
 * 权限：ACCESS_COARSE_LOCATION 或 ACCESS_FINE_LOCATION。
 * 无权限时返回 needs_permission，不抛崩溃。
 */
@Singleton
class LocationTool @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * 读取最近一次已知位置（被动，不主动 requestLocationUpdates）。
     */
    @SuppressLint("MissingPermission")
    fun readOnce(): JsonObject {
        if (!hasPermission()) {
            return buildJsonObject {
                put("ok", false)
                put("error", "未授予定位权限")
                put("code", "location_permission_denied")
                put("needs_permission", true)
            }
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return buildJsonObject {
                put("ok", false)
                put("error", "LocationManager 不可用")
                put("code", "location_unavailable")
            }
        val providers = buildList {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)
            ) {
                add(LocationManager.FUSED_PROVIDER)
            }
            // 仍尝试 PASSIVE
            add(LocationManager.PASSIVE_PROVIDER)
        }.distinct()

        var best: android.location.Location? = null
        for (p in providers) {
            val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
            if (best == null || loc.time > best!!.time) {
                best = loc
            }
        }
        if (best == null) {
            return buildJsonObject {
                put("ok", false)
                put("error", "暂无缓存位置；请打开系统定位后稍后再试（不后台持续定位）")
                put("code", "location_no_fix")
            }
        }
        val loc = best!!
        return buildJsonObject {
            put("ok", true)
            put("latitude", loc.latitude)
            put("longitude", loc.longitude)
            put("accuracy_m", loc.accuracy.toDouble())
            put("provider", loc.provider ?: "unknown")
            put("time_epoch_ms", loc.time)
            put("note", "last_known；非实时持续定位")
        }
    }
}
