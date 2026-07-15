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

package com.lanxin.android.builtin.sync.data

import com.lanxin.android.builtin.sync.domain.SyncOutboxEntry
import com.lanxin.android.builtin.sync.domain.SyncOutboxOp
import com.lanxin.android.builtin.sync.domain.SyncItem
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 进程内 outbox（5.1 MVP）。
 *
 * 后续可替换为 Room 持久化实现，接口保持 [enqueue]/[snapshot]/[removeByIds]。
 */
@Singleton
class InMemorySyncOutbox @Inject constructor() {

    private val mutex = Mutex()
    private val seq = AtomicLong(1L)
    private val entries = LinkedHashMap<Long, SyncOutboxEntry>()

    suspend fun enqueue(item: SyncItem, op: SyncOutboxOp): SyncOutboxEntry = mutex.withLock {
        // 同 itemId 后写覆盖前写，减少重复 push
        val existingKey = entries.entries.firstOrNull { it.value.itemId == item.id }?.key
        if (existingKey != null) {
            entries.remove(existingKey)
        }
        val id = seq.getAndIncrement()
        val entry = SyncOutboxEntry(
            localId = id,
            itemId = item.id,
            payload = item,
            op = op,
            createdAt = System.currentTimeMillis()
        )
        entries[id] = entry
        entry
    }

    suspend fun snapshot(): List<SyncOutboxEntry> = mutex.withLock {
        entries.values.toList()
    }

    suspend fun removeByItemIds(itemIds: Collection<String>) = mutex.withLock {
        val idSet = itemIds.toHashSet()
        val toRemove = entries.filterValues { it.itemId in idSet }.keys.toList()
        toRemove.forEach { entries.remove(it) }
    }

    suspend fun markAttempt(localId: Long, error: String?) = mutex.withLock {
        val old = entries[localId] ?: return@withLock
        entries[localId] = old.copy(
            attempts = old.attempts + 1,
            lastError = error
        )
    }

    suspend fun clear() = mutex.withLock {
        entries.clear()
    }
}
