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

import com.lanxin.android.plugins.memory.data.memory.MemoryEntity
import com.lanxin.android.plugins.memory.data.memory.MemoryType

/**
 * MemoryEntity ↔ SyncItem 映射（memory 优先路径）。
 *
 * knowledge 映射在 5.1 仅占位，见 [knowledgePlaceholderId]。
 */
object SyncItemMapper {

    private const val MEMORY_ID_PREFIX = "memory:"
    private const val KNOWLEDGE_ID_PREFIX = "knowledge:"

    fun memorySyncId(localId: Long): String = "$MEMORY_ID_PREFIX$localId"

    fun parseMemoryLocalId(syncId: String): Long? {
        if (!syncId.startsWith(MEMORY_ID_PREFIX)) return null
        return syncId.removePrefix(MEMORY_ID_PREFIX).toLongOrNull()
    }

    fun knowledgePlaceholderId(localKey: String): String =
        "$KNOWLEDGE_ID_PREFIX$localKey"

    fun fromMemory(
        entity: MemoryEntity,
        deviceId: String?,
        deleted: Boolean = false,
        updatedAtOverride: Long? = null
    ): SyncItem {
        val updated = updatedAtOverride
            ?: entity.lastAccessedAt
            ?: entity.createdAt
        return SyncItem(
            id = memorySyncId(entity.id),
            type = SyncItemType.MEMORY,
            content = if (deleted) "" else entity.content,
            updatedAt = updated,
            deleted = deleted,
            source = SyncSource.ANDROID,
            subtype = entity.type,
            importance = entity.importance,
            metadata = entity.metadata,
            createdAt = entity.createdAt,
            deviceId = deviceId
        )
    }

    /**
     * 将远端 memory 条目转为用于写入本地的字段包。
     * 不直接构造 MemoryEntity（id 策略由 Repository 决定）。
     */
    data class MemoryApplyDraft(
        val localId: Long?,
        val content: String,
        val type: String,
        val importance: Float,
        val metadata: String?,
        val createdAt: Long,
        val deleted: Boolean,
        val updatedAt: Long
    )

    fun toMemoryDraft(item: SyncItem): MemoryApplyDraft {
        require(item.type == SyncItemType.MEMORY) {
            "expected type=memory, got ${item.type}"
        }
        return MemoryApplyDraft(
            localId = parseMemoryLocalId(item.id),
            content = item.content,
            type = item.subtype?.takeIf { it in MemoryType.ALL } ?: MemoryType.CHAT,
            importance = (item.importance ?: 5f).coerceIn(1f, 10f),
            metadata = item.metadata,
            createdAt = item.createdAt ?: item.updatedAt,
            deleted = item.deleted,
            updatedAt = item.updatedAt
        )
    }
}
