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

/**
 * 同步引擎对外门面：outbox + pull/push 编排。
 *
 * 不污染会话；仅通过独立 HTTP 与 AstrBot 交互。
 */
interface SyncRepository {
    /** 将本地变更写入 outbox（尚未上传）。 */
    suspend fun enqueue(item: SyncItem, op: SyncOutboxOp = SyncOutboxOp.UPSERT)

    /** 查看当前 outbox（调试 / 单测）。 */
    suspend fun peekOutbox(): List<SyncOutboxEntry>

    /** 执行一轮 push → pull → merge。 */
    suspend fun syncOnce(types: List<String> = listOf(SyncItemType.MEMORY)): SyncCycleResult

    /** 上次成功同步的 server_time 水位。 */
    suspend fun lastServerTime(): Long

    /** 设备 ID。 */
    suspend fun deviceId(): String

    /** Memory 本地 upsert 后入队（不触发网络）。 */
    suspend fun enqueueMemoryUpsert(entity: MemoryEntity)

    /** Memory 本地删除后入队 tombstone。 */
    suspend fun enqueueMemoryDelete(localId: Long, contentSnapshot: String = "")
}
