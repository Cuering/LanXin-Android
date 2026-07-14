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

import com.lanxin.android.builtin.platform.tools.AppInstallCheckTool
import com.lanxin.android.builtin.platform.tools.ClipboardTool
import com.lanxin.android.builtin.platform.tools.SystemInfoTool
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
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 手机平台专属工具插件。
 *
 * MCP 工具：
 * - clipboard_get / clipboard_set
 * - app_install_check
 * - system_info
 *
 * 仅封装必须在 Android 端执行的能力；服务端已可完成的工具不在此列。
 */
@Singleton
class PlatformPlugin @Inject constructor(
    private val clipboardTool: ClipboardTool,
    private val appInstallCheckTool: AppInstallCheckTool,
    private val systemInfoTool: SystemInfoTool
) : LanXinPlugin {

    override val id = "lanxin.platform"
    override val name = "手机平台工具"
    override val version = "1.0.0"
    override val description = "Android 专属能力：剪贴板、已安装应用查询、设备系统信息"

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
                name = "system_info",
                description = "返回设备信息：型号、厂商、Android 版本/SDK、屏幕尺寸、网络状态、电量等",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject { })
                },
                handler = {
                    runCatching { systemInfoTool.collect() }.toToolResult()
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

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

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
}
