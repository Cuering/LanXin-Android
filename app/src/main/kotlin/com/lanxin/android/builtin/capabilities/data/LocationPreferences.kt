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

package com.lanxin.android.builtin.capabilities.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.lanxin.android.builtin.capabilities.domain.LocationConfig
import com.lanxin.android.builtin.capabilities.domain.LocationSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 位置能力配置（DataStore）。
 *
 * 键前缀 `location_`。默认 **ON**；运行时权限按需申请，不后台定位。
 */
@Singleton
class LocationPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : LocationSettings {

    private val enabledKey = booleanPreferencesKey(KEY_ENABLED)

    override suspend fun getConfig(): LocationConfig {
        val prefs = dataStore.data.first()
        return LocationConfig(
            enabled = prefs[enabledKey] ?: LocationConfig.DEFAULT_ENABLED
        )
    }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit {
            it[enabledKey] = enabled
            it[booleanPreferencesKey(SmartCapabilitiesPreferences.KEY_LOCATION)] = enabled
        }
    }

    companion object {
        const val KEY_ENABLED = "location_enabled"
    }
}
