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

import com.lanxin.android.builtin.sync.domain.SyncClient
import com.lanxin.android.builtin.sync.domain.SyncPullRequest
import com.lanxin.android.builtin.sync.domain.SyncPullResponse
import com.lanxin.android.builtin.sync.domain.SyncPushRequest
import com.lanxin.android.builtin.sync.domain.SyncPushResponse
import com.lanxin.android.data.network.NetworkClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Ktor 实现的 SyncClient。
 *
 * Endpoint：
 * - POST {baseUrl}/api/sync/pull
 * - POST {baseUrl}/api/sync/push
 */
@Singleton
class HttpSyncClient @Inject constructor(
    private val networkClient: NetworkClient,
    private val preferences: SyncPreferences
) : SyncClient {

    private val client get() = networkClient()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    override suspend fun pull(request: SyncPullRequest): Result<SyncPullResponse> =
        withContext(Dispatchers.IO) {
            execute(
                path = "/api/sync/pull",
                body = json.encodeToString(SyncPullRequest.serializer(), request)
            ) {
                json.decodeFromString(SyncPullResponse.serializer(), it)
            }
        }

    override suspend fun push(request: SyncPushRequest): Result<SyncPushResponse> =
        withContext(Dispatchers.IO) {
            execute(
                path = "/api/sync/push",
                body = json.encodeToString(SyncPushRequest.serializer(), request)
            ) {
                json.decodeFromString(SyncPushResponse.serializer(), it)
            }
        }

    private suspend fun <T> execute(
        path: String,
        body: String,
        parse: (String) -> T
    ): Result<T> {
        val config = preferences.getConfig()
        if (!config.isConfigured) {
            return Result.failure(IllegalStateException("同步未配置 baseUrl/token"))
        }
        val deviceId = preferences.getOrCreateDeviceId()
        val url = config.baseUrl.trimEnd('/') + path
        return try {
            val response = client.post(url) {
                header(HttpHeaders.Authorization, "Bearer ${config.token}")
                header("X-LanXin-Device-Id", deviceId)
                header("X-LanXin-Client", "android")
                setBody(body)
            }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                Result.failure(
                    IllegalStateException("HTTP ${response.status.value}: ${text.take(300)}")
                )
            } else {
                Result.success(parse(text))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
