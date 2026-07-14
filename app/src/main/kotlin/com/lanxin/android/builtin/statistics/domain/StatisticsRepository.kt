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

import com.lanxin.android.builtin.statistics.data.DailyStatEntity
import com.lanxin.android.builtin.statistics.data.StatisticsDao
import com.lanxin.android.builtin.statistics.data.toDomain
import com.lanxin.android.builtin.statistics.data.toEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 统计仓库（对齐 AstrBot StatService + DB helper）。
 * 本地优先：Room 存明细 + 日聚合。
 */
@Singleton
class StatisticsRepository @Inject constructor(
    private val dao: StatisticsDao
) {
    private val writeMutex = Mutex()
    private val dayFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun observeDailyStats(days: Int = 7): Flow<List<DailyStat>> {
        val fromDay = LocalDate.now().minusDays((days - 1).toLong().coerceAtLeast(0)).format(dayFormatter)
        return dao.observeDailyStatsFrom(fromDay).map { list -> list.map { it.toDomain() } }
    }

    suspend fun recordChatTurn(event: ChatTurnStatEvent): ProviderStat = writeMutex.withLock {
        val tokenInput = TokenEstimator.estimateMany(event.inputTexts)
        val tokenOutput = TokenEstimator.estimate(event.outputText)
        val now = event.endTimeMs
        val day = dayOf(now)

        val stat = ProviderStat(
            providerId = event.providerId.ifBlank { "unknown" },
            providerModel = event.providerModel,
            chatId = event.chatId,
            status = event.status,
            tokenInput = tokenInput,
            tokenOutput = tokenOutput,
            isEstimated = true,
            startTimeMs = event.startTimeMs,
            endTimeMs = now,
            createdAt = now
        )
        val id = dao.insertProviderStat(stat.toEntity())
        bumpDaily(
            day = day,
            messageDelta = if (event.countAsMessage) 1 else 0,
            callDelta = 1,
            successDelta = if (event.status != ProviderStat.STATUS_ERROR) 1 else 0,
            tokenInputDelta = tokenInput,
            tokenOutputDelta = tokenOutput
        )
        stat.copy(id = id)
    }

    suspend fun getSummary(days: Int = 7): StatisticsSummary {
        val safeDays = days.coerceIn(1, 90)
        val today = LocalDate.now()
        val fromDay = today.minusDays((safeDays - 1).toLong()).format(dayFormatter)
        val todayStr = today.format(dayFormatter)

        val total = dao.getTotalAggregates()
        val todayAgg = dao.getDailyStat(todayStr)
        val daily = dao.getDailyStatsFrom(fromDay).map { it.toDomain() }
        val sinceMs = today.minusDays((safeDays - 1).toLong())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val byProvider = dao.sumTokensByProvider(sinceMs).map {
            ProviderTokenTotal(
                providerId = it.providerId,
                tokens = it.tokens,
                calls = it.calls
            )
        }

        return StatisticsSummary(
            totalMessages = total.messageCount,
            totalCalls = total.callCount,
            totalSuccess = total.successCount,
            totalTokenInput = total.tokenInput,
            totalTokenOutput = total.tokenOutput,
            todayMessages = todayAgg?.messageCount ?: 0,
            todayCalls = todayAgg?.callCount ?: 0,
            todayTokens = (todayAgg?.tokenInput ?: 0) + (todayAgg?.tokenOutput ?: 0),
            rangeDays = safeDays,
            daily = daily,
            byProvider = byProvider
        )
    }

    suspend fun getProviderStats(days: Int = 1, limit: Int = 100): List<ProviderStat> {
        val safeDays = days.coerceIn(1, 90)
        val sinceMs = LocalDate.now()
            .minusDays((safeDays - 1).toLong())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return dao.getRecentProviderStats(sinceMs, limit).map { it.toDomain() }
    }

    suspend fun clearAll() = writeMutex.withLock {
        dao.clearAll()
    }

    private suspend fun bumpDaily(
        day: String,
        messageDelta: Int,
        callDelta: Int,
        successDelta: Int,
        tokenInputDelta: Int,
        tokenOutputDelta: Int
    ) {
        val existing = dao.getDailyStat(day)
        val updated = DailyStatEntity(
            day = day,
            messageCount = (existing?.messageCount ?: 0) + messageDelta,
            callCount = (existing?.callCount ?: 0) + callDelta,
            successCount = (existing?.successCount ?: 0) + successDelta,
            tokenInput = (existing?.tokenInput ?: 0) + tokenInputDelta,
            tokenOutput = (existing?.tokenOutput ?: 0) + tokenOutputDelta,
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertDailyStat(updated)
    }

    private fun dayOf(epochMs: Long): String {
        return Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(dayFormatter)
    }
}
