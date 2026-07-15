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
 * 与 AstrBot 同步端点通信的 HTTP 客户端抽象。
 *
 * 实现：[com.lanxin.android.builtin.sync.data.HttpSyncClient]（Ktor）。
 * 测试：可注入 fake。
 */
interface SyncClient {
    suspend fun pull(request: SyncPullRequest): Result<SyncPullResponse>
    suspend fun push(request: SyncPushRequest): Result<SyncPushResponse>
}
