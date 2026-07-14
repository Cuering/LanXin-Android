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
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 查询本机已安装应用。
 *
 * Android 11+ 完整列表依赖 [android.permission.QUERY_ALL_PACKAGES]；
 * 也可按 package 精确查询单个应用是否安装。
 */
@Singleton
class AppInstallCheckTool @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val pm: PackageManager get() = context.packageManager

    /**
     * @param packageName 精确包名；非空时只查该包是否安装
     * @param query 模糊过滤（包名或应用名包含，忽略大小写）
     * @param includeSystem 是否包含系统应用，默认 false
     * @param limit 最多返回条数，默认 50，上限 500
     */
    fun check(
        packageName: String? = null,
        query: String? = null,
        includeSystem: Boolean = false,
        limit: Int = 50
    ): JsonObject {
        val safeLimit = limit.coerceIn(1, 500)

        // 精确查询单个包
        if (!packageName.isNullOrBlank()) {
            return checkSingle(packageName.trim())
        }

        val apps = runCatching {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }.getOrElse {
            return buildJsonObject {
                put("ok", false)
                put("error", "无法枚举已安装应用：${it.message}")
                put("hint", "需要 QUERY_ALL_PACKAGES 权限，或改用 package_name 精确查询")
            }
        }

        val q = query?.trim()?.lowercase().orEmpty()
        val filtered = apps.asSequence()
            .filter { includeSystem || (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { ai ->
                val label = runCatching { pm.getApplicationLabel(ai).toString() }
                    .getOrDefault(ai.packageName)
                AppEntry(
                    packageName = ai.packageName,
                    label = label,
                    system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    enabled = ai.enabled
                )
            }
            .filter {
                q.isEmpty() ||
                    it.packageName.lowercase().contains(q) ||
                    it.label.lowercase().contains(q)
            }
            .sortedBy { it.label.lowercase() }
            .toList()

        val page = filtered.take(safeLimit)
        return buildJsonObject {
            put("ok", true)
            put("total_matched", filtered.size)
            put("returned", page.size)
            put("include_system", includeSystem)
            put("query", query.orEmpty())
            put(
                "apps",
                buildJsonArray {
                    page.forEach { entry ->
                        add(
                            buildJsonObject {
                                put("package_name", entry.packageName)
                                put("label", entry.label)
                                put("system", entry.system)
                                put("enabled", entry.enabled)
                            }
                        )
                    }
                }
            )
            if (filtered.size > safeLimit) {
                put("truncated", true)
                put("hint", "结果已截断，可用 query / package_name 缩小范围，或增大 limit")
            }
        }
    }

    private fun checkSingle(packageName: String): JsonObject {
        return try {
            val ai = pm.getApplicationInfo(packageName, 0)
            val label = runCatching { pm.getApplicationLabel(ai).toString() }
                .getOrDefault(packageName)
            val pkgInfo = runCatching {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }.getOrNull()
            buildJsonObject {
                put("ok", true)
                put("installed", true)
                put("package_name", packageName)
                put("label", label)
                put("system", (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
                put("enabled", ai.enabled)
                put("version_name", pkgInfo?.versionName.orEmpty())
                put(
                    "version_code",
                    if (pkgInfo != null) pkgInfo.longVersionCode else 0L
                )
            }
        } catch (_: PackageManager.NameNotFoundException) {
            buildJsonObject {
                put("ok", true)
                put("installed", false)
                put("package_name", packageName)
            }
        }
    }

    private data class AppEntry(
        val packageName: String,
        val label: String,
        val system: Boolean,
        val enabled: Boolean
    )
}
