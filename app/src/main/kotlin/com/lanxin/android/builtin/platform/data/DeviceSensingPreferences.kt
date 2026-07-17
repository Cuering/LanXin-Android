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
import com.lanxin.android.builtin.platform.domain.DeviceSensingConfig
import com.lanxin.android.builtin.platform.domain.DeviceSensingSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 设备感知配置（DataStore）。
 *
 * 键前缀 `device_sensing_`，与 system_tools_ / web_search_ / local_inference_ 隔离。
 * **默认关**。
 */
@Singleton
class DeviceSensingPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : DeviceSensingSettings {

    private val enabledKey = booleanPreferencesKey(KEY_ENABLED)

    override suspend fun getConfig(): DeviceSensingConfig {
        val prefs = dataStore.data.first()
        return DeviceSensingConfig(
            enabled = prefs[enabledKey] ?: false
        )
    }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[enabledKey] = enabled }
    }

    companion object {
        const val KEY_ENABLED = "device_sensing_enabled"
    }
}
