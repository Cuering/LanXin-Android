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

package com.lanxin.android.plugin.claw.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.lanxin.android.plugin.claw.domain.ClawHostConfig
import com.lanxin.android.plugin.claw.domain.ClawHostSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Claw 宿主配置（DataStore）。
 *
 * 键前缀 `claw_host_`，与 web_search_ / device_sensing_ / system_tools_ 隔离。
 * **默认关**。
 */
@Singleton
class ClawHostPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ClawHostSettings {

    private val enabledKey = booleanPreferencesKey(KEY_ENABLED)
    private val residentKey = booleanPreferencesKey(KEY_RESIDENT_REQUESTED)

    override suspend fun getConfig(): ClawHostConfig {
        val prefs = dataStore.data.first()
        return ClawHostConfig(
            enabled = prefs[enabledKey] ?: false,
            residentRequested = prefs[residentKey] ?: false
        )
    }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[enabledKey] = enabled }
    }

    override suspend fun setResidentRequested(requested: Boolean) {
        dataStore.edit { it[residentKey] = requested }
    }

    companion object {
        const val KEY_ENABLED = "claw_host_enabled"
        const val KEY_RESIDENT_REQUESTED = "claw_host_resident_requested"
    }
}
