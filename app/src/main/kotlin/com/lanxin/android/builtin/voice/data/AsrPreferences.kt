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

package com.lanxin.android.builtin.voice.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lanxin.android.builtin.capabilities.data.SmartCapabilitiesPreferences
import com.lanxin.android.builtin.voice.domain.AsrConfig
import com.lanxin.android.builtin.voice.domain.AsrSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 离线 ASR 配置（DataStore）。
 *
 * 键前缀 `offline_asr_`，与 local_inference_ / sync_ 命名空间隔离。
 * 模型文件不入库，只存路径。
 */
@Singleton
class AsrPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : AsrSettings {

    private val enabledKey = booleanPreferencesKey(KEY_ENABLED)
    private val modelPathKey = stringPreferencesKey(KEY_MODEL_PATH)
    private val languageKey = stringPreferencesKey(KEY_LANGUAGE)
    private val sampleRateKey = intPreferencesKey(KEY_SAMPLE_RATE)

    override suspend fun getConfig(): AsrConfig {
        val prefs = dataStore.data.first()
        return AsrConfig(
            enabled = prefs[enabledKey] ?: false,
            modelPath = prefs[modelPathKey].orEmpty(),
            language = prefs[languageKey] ?: AsrConfig.DEFAULT_LANGUAGE,
            sampleRateHz = (prefs[sampleRateKey] ?: AsrConfig.DEFAULT_SAMPLE_RATE_HZ)
                .coerceIn(AsrConfig.MIN_SAMPLE_RATE_HZ, AsrConfig.MAX_SAMPLE_RATE_HZ)
        )
    }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit {
            it[enabledKey] = enabled
            it[booleanPreferencesKey(SmartCapabilitiesPreferences.KEY_VOICE)] = enabled
        }
    }

    override suspend fun setModelPath(path: String?) {
        dataStore.edit { prefs ->
            if (path.isNullOrBlank()) {
                prefs.remove(modelPathKey)
            } else {
                prefs[modelPathKey] = path.trim()
            }
        }
    }

    override suspend fun setLanguage(language: String) {
        val value = language.trim().ifBlank { AsrConfig.DEFAULT_LANGUAGE }
        dataStore.edit { it[languageKey] = value }
    }

    override suspend fun setSampleRateHz(sampleRateHz: Int) {
        val value = sampleRateHz.coerceIn(
            AsrConfig.MIN_SAMPLE_RATE_HZ,
            AsrConfig.MAX_SAMPLE_RATE_HZ
        )
        dataStore.edit { it[sampleRateKey] = value }
    }

    companion object {
        const val KEY_ENABLED = "offline_asr_enabled"
        const val KEY_MODEL_PATH = "offline_asr_model_path"
        const val KEY_LANGUAGE = "offline_asr_language"
        const val KEY_SAMPLE_RATE = "offline_asr_sample_rate_hz"
    }
}
