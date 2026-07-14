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

package com.lanxin.android.builtin.github.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * GitHub Token / 默认仓库配置，存 DataStore。
 *
 * 键前缀 `github_`，与全局 token 偏好隔离命名空间。
 */
@Singleton
class GithubConfig @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val tokenKey = stringPreferencesKey(KEY_TOKEN)
    private val ownerKey = stringPreferencesKey(KEY_OWNER)
    private val repoKey = stringPreferencesKey(KEY_REPO)
    private val baseUrlKey = stringPreferencesKey(KEY_BASE_URL)

    val tokenFlow: Flow<String?> = dataStore.data.map { it[tokenKey] }
    val ownerFlow: Flow<String?> = dataStore.data.map { it[ownerKey] }
    val repoFlow: Flow<String?> = dataStore.data.map { it[repoKey] }
    val baseUrlFlow: Flow<String> = dataStore.data.map {
        it[baseUrlKey] ?: DEFAULT_BASE_URL
    }

    suspend fun getToken(): String? = dataStore.data.map { it[tokenKey] }.first()

    suspend fun getOwner(): String? = dataStore.data.map { it[ownerKey] }.first()

    suspend fun getRepo(): String? = dataStore.data.map { it[repoKey] }.first()

    suspend fun getBaseUrl(): String =
        dataStore.data.map { it[baseUrlKey] ?: DEFAULT_BASE_URL }.first()

    suspend fun setToken(token: String?) {
        dataStore.edit { prefs ->
            if (token.isNullOrBlank()) {
                prefs.remove(tokenKey)
            } else {
                prefs[tokenKey] = token.trim()
            }
        }
    }

    suspend fun setOwner(owner: String?) {
        dataStore.edit { prefs ->
            if (owner.isNullOrBlank()) {
                prefs.remove(ownerKey)
            } else {
                prefs[ownerKey] = owner.trim()
            }
        }
    }

    suspend fun setRepo(repo: String?) {
        dataStore.edit { prefs ->
            if (repo.isNullOrBlank()) {
                prefs.remove(repoKey)
            } else {
                prefs[repoKey] = repo.trim()
            }
        }
    }

    suspend fun setBaseUrl(baseUrl: String?) {
        dataStore.edit { prefs ->
            if (baseUrl.isNullOrBlank()) {
                prefs.remove(baseUrlKey)
            } else {
                prefs[baseUrlKey] = baseUrl.trim().trimEnd('/')
            }
        }
    }

    /**
     * 解析 owner/repo：优先参数，其次 DataStore 默认值。
     * @throws IllegalStateException 两者皆空
     */
    suspend fun resolveRepo(owner: String?, repo: String?): Pair<String, String> {
        val o = owner?.trim()?.takeIf { it.isNotEmpty() } ?: getOwner()
        val r = repo?.trim()?.takeIf { it.isNotEmpty() } ?: getRepo()
        require(!o.isNullOrBlank()) { "owner 未配置：请传 owner 或先 gh_config_set" }
        require(!r.isNullOrBlank()) { "repo 未配置：请传 repo 或先 gh_config_set" }
        return o to r
    }

    suspend fun requireToken(): String {
        val token = getToken()?.trim().orEmpty()
        require(token.isNotEmpty()) {
            "GitHub Token 未配置：请先调用 gh_config_set 设置 token（需要 repo 权限）"
        }
        return token
    }

    /** 返回给 MCP 的脱敏配置快照。 */
    suspend fun snapshot(maskToken: Boolean = true): JsonObject {
        val token = getToken()
        val masked = when {
            token.isNullOrBlank() -> null
            !maskToken -> token
            token.length <= 8 -> "****"
            else -> token.take(4) + "****" + token.takeLast(4)
        }
        return buildJsonObject {
            put("ok", true)
            put("has_token", !token.isNullOrBlank())
            put("token_masked", masked)
            put("default_owner", getOwner())
            put("default_repo", getRepo())
            put("base_url", getBaseUrl())
        }
    }

    companion object {
        const val KEY_TOKEN = "github_token"
        const val KEY_OWNER = "github_default_owner"
        const val KEY_REPO = "github_default_repo"
        const val KEY_BASE_URL = "github_base_url"
        const val DEFAULT_BASE_URL = "https://api.github.com"
    }
}
