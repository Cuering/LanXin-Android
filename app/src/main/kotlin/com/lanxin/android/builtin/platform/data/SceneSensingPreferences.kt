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
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lanxin.android.builtin.platform.domain.SceneSensingConfig
import com.lanxin.android.builtin.platform.domain.SceneSensingGate
import com.lanxin.android.builtin.platform.domain.SceneSensingSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 场景识别配置（DataStore）。
 *
 * 键前缀 `scene_sensing_`，与 device_sensing_ / web_search_ 隔离。
 * **默认关**；关闭时不持有相机会话。
 */
@Singleton
class SceneSensingPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SceneSensingSettings {

    private val enabledKey = booleanPreferencesKey(KEY_ENABLED)
    private val consentKey = booleanPreferencesKey(KEY_CONSENT)
    private val lastSceneKey = stringPreferencesKey(KEY_LAST_SCENE)
    private val lastStatusKey = stringPreferencesKey(KEY_LAST_STATUS)

    override suspend fun getConfig(): SceneSensingConfig {
        val prefs = dataStore.data.first()
        return SceneSensingConfig(
            enabled = prefs[enabledKey] ?: false,
            consentGranted = prefs[consentKey] ?: false,
            lastSceneId = prefs[lastSceneKey].orEmpty(),
            lastStatusText = prefs[lastStatusKey].orEmpty()
        )
    }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            val consent = prefs[consentKey] ?: false
            prefs[enabledKey] = SceneSensingGate.clampEnabled(enabled, consent)
        }
    }

    override suspend fun setConsentGranted(granted: Boolean) {
        dataStore.edit { prefs ->
            prefs[consentKey] = granted
            if (!granted) {
                // 撤回同意 → 强制关 + 清缓存
                prefs[enabledKey] = false
                prefs.remove(lastSceneKey)
                prefs.remove(lastStatusKey)
            }
        }
    }

    override suspend fun setLastScene(sceneId: String, statusText: String) {
        dataStore.edit { prefs ->
            prefs[lastSceneKey] = sceneId.trim()
            prefs[lastStatusKey] = statusText.trim()
        }
    }

    override suspend fun clearLastScene() {
        dataStore.edit { prefs ->
            prefs.remove(lastSceneKey)
            prefs.remove(lastStatusKey)
        }
    }

    companion object {
        const val KEY_ENABLED = "scene_sensing_enabled"
        const val KEY_CONSENT = "scene_sensing_consent"
        const val KEY_LAST_SCENE = "scene_sensing_last_scene"
        const val KEY_LAST_STATUS = "scene_sensing_last_status"
    }
}
