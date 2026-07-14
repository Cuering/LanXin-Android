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

package com.lanxin.android.builtin.statistics

import com.lanxin.android.builtin.statistics.domain.StatisticsRepository
import com.lanxin.android.plugin.LanXinPlugin
import com.lanxin.android.plugin.PluginContext
import com.lanxin.android.plugin.ToolDef
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 数据统计插件（对齐 AstrBot StatService）。
 * MCP 工具：stats_summary / stats_provider_tokens / stats_clear
 */
@Singleton
class StatisticsPlugin @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) : LanXinPlugin {

    override val id = "lanxin.statistics"
    override val name = "数据统计"
    override val version = "1.0.0"
    override val description = "统计对话轮数与 token 估算，本地优先存储"

    override suspend fun onLoad(context: PluginContext) {
        context.registerTool(
            ToolDef(
                name = "stats_summary",
                description = "获取统计概览：总消息数、调用数、token 估算、今日数据、按日序列与按提供商汇总",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "days",
                                buildJsonObject {
                                    put("type", "integer")
                                    put("description", "回溯天数，默认 7，范围 1-90")
                                }
                            )
                        }
                    )
                },
                handler = { args ->
                    val days = args["days"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 7
                    val summary = statisticsRepository.getSummary(days)
                    buildJsonObject {
                        put("range_days", summary.rangeDays)
                        put("total_messages", summary.totalMessages)
                        put("total_calls", summary.totalCalls)
                        put("total_success", summary.totalSuccess)
                        put("total_token_input", summary.totalTokenInput)
                        put("total_token_output", summary.totalTokenOutput)
                        put("total_tokens", summary.totalTokens)
                        put("success_rate", summary.successRate.toDouble())
                        put("today_messages", summary.todayMessages)
                        put("today_calls", summary.todayCalls)
                        put("today_tokens", summary.todayTokens)
                        put(
                            "daily",
                            buildJsonArray {
                                summary.daily.forEach { d ->
                                    add(
                                        buildJsonObject {
                                            put("day", d.day)
                                            put("message_count", d.messageCount)
                                            put("call_count", d.callCount)
                                            put("success_count", d.successCount)
                                            put("token_input", d.tokenInput)
                                            put("token_output", d.tokenOutput)
                                            put("token_total", d.tokenTotal)
                                        }
                                    )
                                }
                            }
                        )
                        put(
                            "by_provider",
                            buildJsonArray {
                                summary.byProvider.forEach { p ->
                                    add(
                                        buildJsonObject {
                                            put("provider_id", p.providerId)
                                            put("tokens", p.tokens)
                                            put("calls", p.calls)
                                        }
                                    )
                                }
                            }
                        )
                        put("note", "token 为本地估算（CJK≈1，其它≈4字符/token），非 API 精确 usage")
                    }
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "stats_provider_tokens",
                description = "获取近期按提供商的 token 明细与汇总（对齐 AstrBot provider-tokens）",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "days",
                                buildJsonObject {
                                    put("type", "integer")
                                    put("description", "回溯天数，默认 1，可选 1/3/7")
                                }
                            )
                            put(
                                "limit",
                                buildJsonObject {
                                    put("type", "integer")
                                    put("description", "明细条数上限，默认 50")
                                }
                            )
                        }
                    )
                },
                handler = { args ->
                    val days = args["days"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
                    val limit = args["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 50
                    val summary = statisticsRepository.getSummary(days)
                    val recent = statisticsRepository.getProviderStats(days, limit)
                    buildJsonObject {
                        put("days", summary.rangeDays)
                        put("range_total_tokens", summary.totalTokens)
                        put("range_total_calls", summary.totalCalls)
                        put("today_total_tokens", summary.todayTokens)
                        put("today_total_calls", summary.todayCalls)
                        put(
                            "range_by_provider",
                            buildJsonArray {
                                summary.byProvider.forEach { p ->
                                    add(
                                        buildJsonObject {
                                            put("provider_id", p.providerId)
                                            put("tokens", p.tokens)
                                            put("calls", p.calls)
                                        }
                                    )
                                }
                            }
                        )
                        put(
                            "recent",
                            buildJsonArray {
                                recent.forEach { r ->
                                    add(
                                        buildJsonObject {
                                            put("id", r.id)
                                            put("provider_id", r.providerId)
                                            put("provider_model", r.providerModel ?: "")
                                            put("status", r.status)
                                            put("token_input", r.tokenInput)
                                            put("token_output", r.tokenOutput)
                                            put("token_total", r.tokenTotal)
                                            put("is_estimated", r.isEstimated)
                                            put("duration_ms", r.durationMs)
                                            put("created_at", r.createdAt)
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "stats_clear",
                description = "清空本地统计数据（不可撤销）",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "confirm",
                                buildJsonObject {
                                    put("type", "boolean")
                                    put("description", "必须为 true 才执行清空")
                                }
                            )
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("confirm")) })
                },
                handler = { args ->
                    val confirm = args["confirm"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                        ?: false
                    if (!confirm) {
                        return@ToolDef buildJsonObject {
                            put("error", "需要 confirm=true 才会清空统计数据")
                        }
                    }
                    statisticsRepository.clearAll()
                    buildJsonObject {
                        put("ok", true)
                        put("message", "统计数据已清空")
                    }
                }
            )
        )
    }
}
