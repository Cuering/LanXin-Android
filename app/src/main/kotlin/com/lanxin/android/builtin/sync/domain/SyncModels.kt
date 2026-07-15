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

package com.lanxin.android.builtin.sync.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 同步条目类型。 */
object SyncItemType {
    const val MEMORY = "memory"
    const val KNOWLEDGE = "knowledge"

    val ALL = listOf(MEMORY, KNOWLEDGE)
}

/** 条目来源。 */
object SyncSource {
    const val ANDROID = "android"
    const val ASTRBOT = "astrbot"
}

/**
 * 跨端同步条目（协议 v1）。
 *
 * 见 `docs/sync-protocol.md`。
 */
@Serializable
data class SyncItem(
    val id: String,
    val type: String,
    val content: String = "",
    @SerialName("updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    val source: String = SyncSource.ANDROID,
    val subtype: String? = null,
    val importance: Float? = null,
    val metadata: String? = null,
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("device_id") val deviceId: String? = null
)

@Serializable
data class SyncPullRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("user_id") val userId: String? = null,
    val since: Long = 0L,
    val cursor: String? = null,
    val types: List<String> = emptyList(),
    val limit: Int = 200
)

@Serializable
data class SyncPullResponse(
    val items: List<SyncItem> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("server_time") val serverTime: Long = 0L,
    @SerialName("has_more") val hasMore: Boolean = false
)

@Serializable
data class SyncPushRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("user_id") val userId: String? = null,
    val items: List<SyncItem> = emptyList()
)

@Serializable
data class SyncPushRejected(
    val id: String,
    val reason: String = ""
)

@Serializable
data class SyncPushResponse(
    val accepted: Int = 0,
    val rejected: List<SyncPushRejected> = emptyList(),
    val applied: List<SyncItem> = emptyList(),
    @SerialName("server_time") val serverTime: Long = 0L
)

/** Outbox 操作类型。 */
enum class SyncOutboxOp {
    UPSERT,
    DELETE
}

/**
 * 本地变更队列条目（内存/Room 共用结构）。
 * 5.1 默认 InMemory outbox，可后续落库。
 */
data class SyncOutboxEntry(
    val localId: Long = 0L,
    val itemId: String,
    val payload: SyncItem,
    val op: SyncOutboxOp,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val lastError: String? = null
)

/** 一次完整同步的结果摘要。 */
data class SyncCycleResult(
    val pushed: Int = 0,
    val pulled: Int = 0,
    val merged: Int = 0,
    val rejected: Int = 0,
    val serverTime: Long = 0L,
    val error: String? = null
) {
    val ok: Boolean get() = error == null
}

/** 同步配置快照。 */
data class SyncConfig(
    val baseUrl: String = "",
    val token: String = "",
    val userId: String = "",
    val enabled: Boolean = false
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && token.isNotBlank()
}
