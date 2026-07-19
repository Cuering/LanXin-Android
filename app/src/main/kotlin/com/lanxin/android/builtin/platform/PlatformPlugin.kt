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

package com.lanxin.android.builtin.platform

import com.lanxin.android.builtin.capabilities.domain.LocationConfig
import com.lanxin.android.builtin.capabilities.domain.LocationGate
import com.lanxin.android.builtin.capabilities.domain.LocationSettings
import com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesSettings
import com.lanxin.android.builtin.capabilities.tools.LocationTool
import com.lanxin.android.builtin.navigate.domain.NavigateConfig
import com.lanxin.android.builtin.navigate.domain.NavigateGate
import com.lanxin.android.builtin.navigate.tools.HotelPriceTool
import com.lanxin.android.builtin.navigate.tools.NearbyPoiTool
import com.lanxin.android.builtin.navigate.tools.OpenNavigationTool
import com.lanxin.android.builtin.platform.domain.DeviceSensingConfig
import com.lanxin.android.builtin.platform.domain.DeviceSensingGate
import com.lanxin.android.builtin.platform.domain.DeviceSensingSettings
import com.lanxin.android.builtin.platform.domain.WebSearchConfig
import com.lanxin.android.builtin.platform.domain.WebSearchGate
import com.lanxin.android.builtin.platform.domain.WebSearchSettings
import com.lanxin.android.builtin.platform.tools.AppInstallCheckTool
import com.lanxin.android.builtin.platform.tools.AppIntentTool
import com.lanxin.android.builtin.platform.tools.ClipboardTool
import com.lanxin.android.builtin.platform.tools.FileOpsTool
import com.lanxin.android.builtin.platform.tools.SystemInfoTool
import com.lanxin.android.builtin.platform.tools.WebSearchTool
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
 * 手机平台专属工具插件。
 *
 * MCP 工具：
 * - clipboard_get / clipboard_set
 * - app_install_check
 * - system_info（受设备感知 + 智能能力主开关；默认随迁移 ON）
 * - file_read / file_write / file_list
 * - web_search（受联网搜索 + 智能能力主开关；默认随迁移 ON）
 * - get_location（受位置 + 主开关；默认 ON，按需权限，不持续定位）
 * - nearby_poi / open_navigation / hotel_price_lookup（导航 Navigate V1；主开关 + 位置 + 联网）
 * - app_intent
 *
 * 仅封装适合在 Android 端执行的能力。
 */
@Singleton
class PlatformPlugin @Inject constructor(
    private val clipboardTool: ClipboardTool,
    private val appInstallCheckTool: AppInstallCheckTool,
    private val systemInfoTool: SystemInfoTool,
    private val fileOpsTool: FileOpsTool,
    private val webSearchTool: WebSearchTool,
    private val appIntentTool: AppIntentTool,
    private val webSearchSettings: WebSearchSettings,
    private val deviceSensingSettings: DeviceSensingSettings,
    private val smartCapabilitiesSettings: SmartCapabilitiesSettings,
    private val locationSettings: LocationSettings,
    private val locationTool: LocationTool,
    private val nearbyPoiTool: NearbyPoiTool,
    private val openNavigationTool: OpenNavigationTool,
    private val hotelPriceTool: HotelPriceTool
) : LanXinPlugin {

    override val id = "lanxin.platform"
    override val name = "手机平台工具"
    override val version = "1.5.0"
    override val description =
        "Android 专属能力：剪贴板、已安装应用、设备感知、本地文件、联网搜索、位置、导航 Navigate（附近POI/外链导航/酒店价；与导游拆开）、Intent 唤起"

    override suspend fun onLoad(context: PluginContext) {
        context.registerTool(
            ToolDef(
                name = "clipboard_get",
                description = "读取系统剪贴板文本内容",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject { })
                },
                handler = {
                    runCatching { clipboardTool.get() }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "clipboard_set",
                description = "写入文本内容到系统剪贴板",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("text", stringProp("要写入剪贴板的文本"))
                            put("label", stringProp("ClipData 标签，默认 lanxin"))
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("text")) })
                },
                handler = { args ->
                    runCatching {
                        val text = args.string("text") ?: error("text 必填")
                        val label = args.string("label") ?: "lanxin"
                        clipboardTool.set(text, label)
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "app_install_check",
                description = "查询手机已安装应用。可精确查 package_name，或模糊 query 过滤列表；include_system 控制是否含系统应用",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "package_name",
                                stringProp("精确包名，非空时只查该应用是否安装（不依赖 QUERY_ALL_PACKAGES）")
                            )
                            put(
                                "query",
                                stringProp("模糊过滤：包名或应用名包含该关键字（忽略大小写）")
                            )
                            put(
                                "include_system",
                                boolProp("是否包含系统应用，默认 false")
                            )
                            put(
                                "limit",
                                intProp("最多返回条数，默认 50，上限 500")
                            )
                        }
                    )
                },
                handler = { args ->
                    runCatching {
                        appInstallCheckTool.check(
                            packageName = args.string("package_name"),
                            query = args.string("query"),
                            includeSystem = args.bool("include_system") ?: false,
                            limit = args.int("limit") ?: 50
                        )
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = DeviceSensingConfig.TOOL_NAME,
                description = "返回设备信息：型号、厂商、Android 版本/SDK、屏幕尺寸、网络状态、电量等；需在设置中开启设备感知",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject { })
                },
                handler = {
                    runCatching {
                        val master = runCatching {
                            smartCapabilitiesSettings.getConfig().masterEnabled
                        }.getOrDefault(true)
                        val config = deviceSensingSettings.getConfig()
                        DeviceSensingGate.denyIfDisabled(config, master)?.let {
                            return@runCatching it
                        }
                        systemInfoTool.collect()
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "file_read",
                description = "读取应用私有文件或 content/file URI 文本内容（ContentResolver + 私有目录）",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "path",
                                stringProp("应用私有相对路径，如 notes/a.txt 或 files/notes/a.txt；与 uri 二选一")
                            )
                            put(
                                "uri",
                                stringProp("content:// 或 file:// URI；与 path 二选一")
                            )
                            put("encoding", stringProp("字符编码，默认 UTF-8"))
                            put(
                                "max_bytes",
                                intProp("最大读取字节，默认 262144，上限 2097152")
                            )
                        }
                    )
                },
                handler = { args ->
                    runCatching {
                        fileOpsTool.read(
                            path = args.string("path"),
                            uri = args.string("uri"),
                            encoding = args.string("encoding") ?: "UTF-8",
                            maxBytes = args.int("max_bytes") ?: FileOpsTool.DEFAULT_MAX_BYTES
                        )
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "file_write",
                description = "写入文本到应用私有目录（files 或 cache），禁止越界写外部存储",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("path", stringProp("相对路径，如 notes/a.txt"))
                            put("content", stringProp("写入的文本内容"))
                            put("encoding", stringProp("字符编码，默认 UTF-8"))
                            put("append", boolProp("是否追加，默认 false"))
                            put(
                                "base",
                                stringProp("根目录：files（默认）或 cache")
                            )
                        }
                    )
                    put(
                        "required",
                        buildJsonArray {
                            add(JsonPrimitive("path"))
                            add(JsonPrimitive("content"))
                        }
                    )
                },
                handler = { args ->
                    runCatching {
                        val path = args.string("path") ?: error("path 必填")
                        val content = args.string("content") ?: error("content 必填")
                        fileOpsTool.write(
                            path = path,
                            content = content,
                            encoding = args.string("encoding") ?: "UTF-8",
                            append = args.bool("append") ?: false,
                            base = args.string("base") ?: "files"
                        )
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "file_list",
                description = "列出应用私有目录（files/cache）下的文件与子目录",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("path", stringProp("相对子目录，默认根目录"))
                            put("base", stringProp("根目录：files（默认）或 cache"))
                            put("recursive", boolProp("是否递归，默认 false，最大深度 8"))
                            put("limit", intProp("最多返回条数，默认 200，上限 2000"))
                        }
                    )
                },
                handler = { args ->
                    runCatching {
                        fileOpsTool.list(
                            path = args.string("path") ?: "",
                            base = args.string("base") ?: "files",
                            recursive = args.bool("recursive") ?: false,
                            limit = args.int("limit") ?: 200
                        )
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = WebSearchConfig.TOOL_NAME,
                description = "HTTP 搜索（DuckDuckGo Instant Answer + lite HTML 回退），返回标题/链接/摘要；需在设置中开启联网搜索",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("query", stringProp("搜索关键词"))
                            put("limit", intProp("最多结果条数，默认取设置，上限 20"))
                            put(
                                "region",
                                stringProp("区域代码，默认取设置（如 wt-wt / cn-zh / us-en）")
                            )
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("query")) })
                },
                handler = { args ->
                    runCatching {
                        val master = runCatching {
                            smartCapabilitiesSettings.getConfig().masterEnabled
                        }.getOrDefault(true)
                        val config = webSearchSettings.getConfig()
                        WebSearchGate.denyIfDisabled(config, master)?.let {
                            return@runCatching it
                        }
                        val query = args.string("query") ?: error("query 必填")
                        webSearchTool.search(
                            query = query,
                            limit = args.int("limit") ?: config.clampedLimit(),
                            region = args.string("region") ?: config.normalizedRegion()
                        )
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = LocationConfig.TOOL_NAME,
                description = "读取设备最近一次已知位置（经纬度/精度）；需智能能力→位置开启；首次用时需定位权限；不后台持续定位",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject { })
                },
                handler = {
                    runCatching {
                        val smart = runCatching {
                            smartCapabilitiesSettings.getConfig()
                        }.getOrDefault(
                            com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesConfig()
                        )
                        val locCfg = locationSettings.getConfig()
                        LocationGate.denyIfDisabled(
                            smart = smart,
                            location = locCfg,
                            permissionGranted = locationTool.hasPermission()
                        )?.let { return@runCatching it }
                        locationTool.readOnce()
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = NavigateConfig.NEARBY_POI_TOOL,
                description = "查附近 POI：洗手间/出口/电梯/餐饮/酒店/ATM/药店/停车场；需位置+联网；返回距离/方向粗估与 opening_hours；不做室内逐步导航",
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
                        NavigateGate.denyPoiIfDisabled(
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
                        val master = runCatching {
                            smartCapabilitiesSettings.getConfig().masterEnabled
                        }.getOrDefault(true)
                        NavigateGate.denyNavIfDisabled(master)?.let {
                            return@runCatching it
                        }
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
                            masterEnabled = smart.masterEnabled,
                            locationPrefsOpen = locationPrefs,
                            webSearchEnabled = webOn
                        )?.let { return@runCatching it }

                        val query = args.string("query") ?: error("query 必填")
                        val lat = args.double("latitude")
                        val lon = args.double("longitude")
                        // 未传坐标时尽量补当前位置（失败则仅用 query）
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

        context.registerTool(
            ToolDef(
                name = "app_intent",
                description = "通过 Intent 唤起其他 App：打开 URL/Deep Link、拨号、短信、邮件、分享、应用设置、按包名启动",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "type",
                                stringProp(
                                    "便捷类型：view/url/deeplink/dial/call/sms/mail/share/settings/app_settings/launch；也可不填走通用 action"
                                )
                            )
                            put(
                                "action",
                                stringProp("Intent action，如 android.intent.action.VIEW")
                            )
                            put(
                                "data",
                                stringProp("data URI 或号码/地址，如 https://… / tel:10086 / 包相关参数")
                            )
                            put("package_name", stringProp("目标应用包名（可选/launch 必填）"))
                            put("mime_type", stringProp("MIME type，可选"))
                            put(
                                "extras",
                                stringProp("简单 extras：key=value,key2=value2（逗号用 %2C）")
                            )
                            put("text", stringProp("分享/短信/邮件正文"))
                            put("chooser_title", stringProp("系统选择器标题"))
                            put("use_chooser", boolProp("是否使用 createChooser，默认 false"))
                        }
                    )
                },
                handler = { args ->
                    runCatching {
                        appIntentTool.launch(
                            action = args.string("action"),
                            data = args.string("data"),
                            packageName = args.string("package_name"),
                            mimeType = args.string("mime_type"),
                            extras = args.string("extras"),
                            type = args.string("type"),
                            text = args.string("text"),
                            chooserTitle = args.string("chooser_title"),
                            useChooser = args.bool("use_chooser") ?: false
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

    /**
     * 优先用参数坐标；否则读 last known（需权限）。
     */
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

    private fun JsonObject.bool(key: String): Boolean? =
        this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

    private fun JsonObject.double(key: String): Double? =
        this[key]?.jsonPrimitive?.doubleOrNull
            ?: this[key]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

    private fun stringProp(desc: String) = buildJsonObject {
        put("type", "string")
        put("description", desc)
    }

    private fun boolProp(desc: String) = buildJsonObject {
        put("type", "boolean")
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
