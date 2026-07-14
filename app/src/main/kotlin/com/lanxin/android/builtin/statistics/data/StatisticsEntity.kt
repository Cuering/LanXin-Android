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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lanxin.android.builtin.statistics.domain.DailyStat
import com.lanxin.android.builtin.statistics.domain.ProviderStat

/**
 * 对齐 AstrBot ProviderStat：每次模型调用一条明细。
 */
@Entity(
    tableName = "provider_stats",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["provider_id"]),
        Index(value = ["status"])
    ]
)
data class ProviderStatEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "provider_id")
    val providerId: String,

    @ColumnInfo(name = "provider_model")
    val providerModel: String? = null,

    @ColumnInfo(name = "chat_id")
    val chatId: Int? = null,

    @ColumnInfo(name = "status")
    val status: String = ProviderStat.STATUS_COMPLETED,

    @ColumnInfo(name = "token_input")
    val tokenInput: Int = 0,

    @ColumnInfo(name = "token_output")
    val tokenOutput: Int = 0,

    @ColumnInfo(name = "is_estimated")
    val isEstimated: Boolean = true,

    @ColumnInfo(name = "start_time_ms")
    val startTimeMs: Long = 0L,

    @ColumnInfo(name = "end_time_ms")
    val endTimeMs: Long = 0L,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 对齐 AstrBot PlatformStat：按天聚合消息/token。
 */
@Entity(tableName = "daily_stats")
data class DailyStatEntity(
    @PrimaryKey
    @ColumnInfo(name = "day")
    val day: String,

    @ColumnInfo(name = "message_count")
    val messageCount: Int = 0,

    @ColumnInfo(name = "call_count")
    val callCount: Int = 0,

    @ColumnInfo(name = "success_count")
    val successCount: Int = 0,

    @ColumnInfo(name = "token_input")
    val tokenInput: Int = 0,

    @ColumnInfo(name = "token_output")
    val tokenOutput: Int = 0,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

fun ProviderStatEntity.toDomain(): ProviderStat = ProviderStat(
    id = id,
    providerId = providerId,
    providerModel = providerModel,
    chatId = chatId,
    status = status,
    tokenInput = tokenInput,
    tokenOutput = tokenOutput,
    isEstimated = isEstimated,
    startTimeMs = startTimeMs,
    endTimeMs = endTimeMs,
    createdAt = createdAt
)

fun ProviderStat.toEntity(): ProviderStatEntity = ProviderStatEntity(
    id = id,
    providerId = providerId,
    providerModel = providerModel,
    chatId = chatId,
    status = status,
    tokenInput = tokenInput,
    tokenOutput = tokenOutput,
    isEstimated = isEstimated,
    startTimeMs = startTimeMs,
    endTimeMs = endTimeMs,
    createdAt = createdAt
)

fun DailyStatEntity.toDomain(): DailyStat = DailyStat(
    day = day,
    messageCount = messageCount,
    callCount = callCount,
    successCount = successCount,
    tokenInput = tokenInput,
    tokenOutput = tokenOutput,
    updatedAt = updatedAt
)

fun DailyStat.toEntity(): DailyStatEntity = DailyStatEntity(
    day = day,
    messageCount = messageCount,
    callCount = callCount,
    successCount = successCount,
    tokenInput = tokenInput,
    tokenOutput = tokenOutput,
    updatedAt = updatedAt
)
