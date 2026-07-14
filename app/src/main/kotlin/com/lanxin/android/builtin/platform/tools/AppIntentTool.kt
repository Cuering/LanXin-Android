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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 通过 [Intent] 唤起其他 App / 系统能力。
 *
 * 支持：
 * - 通用 action + data URI + package
 * - Deep Link / 打开 URL
 * - 拨号、短信、邮件、分享、应用设置、按包名启动
 *
 * 从 Application Context 启动时自动加 [Intent.FLAG_ACTIVITY_NEW_TASK]。
 */
@Singleton
class AppIntentTool @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * @param action Intent action，如 android.intent.action.VIEW；可省略，按 type 推断
     * @param data URI 字符串（https / tel / sms / mailto / 自定义 scheme）
     * @param packageName 目标包名（可选，强制指定组件所在包）
     * @param type MIME type（可选）
     * @param extras 简单 string extras，key=value 逗号分隔（可选）
     * @param type 便捷类型：view / url / dial / call / sms / mail / share / settings / launch
     * @param text 分享/短信/邮件正文
     * @param chooserTitle 使用系统选择器时的标题
     * @param useChooser 是否包一层 createChooser
     */
    fun launch(
        action: String? = null,
        data: String? = null,
        packageName: String? = null,
        mimeType: String? = null,
        extras: String? = null,
        type: String? = null,
        text: String? = null,
        chooserTitle: String? = null,
        useChooser: Boolean = false
    ): JsonObject {
        return try {
            val intent = buildIntent(
                action = action,
                data = data,
                packageName = packageName,
                mimeType = mimeType,
                extras = extras,
                type = type,
                text = text
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val resolved = intent.resolveActivity(context.packageManager)
            val toStart = if (useChooser || type.equals("share", ignoreCase = true)) {
                Intent.createChooser(intent, chooserTitle ?: "选择应用").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                intent
            }

            context.startActivity(toStart)
            buildJsonObject {
                put("ok", true)
                put("started", true)
                put("action", intent.action.orEmpty())
                put("data", intent.dataString.orEmpty())
                put("package", intent.`package`.orEmpty())
                put("type", type.orEmpty())
                put("resolved_activity", resolved?.flattenToString().orEmpty())
                put("used_chooser", useChooser || type.equals("share", ignoreCase = true))
            }
        } catch (e: ActivityNotFoundException) {
            buildJsonObject {
                put("ok", false)
                put("started", false)
                put("error", "没有可处理的应用：${e.message}")
            }
        } catch (e: SecurityException) {
            buildJsonObject {
                put("ok", false)
                put("started", false)
                put("error", "权限不足：${e.message}")
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("ok", false)
                put("started", false)
                put("error", e.message ?: e.toString())
            }
        }
    }

    internal fun buildIntent(
        action: String?,
        data: String?,
        packageName: String?,
        mimeType: String?,
        extras: String?,
        type: String?,
        text: String?
    ): Intent {
        val t = type?.trim()?.lowercase().orEmpty()
        val intent = when (t) {
            "view", "url", "deeplink", "open" -> {
                val uri = requireUri(data, "data/url")
                Intent(Intent.ACTION_VIEW, uri)
            }
            "dial", "phone" -> {
                val uri = when {
                    !data.isNullOrBlank() && data.startsWith("tel:", ignoreCase = true) ->
                        Uri.parse(data)
                    !data.isNullOrBlank() -> Uri.parse("tel:${data.trim()}")
                    else -> error("dial 需要 data 电话号码")
                }
                Intent(Intent.ACTION_DIAL, uri)
            }
            "call" -> {
                val uri = when {
                    !data.isNullOrBlank() && data.startsWith("tel:", ignoreCase = true) ->
                        Uri.parse(data)
                    !data.isNullOrBlank() -> Uri.parse("tel:${data.trim()}")
                    else -> error("call 需要 data 电话号码")
                }
                Intent(Intent.ACTION_CALL, uri)
            }
            "sms", "smsto" -> {
                val number = data?.removePrefix("sms:")?.removePrefix("smsto:")?.trim().orEmpty()
                val uri = if (number.isBlank()) {
                    Uri.parse("smsto:")
                } else {
                    Uri.parse("smsto:$number")
                }
                Intent(Intent.ACTION_SENDTO, uri).apply {
                    if (!text.isNullOrBlank()) putExtra("sms_body", text)
                }
            }
            "mail", "email", "mailto" -> {
                val uri = when {
                    !data.isNullOrBlank() && data.startsWith("mailto:", ignoreCase = true) ->
                        Uri.parse(data)
                    !data.isNullOrBlank() -> Uri.parse("mailto:${data.trim()}")
                    else -> Uri.parse("mailto:")
                }
                Intent(Intent.ACTION_SENDTO, uri).apply {
                    if (!text.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, text)
                }
            }
            "share", "send" -> {
                Intent(Intent.ACTION_SEND).apply {
                    this.type = mimeType?.ifBlank { null } ?: "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text ?: data.orEmpty())
                }
            }
            "settings", "app_settings" -> {
                if (!packageName.isNullOrBlank() || t == "app_settings") {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        this.data = Uri.fromParts(
                            "package",
                            packageName?.ifBlank { null } ?: context.packageName,
                            null
                        )
                    }
                } else {
                    Intent(Settings.ACTION_SETTINGS)
                }
            }
            "launch", "app" -> {
                val pkg = packageName?.trim().orEmpty()
                if (pkg.isEmpty()) error("launch 需要 package_name")
                context.packageManager.getLaunchIntentForPackage(pkg)
                    ?: error("包 $pkg 未安装或无启动 Activity")
            }
            else -> {
                val act = action?.ifBlank { null } ?: Intent.ACTION_VIEW
                Intent(act).apply {
                    if (!data.isNullOrBlank()) this.data = Uri.parse(data.trim())
                    if (!mimeType.isNullOrBlank()) this.type = mimeType
                }
            }
        }

        if (!packageName.isNullOrBlank() && t != "launch" && t != "app") {
            intent.`package` = packageName.trim()
        }
        applyExtras(intent, extras)
        return intent
    }

    private fun requireUri(data: String?, field: String): Uri {
        if (data.isNullOrBlank()) error("$field 必填")
        return Uri.parse(data.trim())
    }

    private fun applyExtras(intent: Intent, extras: String?) {
        if (extras.isNullOrBlank()) return
        extras.split(',').forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@forEach
            val key = pair.substring(0, idx).trim()
            val value = pair.substring(idx + 1).trim().replace("%2C", ",")
            if (key.isNotEmpty()) intent.putExtra(key, value)
        }
    }
}
