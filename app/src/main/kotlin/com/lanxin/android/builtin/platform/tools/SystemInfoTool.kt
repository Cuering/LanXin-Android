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

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 设备系统信息：型号、Android 版本、屏幕、网络、电量等。
 * 主要依赖 [Build]、[ConnectivityManager]、[BatteryManager]；
 * 电量通过粘性广播 [Intent.ACTION_BATTERY_CHANGED] 读取，无需额外权限。
 *
 * Agent 可见性与执行受 [com.lanxin.android.builtin.platform.domain.DeviceSensingGate] 门闸
 * （默认关；设置 → 设备感知）。本类只负责采集，不含权限策略。
 */
@Singleton
class SystemInfoTool @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun collect(): JsonObject {
        return buildJsonObject {
            put("ok", true)
            put("device", deviceJson())
            put("android", androidJson())
            put("app", appJson())
            put("screen", screenJson())
            put("network", networkJson())
            put("battery", batteryJson())
        }
    }

    private fun deviceJson(): JsonObject = buildJsonObject {
        put("manufacturer", Build.MANUFACTURER.orEmpty())
        put("brand", Build.BRAND.orEmpty())
        put("model", Build.MODEL.orEmpty())
        put("device", Build.DEVICE.orEmpty())
        put("product", Build.PRODUCT.orEmpty())
        put("hardware", Build.HARDWARE.orEmpty())
        put("board", Build.BOARD.orEmpty())
        put("fingerprint", Build.FINGERPRINT.orEmpty())
        put("is_emulator", isEmulator())
    }

    private fun androidJson(): JsonObject = buildJsonObject {
        put("release", Build.VERSION.RELEASE.orEmpty())
        put("sdk_int", Build.VERSION.SDK_INT)
        put("codename", Build.VERSION.CODENAME.orEmpty())
        put("security_patch", Build.VERSION.SECURITY_PATCH.orEmpty())
        put("incremental", Build.VERSION.INCREMENTAL.orEmpty())
    }

    private fun appJson(): JsonObject = buildJsonObject {
        val pm = context.packageManager
        val pkg = context.packageName
        val info = runCatching {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0)
        }.getOrNull()
        put("package_name", pkg)
        put("version_name", info?.versionName.orEmpty())
        put("version_code", info?.longVersionCode ?: 0L)
        put(
            "android_id",
            runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            }.getOrNull().orEmpty()
        )
    }

    private fun screenJson(): JsonObject {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.density
        val densityDpi = metrics.densityDpi
        return buildJsonObject {
            put("width_px", width)
            put("height_px", height)
            put("density", density.toDouble())
            put("density_dpi", densityDpi)
            put("width_dp", (width / density).toInt())
            put("height_dp", (height / density).toInt())
            put("orientation", if (width > height) "landscape" else "portrait")
        }
    }

    private fun networkJson(): JsonObject {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            return buildJsonObject {
                put("available", false)
                put("type", "unknown")
            }
        }
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val connected = caps != null && (
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            )
        val type = when {
            caps == null -> "none"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
        return buildJsonObject {
            put("available", connected)
            put("type", type)
            put("has_internet", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
            put("validated", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
            put("metered", cm.isActiveNetworkMetered)
        }
    }

    private fun batteryJson(): JsonObject {
        val intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        if (intent == null) {
            return buildJsonObject {
                put("available", false)
            }
        }

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level >= 0 && scale > 0) {
            (level * 100f / scale).toInt()
        } else {
            -1
        }
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) // 0.1°C
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)

        val statusText = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
            else -> "unknown"
        }
        val pluggedText = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            0 -> "none"
            else -> "other"
        }
        val healthText = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "cold"
            else -> "unknown"
        }

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val capacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: pct
        val chargeCounter = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: -1
        val currentNow = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE

        return buildJsonObject {
            put("available", true)
            put("percent", if (capacity in 0..100) capacity else pct)
            put("status", statusText)
            put("plugged", pluggedText)
            put(
                "charging",
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            )
            put("health", healthText)
            put("temperature_c", if (temperature >= 0) temperature / 10.0 else -1.0)
            put("voltage_mv", voltage)
            put("charge_counter_uah", chargeCounter)
            put(
                "current_now_ua",
                if (currentNow != Int.MIN_VALUE) currentNow else 0
            )
        }
    }

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            "google_sdk" == Build.PRODUCT
    }
}
