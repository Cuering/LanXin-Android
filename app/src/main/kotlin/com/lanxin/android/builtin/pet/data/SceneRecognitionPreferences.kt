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

package com.lanxin.android.builtin.pet.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.lanxin.android.builtin.pet.domain.SceneRecognitionConfig
import com.lanxin.android.builtin.pet.domain.SceneRecognitionSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 场景识别配置（DataStore）。
 *
 * 键前缀 `scene_recognition_`，与 desktop_pet_ / device_sensing_ 隔离。
 * **默认关**；**默认未确认**。
 */
@Singleton
class SceneRecognitionPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SceneRecognitionSettings {

    private val enabledKey = booleanPreferencesKey(KEY_ENABLED)
    private val consentKey = booleanPreferencesKey(KEY_CONSENT)

    override suspend fun getConfig(): SceneRecognitionConfig {
        val prefs = dataStore.data.first()
        return SceneRecognitionConfig(
            enabled = prefs[enabledKey] ?: false,
            consentGranted = prefs[consentKey] ?: false
        )
    }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[enabledKey] = enabled }
    }

    override suspend fun setConsentGranted(granted: Boolean) {
        dataStore.edit { it[consentKey] = granted }
    }

    override suspend fun update(config: SceneRecognitionConfig) {
        dataStore.edit {
            it[enabledKey] = config.enabled
            it[consentKey] = config.consentGranted
        }
    }

    companion object {
        const val KEY_ENABLED = "scene_recognition_enabled"
        const val KEY_CONSENT = "scene_recognition_consent"
    }
}
