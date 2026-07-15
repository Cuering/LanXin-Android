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

import com.lanxin.android.builtin.sync.domain.LwwResolver
import com.lanxin.android.builtin.sync.domain.SyncClient
import com.lanxin.android.builtin.sync.domain.SyncCycleResult
import com.lanxin.android.builtin.sync.domain.SyncItem
import com.lanxin.android.builtin.sync.domain.SyncItemMapper
import com.lanxin.android.builtin.sync.domain.SyncItemType
import com.lanxin.android.builtin.sync.domain.SyncOutboxOp
import com.lanxin.android.builtin.sync.domain.SyncPullRequest
import com.lanxin.android.builtin.sync.domain.SyncPushRequest
import com.lanxin.android.builtin.sync.domain.SyncRepository
import com.lanxin.android.plugins.memory.data.memory.MemoryEntity
import com.lanxin.android.plugins.memory.data.memory.MemoryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 同步引擎骨架实现。
 *
 * - outbox：InMemory（进程级）
 * - memory：enqueue / pull 合并写入 MemoryRepository
 * - knowledge：接口预留，pull 时忽略非 memory（或仅记录）
 *
 * 不接入 ChatViewModel，不污染会话。
 */
@Singleton
class DefaultSyncRepository @Inject constructor(
    private val syncClient: SyncClient,
    private val preferences: SyncPreferences,
    private val outbox: InMemorySyncOutbox,
    private val memoryRepository: MemoryRepository
) : SyncRepository {

    private val cycleMutex = Mutex()

    override suspend fun enqueue(item: SyncItem, op: SyncOutboxOp) {
        val deviceId = preferences.getOrCreateDeviceId()
        val stamped = if (item.deviceId.isNullOrBlank()) {
            item.copy(deviceId = deviceId)
        } else {
            item
        }
        outbox.enqueue(stamped, op)
    }

    override suspend fun peekOutbox() = outbox.snapshot()

    override suspend fun lastServerTime(): Long = preferences.getLastServerTime()

    override suspend fun deviceId(): String = preferences.getOrCreateDeviceId()

    override suspend fun syncOnce(types: List<String>): SyncCycleResult = cycleMutex.withLock {
        withContext(Dispatchers.IO) {
            runCycle(types.ifEmpty { listOf(SyncItemType.MEMORY) })
        }
    }

    private suspend fun runCycle(types: List<String>): SyncCycleResult {
        val config = preferences.getConfig()
        if (!config.isConfigured) {
            return SyncCycleResult(error = "同步未配置 baseUrl/token")
        }
        if (!config.enabled) {
            return SyncCycleResult(error = "同步未启用")
        }

        val deviceId = preferences.getOrCreateDeviceId()
        val userId = config.userId.ifBlank { null }

        // 1) push outbox
        val pending = outbox.snapshot()
        var pushed = 0
        var rejected = 0
        var serverTime = preferences.getLastServerTime()

        if (pending.isNotEmpty()) {
            val pushResult = syncClient.push(
                SyncPushRequest(
                    deviceId = deviceId,
                    userId = userId,
                    items = pending.map { it.payload }
                )
            )
            pushResult.fold(
                onSuccess = { resp ->
                    pushed = resp.accepted
                    rejected = resp.rejected.size
                    if (resp.serverTime > 0) serverTime = resp.serverTime
                    val rejectedIds = resp.rejected.map { it.id }.toHashSet()
                    val acceptedIds = pending.map { it.itemId }.filter { it !in rejectedIds }
                    outbox.removeByItemIds(acceptedIds)
                    pending.filter { it.itemId in rejectedIds }.forEach { entry ->
                        outbox.markAttempt(entry.localId, "rejected")
                    }
                    // 服务端 LWW 最终态可选回写
                    resp.applied
                        .filter { it.type == SyncItemType.MEMORY }
                        .forEach { applyMemoryRemote(it) }
                },
                onFailure = { e ->
                    pending.forEach { outbox.markAttempt(it.localId, e.message) }
                    return SyncCycleResult(
                        pushed = 0,
                        rejected = 0,
                        error = "push 失败: ${e.message}"
                    )
                }
            )
        }

        // 2) pull
        val since = preferences.getLastServerTime()
        val pullResult = syncClient.pull(
            SyncPullRequest(
                deviceId = deviceId,
                userId = userId,
                since = since,
                types = types,
                limit = 200
            )
        )

        val pullResp = pullResult.getOrElse { e ->
            return SyncCycleResult(
                pushed = pushed,
                rejected = rejected,
                serverTime = serverTime,
                error = "pull 失败: ${e.message}"
            )
        }

        if (pullResp.serverTime > 0) {
            serverTime = pullResp.serverTime
        }

        // 3) merge memory
        var merged = 0
        val memoryItems = pullResp.items.filter { it.type == SyncItemType.MEMORY }
        for (remote in memoryItems) {
            if (applyMemoryRemote(remote)) merged++
        }

        if (serverTime > 0) {
            preferences.setLastServerTime(serverTime)
        }

        return SyncCycleResult(
            pushed = pushed,
            pulled = pullResp.items.size,
            merged = merged,
            rejected = rejected,
            serverTime = serverTime
        )
    }

    /**
     * 将远端 memory 条目按 LWW 合并进本地。
     * @return 是否发生写入/删除
     */
    private suspend fun applyMemoryRemote(remote: SyncItem): Boolean {
        val draft = SyncItemMapper.toMemoryDraft(remote)
        val localId = draft.localId
        val existing: MemoryEntity? = if (localId != null && localId > 0) {
            memoryRepository.getMemoryById(localId)
        } else {
            null
        }

        if (existing != null) {
            val localItem = SyncItemMapper.fromMemory(existing, deviceId = null)
            if (!LwwResolver.shouldApply(localItem, remote, preferRemote = true)) {
                return false
            }
            if (remote.deleted) {
                memoryRepository.deleteMemory(existing.id)
                return true
            }
            memoryRepository.updateMemory(
                existing.copy(
                    content = draft.content,
                    type = draft.type,
                    importance = draft.importance,
                    metadata = draft.metadata,
                    lastAccessedAt = draft.updatedAt
                )
            )
            return true
        }

        // 本地无此 id：tombstone 无需落库
        if (remote.deleted) return false
        memoryRepository.addMemory(
            content = draft.content,
            type = draft.type,
            importance = draft.importance,
            metadata = draft.metadata
        )
        return true
    }

    /**
     * 供 UI / MemoryRepo 钩子调用：本地新增后入队。
     */
    override suspend fun enqueueMemoryUpsert(entity: MemoryEntity) {
        val deviceId = preferences.getOrCreateDeviceId()
        val item = SyncItemMapper.fromMemory(
            entity = entity,
            deviceId = deviceId,
            deleted = false,
            updatedAtOverride = System.currentTimeMillis()
        )
        enqueue(item, SyncOutboxOp.UPSERT)
    }

    override suspend fun enqueueMemoryDelete(localId: Long, contentSnapshot: String = "") {
        val deviceId = preferences.getOrCreateDeviceId()
        val now = System.currentTimeMillis()
        val item = SyncItem(
            id = SyncItemMapper.memorySyncId(localId),
            type = SyncItemType.MEMORY,
            content = contentSnapshot,
            updatedAt = now,
            deleted = true,
            source = com.lanxin.android.builtin.sync.domain.SyncSource.ANDROID,
            deviceId = deviceId
        )
        enqueue(item, SyncOutboxOp.DELETE)
    }
}
