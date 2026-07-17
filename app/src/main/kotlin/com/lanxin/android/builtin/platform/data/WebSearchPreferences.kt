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

package com.lanxin.android.builtin.platform.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lanxin.android.builtin.platform.domain.WebSearchConfig
import com.lanxin.android.builtin.platform.domain.WebSearchSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 联网搜索配置（DataStore）。
 *
 * 键前缀 `web_search_`，与 system_tools_ / local_inference_ / offline_asr_ 隔离。
 * **默认关**。
 */
@Singleton
class WebSearchPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : WebSearchSettings {

    private val enabledKey = booleanPreferencesKey(KEY_ENABLED)
    private val limitKey = intPreferencesKey(KEY_DEFAULT_LIMIT)
    private val regionKey = stringPreferencesKey(KEY_REGION)

    override suspend fun getConfig(): WebSearchConfig {
        val prefs = dataStore.data.first()
        return WebSearchConfig(
            enabled = prefs[enabledKey] ?: false,
            defaultLimit = (prefs[limitKey] ?: WebSearchConfig.DEFAULT_LIMIT)
                .coerceIn(WebSearchConfig.MIN_LIMIT, WebSearchConfig.MAX_LIMIT),
            region = prefs[regionKey] ?: WebSearchConfig.DEFAULT_REGION
        )
    }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[enabledKey] = enabled }
    }

    override suspend fun setDefaultLimit(limit: Int) {
        val value = limit.coerceIn(WebSearchConfig.MIN_LIMIT, WebSearchConfig.MAX_LIMIT)
        dataStore.edit { it[limitKey] = value }
    }

    override suspend fun setRegion(region: String) {
        val value = region.trim().ifBlank { WebSearchConfig.DEFAULT_REGION }
        dataStore.edit { it[regionKey] = value }
    }

    companion object {
        const val KEY_ENABLED = "web_search_enabled"
        const val KEY_DEFAULT_LIMIT = "web_search_default_limit"
        const val KEY_REGION = "web_search_region"
    }
}
