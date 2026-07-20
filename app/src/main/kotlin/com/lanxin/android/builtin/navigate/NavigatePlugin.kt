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

package com.lanxin.android.builtin.navigate

import com.lanxin.android.builtin.capabilities.domain.LocationGate
import com.lanxin.android.builtin.capabilities.domain.LocationSettings
import com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesSettings
import com.lanxin.android.builtin.capabilities.tools.LocationTool
import com.lanxin.android.builtin.navigate.domain.NavigateConfig
import com.lanxin.android.builtin.navigate.domain.NavigateGate
import com.lanxin.android.builtin.navigate.tools.HotelPriceTool
import com.lanxin.android.builtin.navigate.tools.NearbyPoiTool
import com.lanxin.android.builtin.navigate.tools.OpenNavigationTool
import com.lanxin.android.builtin.platform.domain.WebSearchGate
import com.lanxin.android.builtin.platform.domain.WebSearchSettings
import com.lanxin.android.plugin.LanXinPlugin
import com.lanxin.android.plugin.PluginContext
import com.lanxin.android.plugin.ToolDef
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 导航 Navigate 编译期插件（id = [NavigateConfig.PLUGIN_ID]）。
 *
 * **默认 OFF**：由 PluginManager.register(defaultEnabled=false) + 智能能力子开关共同约束。
 * 工具：nearby_poi / open_navigation / hotel_price_lookup。
 * get_location 留在 PlatformPlugin。
 */
@Singleton
class NavigatePlugin @Inject constructor(
    private val nearbyPoiTool: NearbyPoiTool,
    private val openNavigationTool: OpenNavigationTool,
    private val hotelPriceTool: HotelPriceTool,
    private val locationTool: LocationTool,
    private val locationSettings: LocationSettings,
    private val webSearchSettings: WebSearchSettings,
    private val smartCapabilitiesSettings: SmartCapabilitiesSettings
) : LanXinPlugin {

    override val id = NavigateConfig.PLUGIN_ID
    override val name = "导航"
    override val version = "1.0.0"
    override val description =
        "附近 POI / 外链导航 / 酒店价（默认关；设置 → 智能能力 → 导航 开启；与导游拆开）"

    override suspend fun onLoad(context: PluginContext) {
        context.registerTool(
            ToolDef(
                name = NavigateConfig.NEARBY_POI_TOOL,
                description =
                    "查附近 POI：洗手间/出口/电梯/餐饮/酒店/ATM/药店/停车场；需位置+联网；返回距离/方向粗估与 opening_hours；不做室内逐步导航",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "category",
                                stringProp(
                                    "类别：restroom/exit/elevator/dining/hotel/atm/pharmacy/parking 或中文「洗手间」「出口」等"
                                )
                            )
                            put("latitude", numberProp("纬度；缺省则用 get_location 最近位置"))
                            put("longitude", numberProp("经度；缺省则用 get_location 最近位置"))
                            put("radius_m", intProp("搜索半径米，默认 800，上限 3000"))
                            put("limit", intProp("最多条数，默认 5，上限 15"))
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("category")) })
                },
                handler = { args ->
                    runCatching {
                        val smart = runCatching {
                            smartCapabilitiesSettings.getConfig()
                        }.getOrDefault(
                            com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesConfig()
                        )
                        val locCfg = locationSettings.getConfig()
                        val webCfg = webSearchSettings.getConfig()
                        val locationPrefs = LocationGate.isPrefsOpen(smart, locCfg)
                        val webOn = WebSearchGate.isEnabled(
                            webCfg,
                            smart.masterEnabled && smart.webSearchEnabled
                        )
                        val pluginOn = smart.navigateEnabled
                        NavigateGate.denyPoiIfDisabled(
                            pluginEnabled = pluginOn,
                            masterEnabled = smart.masterEnabled,
                            locationPrefsOpen = locationPrefs,
                            webSearchEnabled = webOn
                        )?.let { return@runCatching it }

                        val category = args.string("category") ?: error("category 必填")
                        val coords = resolveCoords(
                            latArg = args.double("latitude"),
                            lonArg = args.double("longitude")
                        )
                        if (!coords.ok) return@runCatching coords.error!!
                        nearbyPoiTool.search(
                            categoryRaw = category,
                            lat = coords.lat,
                            lon = coords.lon,
                            radiusM = args.int("radius_m") ?: NavigateConfig.DEFAULT_RADIUS_M,
                            limit = args.int("limit") ?: NavigateConfig.DEFAULT_LIMIT
                        )
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = NavigateConfig.OPEN_NAVIGATION_TOOL,
                description = "一键调起系统/高德/百度/Google 外链导航到目标坐标；App 内不做 turn-by-turn",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("latitude", numberProp("目的地纬度"))
                            put("longitude", numberProp("目的地经度"))
                            put("name", stringProp("目的地名称，可选"))
                            put(
                                "provider",
                                stringProp("auto/amap/baidu/google/geo，默认 auto")
                            )
                            put(
                                "mode",
                                stringProp("walk/drive/transit/ride，默认 walk")
                            )
                        }
                    )
                    put(
                        "required",
                        buildJsonArray {
                            add(JsonPrimitive("latitude"))
                            add(JsonPrimitive("longitude"))
                        }
                    )
                },
                handler = { args ->
                    runCatching {
                        val smart = runCatching {
                            smartCapabilitiesSettings.getConfig()
                        }.getOrDefault(
                            com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesConfig()
                        )
                        NavigateGate.denyNavIfDisabled(
                            pluginEnabled = smart.navigateEnabled,
                            masterEnabled = smart.masterEnabled
                        )?.let { return@runCatching it }
                        val lat = args.double("latitude") ?: error("latitude 必填")
                        val lon = args.double("longitude") ?: error("longitude 必填")
                        openNavigationTool.open(
                            lat = lat,
                            lon = lon,
                            name = args.string("name"),
                            provider = args.string("provider"),
                            mode = args.string("mode")
                        )
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = NavigateConfig.HOTEL_PRICE_TOOL,
                description = "联网检索酒店/房间价位摘要；价格供参考、以平台实时为准；需位置门闸+联网搜索",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("query", stringProp("酒店名或「附近酒店 价位」等关键词"))
                            put("latitude", numberProp("可选，辅助「附近」检索"))
                            put("longitude", numberProp("可选，辅助「附近」检索"))
                            put("limit", intProp("摘要条数，默认 6"))
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("query")) })
                },
                handler = { args ->
                    runCatching {
                        val smart = runCatching {
                            smartCapabilitiesSettings.getConfig()
                        }.getOrDefault(
                            com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesConfig()
                        )
                        val locCfg = locationSettings.getConfig()
                        val webCfg = webSearchSettings.getConfig()
                        val locationPrefs = LocationGate.isPrefsOpen(smart, locCfg)
                        val webOn = WebSearchGate.isEnabled(
                            webCfg,
                            smart.masterEnabled && smart.webSearchEnabled
                        )
                        NavigateGate.denyPoiIfDisabled(
                            pluginEnabled = smart.navigateEnabled,
                            masterEnabled = smart.masterEnabled,
                            locationPrefsOpen = locationPrefs,
                            webSearchEnabled = webOn
                        )?.let { return@runCatching it }

                        val query = args.string("query") ?: error("query 必填")
                        val lat = args.double("latitude")
                        val lon = args.double("longitude")
                        val (useLat, useLon) = if (lat != null && lon != null) {
                            lat to lon
                        } else {
                            val once = if (locationTool.hasPermission()) {
                                locationTool.readOnce()
                            } else {
                                null
                            }
                            val olat = once?.get("latitude")?.jsonPrimitive?.doubleOrNull
                            val olon = once?.get("longitude")?.jsonPrimitive?.doubleOrNull
                            olat to olon
                        }
                        hotelPriceTool.lookup(
                            query = query,
                            lat = useLat,
                            lon = useLon,
                            limit = args.int("limit") ?: 6
                        )
                    }.toToolResult()
                }
            )
        )
    }

    private fun Result<JsonObject>.toToolResult(): JsonObject =
        fold(
            onSuccess = { it },
            onFailure = { e ->
                buildJsonObject {
                    put("ok", false)
                    put("error", e.message ?: e.toString())
                }
            }
        )

    private data class CoordResult(
        val ok: Boolean,
        val lat: Double = 0.0,
        val lon: Double = 0.0,
        val error: JsonObject? = null
    )

    private fun resolveCoords(latArg: Double?, lonArg: Double?): CoordResult {
        if (latArg != null && lonArg != null) {
            return CoordResult(ok = true, lat = latArg, lon = lonArg)
        }
        if (!locationTool.hasPermission()) {
            return CoordResult(
                ok = false,
                error = buildJsonObject {
                    put("ok", false)
                    put("error", "未授予定位权限，且未提供 latitude/longitude")
                    put("code", "location_permission_denied")
                    put("needs_permission", true)
                }
            )
        }
        val once = locationTool.readOnce()
        if (once["ok"]?.jsonPrimitive?.contentOrNull != "true") {
            return CoordResult(ok = false, error = once)
        }
        val lat = once["latitude"]?.jsonPrimitive?.doubleOrNull
        val lon = once["longitude"]?.jsonPrimitive?.doubleOrNull
        if (lat == null || lon == null) {
            return CoordResult(
                ok = false,
                error = buildJsonObject {
                    put("ok", false)
                    put("error", "无法解析当前位置")
                    put("code", "location_no_fix")
                }
            )
        }
        return CoordResult(ok = true, lat = lat, lon = lon)
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

    private fun JsonObject.double(key: String): Double? =
        this[key]?.jsonPrimitive?.doubleOrNull
            ?: this[key]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

    private fun stringProp(desc: String) = buildJsonObject {
        put("type", "string")
        put("description", desc)
    }

    private fun intProp(desc: String) = buildJsonObject {
        put("type", "integer")
        put("description", desc)
    }

    private fun numberProp(desc: String) = buildJsonObject {
        put("type", "number")
        put("description", desc)
    }
}
