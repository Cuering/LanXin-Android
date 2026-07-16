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
 * LWW 决策结果（Phase 5.2）。
 *
 * - [APPLY_NEW]：无本地 existing，直接采纳 candidate
 * - [APPLY]：candidate 按协议胜出，应覆盖 existing
 * - [SKIP]：existing 胜出（或与 candidate 全等且 preferRemote=false），不写库
 */
enum class LwwDecision {
    APPLY_NEW,
    APPLY,
    SKIP
}

/**
 * LWW（Last-Write-Wins）冲突解析 — 协议 v1 完整实现（Phase 5.2）。
 *
 * 比较顺序见 `docs/sync-protocol.md` §4：
 * 1. updated_at 较大者胜
 * 2. 相等时 deleted=true 优先（防复活）
 * 3. 仍相等时 source 字典序较大者胜
 * 4. 仍相等时由 [preferRemote] 决定
 *
 * push 的 applied 回写与 pull merge 必须共用本入口，保证对称。
 */
object LwwResolver {

    /**
     * 协议级比较：返回 >0 表示 [a] 胜于 [b]；<0 表示 [b] 胜；0 表示 LWW 字段全等。
     * 不包含 preferRemote（全等时返回 0）。
     */
    fun compare(a: SyncItem, b: SyncItem): Int {
        val byTime = a.updatedAt.compareTo(b.updatedAt)
        if (byTime != 0) return byTime

        // deleted=true 优先（防复活）
        if (a.deleted != b.deleted) {
            return if (a.deleted) 1 else -1
        }

        val bySource = a.source.compareTo(b.source)
        if (bySource != 0) return bySource

        return 0
    }

    /**
     * 在两条同 id 条目中选出胜者。
     * 调用方应保证 [local].id == [remote].id（若 id 不同，行为未定义，返回 [remote]）。
     *
     * 返回值恒为 [local] 或 [remote] 的引用之一，便于 [shouldApply] / [decide] 用 === 判定。
     */
    fun pick(
        local: SyncItem,
        remote: SyncItem,
        preferRemote: Boolean = true
    ): SyncItem {
        if (local.id != remote.id) return remote

        val cmp = compare(remote, local)
        if (cmp > 0) return remote
        if (cmp < 0) return local

        return if (preferRemote) remote else local
    }

    /**
     * 判断 [candidate] 是否应覆盖 [existing]。
     * existing == null 时恒为 true。
     *
     * 使用引用相等：pick 始终返回入参引用，避免 content 等非协议字段误判。
     */
    fun shouldApply(
        existing: SyncItem?,
        candidate: SyncItem,
        preferRemote: Boolean = true
    ): Boolean {
        if (existing == null) return true
        return pick(existing, candidate, preferRemote) === candidate
    }

    /**
     * 结构化决策，供 merge 路径计数（merged / skipped / conflictResolved）。
     */
    fun decide(
        existing: SyncItem?,
        candidate: SyncItem,
        preferRemote: Boolean = true
    ): LwwDecision {
        if (existing == null) return LwwDecision.APPLY_NEW
        return if (pick(existing, candidate, preferRemote) === candidate) {
            LwwDecision.APPLY
        } else {
            LwwDecision.SKIP
        }
    }

    /**
     * 是否视为「冲突已解决」（双方均存在且 candidate 胜出且协议字段不完全相同）。
     * 用于 SyncCycleResult.conflictResolved 轻量观测。
     */
    fun isConflictResolution(
        existing: SyncItem?,
        candidate: SyncItem,
        preferRemote: Boolean = true
    ): Boolean {
        if (existing == null) return false
        if (!shouldApply(existing, candidate, preferRemote)) return false
        // 协议字段已有差异才算冲突；全等 + preferRemote 仅算对齐回写
        return compare(existing, candidate) != 0 ||
            existing.content != candidate.content ||
            existing.deleted != candidate.deleted
    }

    /**
     * 按 id 合并本地与远端列表；同 id 走 LWW，结果按 id 排序。
     * memory / knowledge 均可在列表层使用（knowledge 存储适配可后置）。
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
