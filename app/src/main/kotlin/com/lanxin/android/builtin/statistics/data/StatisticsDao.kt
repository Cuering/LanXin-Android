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

package com.lanxin.android.builtin.statistics.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StatisticsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProviderStat(stat: ProviderStatEntity): Long

    @Query(
        """
        SELECT * FROM provider_stats
        WHERE created_at >= :sinceMs
        ORDER BY created_at ASC
        """
    )
    suspend fun getProviderStatsSince(sinceMs: Long): List<ProviderStatEntity>

    @Query(
        """
        SELECT * FROM provider_stats
        WHERE created_at >= :sinceMs
        ORDER BY created_at DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentProviderStats(sinceMs: Long, limit: Int = 50): List<ProviderStatEntity>

    @Query("SELECT COUNT(*) FROM provider_stats")
    suspend fun countProviderStats(): Int

    @Query(
        """
        SELECT provider_id AS providerId,
               SUM(token_input + token_output) AS tokens,
               COUNT(*) AS calls
        FROM provider_stats
        WHERE created_at >= :sinceMs
        GROUP BY provider_id
        ORDER BY tokens DESC
        """
    )
    suspend fun sumTokensByProvider(sinceMs: Long): List<ProviderTokenSum>

    @Query("SELECT * FROM daily_stats WHERE day = :day LIMIT 1")
    suspend fun getDailyStat(day: String): DailyStatEntity?

    @Query(
        """
        SELECT * FROM daily_stats
        WHERE day >= :fromDay
        ORDER BY day ASC
        """
    )
    suspend fun getDailyStatsFrom(fromDay: String): List<DailyStatEntity>

    @Query(
        """
        SELECT * FROM daily_stats
        WHERE day >= :fromDay
        ORDER BY day ASC
        """
    )
    fun observeDailyStatsFrom(fromDay: String): Flow<List<DailyStatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyStat(stat: DailyStatEntity)

    @Query(
        """
        SELECT
            COALESCE(SUM(message_count), 0) AS messageCount,
            COALESCE(SUM(call_count), 0) AS callCount,
            COALESCE(SUM(success_count), 0) AS successCount,
            COALESCE(SUM(token_input), 0) AS tokenInput,
            COALESCE(SUM(token_output), 0) AS tokenOutput
        FROM daily_stats
        """
    )
    suspend fun getTotalAggregates(): DailyAggregate

    @Query(
        """
        SELECT
            COALESCE(SUM(message_count), 0) AS messageCount,
            COALESCE(SUM(call_count), 0) AS callCount,
            COALESCE(SUM(success_count), 0) AS successCount,
            COALESCE(SUM(token_input), 0) AS tokenInput,
            COALESCE(SUM(token_output), 0) AS tokenOutput
        FROM daily_stats
        WHERE day >= :fromDay
        """
    )
    suspend fun getAggregatesFrom(fromDay: String): DailyAggregate

    @Query("DELETE FROM provider_stats")
    suspend fun clearProviderStats()

    @Query("DELETE FROM daily_stats")
    suspend fun clearDailyStats()

    @Transaction
    suspend fun clearAll() {
        clearProviderStats()
        clearDailyStats()
    }
}

data class ProviderTokenSum(
    val providerId: String,
    val tokens: Int,
    val calls: Int
)

data class DailyAggregate(
    val messageCount: Int = 0,
    val callCount: Int = 0,
    val successCount: Int = 0,
    val tokenInput: Int = 0,
    val tokenOutput: Int = 0
)
