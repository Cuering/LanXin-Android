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

/**
 * LWW（Last-Write-Wins）冲突解析 — Phase 5.2 预留 helper。
 *
 * 比较顺序见 `docs/sync-protocol.md` §4：
 * 1. updated_at 较大者胜
 * 2. 相等时 deleted=true 优先（防复活）
 * 3. 相等时 source 字典序较大者胜
 * 4. 仍相等时由 [preferRemote] 决定
 */
object LwwResolver {

    /**
     * 在两条同 id 条目中选出胜者。
     * 调用方应保证 [local].id == [remote].id（若 id 不同，行为未定义，返回 [remote]）。
     */
    fun pick(
        local: SyncItem,
        remote: SyncItem,
        preferRemote: Boolean = true
    ): SyncItem {
        if (local.id != remote.id) return remote

        val byTime = remote.updatedAt.compareTo(local.updatedAt)
        if (byTime > 0) return remote
        if (byTime < 0) return local

        // updated_at 相同：tombstone 优先
        if (remote.deleted != local.deleted) {
            return if (remote.deleted) remote else local
        }

        val bySource = remote.source.compareTo(local.source)
        if (bySource > 0) return remote
        if (bySource < 0) return local

        return if (preferRemote) remote else local
    }

    /**
     * 判断 [candidate] 是否应覆盖 [existing]。
     * existing == null 时恒为 true。
     */
    fun shouldApply(
        existing: SyncItem?,
        candidate: SyncItem,
        preferRemote: Boolean = true
    ): Boolean {
        if (existing == null) return true
        val winner = pick(existing, candidate, preferRemote)
        // 以协议字段判断 candidate 是否胜出（避免引用相等问题）
        return winner.updatedAt == candidate.updatedAt &&
            winner.deleted == candidate.deleted &&
            winner.content == candidate.content &&
            winner.source == candidate.source
    }

    /**
     * 按 id 合并本地与远端列表；同 id 走 LWW，结果按 id 排序。
     */
    fun mergeById(
        local: List<SyncItem>,
        remote: List<SyncItem>,
        preferRemote: Boolean = true
    ): List<SyncItem> {
        val map = LinkedHashMap<String, SyncItem>()
        local.forEach { map[it.id] = it }
        remote.forEach { remoteItem ->
            val existing = map[remoteItem.id]
            map[remoteItem.id] = if (existing == null) {
                remoteItem
            } else {
                pick(existing, remoteItem, preferRemote)
            }
        }
        return map.values.sortedBy { it.id }
    }
}
