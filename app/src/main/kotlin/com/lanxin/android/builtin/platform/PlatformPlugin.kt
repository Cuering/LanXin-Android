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
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 手机平台专属工具插件。
 *
 * MCP 工具：
 * - clipboard_get / clipboard_set
 * - app_install_check
 * - system_info
 * - file_read / file_write / file_list
 * - web_search（受 [WebSearchSettings] 门闸；默认关）
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
    private val webSearchSettings: WebSearchSettings
) : LanXinPlugin {

    override val id = "lanxin.platform"
    override val name = "手机平台工具"
    override val version = "1.2.0"
    override val description =
        "Android 专属能力：剪贴板、已安装应用、系统信息、本地文件、联网搜索（默认关）、Intent 唤起"

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
                        val config = webSearchSettings.getConfig()
                        WebSearchGate.denyIfDisabled(config)?.let { return@runCatching it }
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
