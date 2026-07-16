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

import com.lanxin.android.builtin.sync.domain.LwwDecision
import com.lanxin.android.builtin.sync.domain.LwwResolver
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 同步引擎实现（Phase 5.1 骨架 + 5.2 LWW 完整路径）。
 *
 * - outbox：InMemory（进程级）
 * - memory：enqueue / pull 合并写入 MemoryRepository
 * - knowledge：列表层 LWW 可测；落库适配后置
 * - push.applied 与 pull items 统一走 [applyRemoteItem] → LWW
 *
 * 不接入 ChatViewModel，不污染会话。
 *
 * HTTP 层直接依赖 [HttpSyncClient]（避免 domain 接口在 KSP 中成为 ERROR type）。
 */
class DefaultSyncRepository(
    private val syncClient: HttpSyncClient,
    private val preferences: SyncPreferences,
    private val outbox: InMemorySyncOutbox,
    private val memoryRepository: MemoryRepository,
    private val preferRemote: Boolean = true
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
        var merged = 0
        var skipped = 0
        var conflictResolved = 0
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
                    // 服务端 LWW 最终态：与 pull 共用同一入口
                    resp.applied.forEach { item ->
                        val outcome = applyRemoteItem(item)
                        merged += outcome.mergedDelta
                        skipped += outcome.skippedDelta
                        conflictResolved += outcome.conflictDelta
                    }
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
                merged = merged,
                skipped = skipped,
                conflictResolved = conflictResolved,
                serverTime = serverTime,
                error = "pull 失败: ${e.message}"
            )
        }

        if (pullResp.serverTime > 0) {
            serverTime = pullResp.serverTime
        }

        // 3) merge：统一 LWW 入口（memory 落库；knowledge 列表层可后续扩展）
        for (remote in pullResp.items) {
            val outcome = applyRemoteItem(remote)
            merged += outcome.mergedDelta
            skipped += outcome.skippedDelta
            conflictResolved += outcome.conflictDelta
        }

        if (serverTime > 0) {
            preferences.setLastServerTime(serverTime)
        }

        return SyncCycleResult(
            pushed = pushed,
            pulled = pullResp.items.size,
            merged = merged,
            rejected = rejected,
            skipped = skipped,
            conflictResolved = conflictResolved,
            serverTime = serverTime
        )
    }

    /**
     * push.applied 与 pull 共用的远端条目应用入口。
     * memory 走 LWW + MemoryRepository；其它 type 暂不落库（计 skipped）。
     */
    private suspend fun applyRemoteItem(remote: SyncItem): ApplyOutcome {
        return when (remote.type) {
            SyncItemType.MEMORY -> applyMemoryRemote(remote)
            else -> ApplyOutcome(skippedDelta = 1) // knowledge 等：无存储适配时跳过
        }
    }

    /**
     * 将远端 memory 条目按 LWW 合并进本地。
     */
    private suspend fun applyMemoryRemote(remote: SyncItem): ApplyOutcome {
        val draft = SyncItemMapper.toMemoryDraft(remote)
        val localId = draft.localId
        val existing: MemoryEntity? = if (localId != null && localId > 0) {
            memoryRepository.getMemoryById(localId)
        } else {
            null
        }

        val localItem: SyncItem? = existing?.let {
            SyncItemMapper.fromMemory(it, deviceId = null)
        }

        val decision = LwwResolver.decide(localItem, remote, preferRemote)
        when (decision) {
            LwwDecision.SKIP -> return ApplyOutcome(skippedDelta = 1)
            LwwDecision.APPLY_NEW -> {
                // 本地无此 id：tombstone 无需落库
                if (remote.deleted) return ApplyOutcome(skippedDelta = 1)
                memoryRepository.addMemory(
                    content = draft.content,
                    type = draft.type,
                    importance = draft.importance,
                    metadata = draft.metadata
                )
                return ApplyOutcome(mergedDelta = 1)
            }
            LwwDecision.APPLY -> {
                val conflict = LwwResolver.isConflictResolution(localItem, remote, preferRemote)
                if (existing == null) {
                    // decide 在 existing==null 时只返回 APPLY_NEW，此处防御
                    if (remote.deleted) return ApplyOutcome(skippedDelta = 1)
                    memoryRepository.addMemory(
                        content = draft.content,
                        type = draft.type,
                        importance = draft.importance,
                        metadata = draft.metadata
                    )
                    return ApplyOutcome(mergedDelta = 1)
                }
                if (remote.deleted) {
                    memoryRepository.deleteMemory(existing.id)
                    return ApplyOutcome(
                        mergedDelta = 1,
                        conflictDelta = if (conflict) 1 else 0
                    )
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
                return ApplyOutcome(
                    mergedDelta = 1,
                    conflictDelta = if (conflict) 1 else 0
                )
            }
        }
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

    override suspend fun enqueueMemoryDelete(localId: Long, contentSnapshot: String) {
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

    /** 单条 apply 的计数增量。 */
    private data class ApplyOutcome(
        val mergedDelta: Int = 0,
        val skippedDelta: Int = 0,
        val conflictDelta: Int = 0
    )
}
