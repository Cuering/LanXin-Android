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

import com.lanxin.android.builtin.statistics.data.DailyAggregate
import com.lanxin.android.builtin.statistics.data.DailyStatEntity
import com.lanxin.android.builtin.statistics.data.ProviderStatEntity
import com.lanxin.android.builtin.statistics.data.ProviderTokenSum
import com.lanxin.android.builtin.statistics.data.StatisticsDao
import com.lanxin.android.builtin.statistics.domain.ChatTurnStatEvent
import com.lanxin.android.builtin.statistics.domain.ProviderStat
import com.lanxin.android.builtin.statistics.domain.StatisticsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StatisticsRepositoryTest {

    private lateinit var dao: FakeStatisticsDao
    private lateinit var repository: StatisticsRepository

    @Before
    fun setup() {
        dao = FakeStatisticsDao()
        repository = StatisticsRepository(dao)
    }

    @Test
    fun `recordChatTurn stores provider and daily stats`() = runBlocking {
        val result = repository.recordChatTurn(
            ChatTurnStatEvent(
                providerId = "openai",
                providerModel = "gpt-4o",
                chatId = 1,
                status = ProviderStat.STATUS_COMPLETED,
                inputTexts = listOf("你好世界"),
                outputText = "hello!!",
                startTimeMs = System.currentTimeMillis() - 1000,
                countAsMessage = true
            )
        )

        assertTrue(result.id > 0)
        assertEquals("openai", result.providerId)
        assertTrue(result.tokenInput > 0)
        assertTrue(result.tokenOutput > 0)
        assertTrue(result.isEstimated)

        val summary = repository.getSummary(7)
        assertEquals(1, summary.totalMessages)
        assertEquals(1, summary.totalCalls)
        assertEquals(1, summary.totalSuccess)
        assertTrue(summary.totalTokens > 0)
        assertEquals(1, summary.todayMessages)
        assertTrue(summary.byProvider.any { it.providerId == "openai" })
    }

    @Test
    fun `countAsMessage false does not increase message count`() = runBlocking {
        repository.recordChatTurn(
            ChatTurnStatEvent(
                providerId = "a",
                providerModel = "m",
                chatId = null,
                status = ProviderStat.STATUS_COMPLETED,
                inputTexts = listOf("hi"),
                outputText = "ok",
                startTimeMs = System.currentTimeMillis(),
                countAsMessage = false
            )
        )
        val summary = repository.getSummary(1)
        assertEquals(0, summary.totalMessages)
        assertEquals(1, summary.totalCalls)
    }

    @Test
    fun `error status decreases success rate`() = runBlocking {
        repository.recordChatTurn(
            ChatTurnStatEvent(
                providerId = "a",
                providerModel = null,
                chatId = null,
                status = ProviderStat.STATUS_ERROR,
                inputTexts = listOf("x"),
                outputText = "err",
                startTimeMs = System.currentTimeMillis(),
                countAsMessage = true
            )
        )
        val summary = repository.getSummary(1)
        assertEquals(1, summary.totalCalls)
        assertEquals(0, summary.totalSuccess)
        assertEquals(0f, summary.successRate)
    }

    @Test
    fun `clearAll wipes data`() = runBlocking {
        repository.recordChatTurn(
            ChatTurnStatEvent(
                providerId = "a",
                providerModel = null,
                chatId = null,
                status = ProviderStat.STATUS_COMPLETED,
                inputTexts = listOf("x"),
                outputText = "y",
                startTimeMs = System.currentTimeMillis()
            )
        )
        repository.clearAll()
        val summary = repository.getSummary(7)
        assertEquals(0, summary.totalMessages)
        assertEquals(0, summary.totalCalls)
        assertEquals(0, summary.totalTokens)
    }

    private class FakeStatisticsDao : StatisticsDao {
        private var nextId = 1L
        private val providerStats = mutableListOf<ProviderStatEntity>()
        private val daily = MutableStateFlow<Map<String, DailyStatEntity>>(emptyMap())

        override suspend fun insertProviderStat(stat: ProviderStatEntity): Long {
            val id = nextId++
            providerStats.add(stat.copy(id = id))
            return id
        }

        override suspend fun getProviderStatsSince(sinceMs: Long): List<ProviderStatEntity> =
            providerStats.filter { it.createdAt >= sinceMs }.sortedBy { it.createdAt }

        override suspend fun getRecentProviderStats(sinceMs: Long, limit: Int): List<ProviderStatEntity> =
            providerStats.filter { it.createdAt >= sinceMs }
                .sortedByDescending { it.createdAt }
                .take(limit)

        override suspend fun countProviderStats(): Int = providerStats.size

        override suspend fun sumTokensByProvider(sinceMs: Long): List<ProviderTokenSum> =
            providerStats.filter { it.createdAt >= sinceMs }
                .groupBy { it.providerId }
                .map { (id, list) ->
                    ProviderTokenSum(
                        providerId = id,
                        tokens = list.sumOf { it.tokenInput + it.tokenOutput },
                        calls = list.size
                    )
                }
                .sortedByDescending { it.tokens }

        override suspend fun getDailyStat(day: String): DailyStatEntity? = daily.value[day]

        override suspend fun getDailyStatsFrom(fromDay: String): List<DailyStatEntity> =
            daily.value.values.filter { it.day >= fromDay }.sortedBy { it.day }

        override fun observeDailyStatsFrom(fromDay: String): Flow<List<DailyStatEntity>> =
            daily.map { map -> map.values.filter { it.day >= fromDay }.sortedBy { it.day } }

        override suspend fun upsertDailyStat(stat: DailyStatEntity) {
            daily.update { it + (stat.day to stat) }
        }

        override suspend fun getTotalAggregates(): DailyAggregate {
            val all = daily.value.values
            return DailyAggregate(
                messageCount = all.sumOf { it.messageCount },
                callCount = all.sumOf { it.callCount },
                successCount = all.sumOf { it.successCount },
                tokenInput = all.sumOf { it.tokenInput },
                tokenOutput = all.sumOf { it.tokenOutput }
            )
        }

        override suspend fun getAggregatesFrom(fromDay: String): DailyAggregate {
            val all = daily.value.values.filter { it.day >= fromDay }
            return DailyAggregate(
                messageCount = all.sumOf { it.messageCount },
                callCount = all.sumOf { it.callCount },
                successCount = all.sumOf { it.successCount },
                tokenInput = all.sumOf { it.tokenInput },
                tokenOutput = all.sumOf { it.tokenOutput }
            )
        }

        override suspend fun clearProviderStats() {
            providerStats.clear()
        }

        override suspend fun clearDailyStats() {
            daily.value = emptyMap()
        }
    }
}
