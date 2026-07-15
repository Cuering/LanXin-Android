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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lanxin.android.builtin.sync.domain.SyncConfig
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 同步配置与水位（DataStore）。
 *
 * 键前缀 `sync_`，与 github_ / token 命名空间隔离。
 */
@Singleton
class SyncPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val deviceIdKey = stringPreferencesKey(KEY_DEVICE_ID)
    private val baseUrlKey = stringPreferencesKey(KEY_BASE_URL)
    private val tokenKey = stringPreferencesKey(KEY_TOKEN)
    private val userIdKey = stringPreferencesKey(KEY_USER_ID)
    private val enabledKey = booleanPreferencesKey(KEY_ENABLED)
    private val serverTimeKey = longPreferencesKey(KEY_SERVER_TIME)

    suspend fun getOrCreateDeviceId(): String {
        val existing = dataStore.data.map { it[deviceIdKey] }.first()
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        dataStore.edit { it[deviceIdKey] = created }
        return created
    }

    suspend fun getConfig(): SyncConfig {
        val prefs = dataStore.data.first()
        return SyncConfig(
            baseUrl = prefs[baseUrlKey].orEmpty(),
            token = prefs[tokenKey].orEmpty(),
            userId = prefs[userIdKey].orEmpty(),
            enabled = prefs[enabledKey] ?: false
        )
    }

    suspend fun setBaseUrl(url: String?) {
        dataStore.edit { prefs ->
            if (url.isNullOrBlank()) prefs.remove(baseUrlKey)
            else prefs[baseUrlKey] = url.trim().trimEnd('/')
        }
    }

    suspend fun setToken(token: String?) {
        dataStore.edit { prefs ->
            if (token.isNullOrBlank()) prefs.remove(tokenKey)
            else prefs[tokenKey] = token.trim()
        }
    }

    suspend fun setUserId(userId: String?) {
        dataStore.edit { prefs ->
            if (userId.isNullOrBlank()) prefs.remove(userIdKey)
            else prefs[userIdKey] = userId.trim()
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[enabledKey] = enabled }
    }

    suspend fun getLastServerTime(): Long =
        dataStore.data.map { it[serverTimeKey] ?: 0L }.first()

    suspend fun setLastServerTime(time: Long) {
        if (time <= 0L) return
        dataStore.edit { it[serverTimeKey] = time }
    }

    companion object {
        const val KEY_DEVICE_ID = "sync_device_id"
        const val KEY_BASE_URL = "sync_base_url"
        const val KEY_TOKEN = "sync_token"
        const val KEY_USER_ID = "sync_user_id"
        const val KEY_ENABLED = "sync_enabled"
        const val KEY_SERVER_TIME = "sync_last_server_time"
    }
}
