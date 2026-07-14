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

package com.lanxin.android.builtin.statistics.domain

/**
 * 单次模型调用统计（对齐 AstrBot ProviderStat）。
 */
data class ProviderStat(
    val id: Long = 0,
    val providerId: String,
    val providerModel: String? = null,
    val chatId: Int? = null,
    val status: String = STATUS_COMPLETED,
    val tokenInput: Int = 0,
    val tokenOutput: Int = 0,
    val isEstimated: Boolean = true,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
) {
    val tokenTotal: Int get() = tokenInput + tokenOutput
    val durationMs: Long get() = (endTimeMs - startTimeMs).coerceAtLeast(0L)

    companion object {
        const val STATUS_COMPLETED = "completed"
        const val STATUS_ERROR = "error"
    }
}

/**
 * 按日聚合统计（对齐 AstrBot PlatformStat 的时间桶思路，Android 端按天聚合）。
 */
data class DailyStat(
    val day: String,
    val messageCount: Int = 0,
    val callCount: Int = 0,
    val successCount: Int = 0,
    val tokenInput: Int = 0,
    val tokenOutput: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val tokenTotal: Int get() = tokenInput + tokenOutput
    val successRate: Float
        get() = if (callCount == 0) 0f else successCount.toFloat() / callCount
}

/**
 * 概览数据（设置页 / MCP stats_summary）。
 */
data class StatisticsSummary(
    val totalMessages: Int = 0,
    val totalCalls: Int = 0,
    val totalSuccess: Int = 0,
    val totalTokenInput: Int = 0,
    val totalTokenOutput: Int = 0,
    val todayMessages: Int = 0,
    val todayCalls: Int = 0,
    val todayTokens: Int = 0,
    val rangeDays: Int = 7,
    val daily: List<DailyStat> = emptyList(),
    val byProvider: List<ProviderTokenTotal> = emptyList()
) {
    val totalTokens: Int get() = totalTokenInput + totalTokenOutput
    val successRate: Float
        get() = if (totalCalls == 0) 0f else totalSuccess.toFloat() / totalCalls
}

data class ProviderTokenTotal(
    val providerId: String,
    val tokens: Int,
    val calls: Int
)

/**
 * 记录一次对话轮次所需的最小上下文。
 */
data class ChatTurnStatEvent(
    val providerId: String,
    val providerModel: String?,
    val chatId: Int?,
    val status: String,
    val inputTexts: List<String?>,
    val outputText: String?,
    val startTimeMs: Long,
    val endTimeMs: Long = System.currentTimeMillis(),
    /** 是否同时计一次用户消息（多平台并行时仅首个平台计 1） */
    val countAsMessage: Boolean = true
)
