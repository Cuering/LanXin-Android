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

package com.lanxin.android.builtin.localinference.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lanxin.android.builtin.capabilities.data.SmartCapabilitiesPreferences
import com.lanxin.android.builtin.localinference.domain.LocalInferenceConfig
import com.lanxin.android.builtin.localinference.domain.LocalInferenceSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 本地推理配置（DataStore）。
 *
 * 键前缀 `local_inference_`，与 sync_ / 市场命名空间隔离。
 * 模型文件不入库，只存路径。
 */
@Singleton
class LocalInferencePreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : LocalInferenceSettings {

    private val enabledKey = booleanPreferencesKey(KEY_ENABLED)
    private val modelPathKey = stringPreferencesKey(KEY_MODEL_PATH)
    private val maxTokensKey = intPreferencesKey(KEY_MAX_TOKENS)
    private val temperatureKey = floatPreferencesKey(KEY_TEMPERATURE)
    private val showThinkingKey = booleanPreferencesKey(KEY_SHOW_THINKING)
    private val preferLocalKey = booleanPreferencesKey(KEY_PREFER_LOCAL)
    private val contextWindowKey = intPreferencesKey(KEY_CONTEXT_WINDOW)

    override suspend fun getConfig(): LocalInferenceConfig {
        val prefs = dataStore.data.first()
        return LocalInferenceConfig(
            enabled = prefs[enabledKey] ?: false,
            modelPath = prefs[modelPathKey].orEmpty(),
            maxTokens = (prefs[maxTokensKey] ?: LocalInferenceConfig.DEFAULT_MAX_TOKENS)
                .coerceIn(LocalInferenceConfig.MIN_MAX_TOKENS, LocalInferenceConfig.MAX_MAX_TOKENS),
            temperature = prefs[temperatureKey] ?: LocalInferenceConfig.DEFAULT_TEMPERATURE,
            showThinking = prefs[showThinkingKey] ?: false,
            contextWindowTokens = (prefs[contextWindowKey]
                ?: LocalInferenceConfig.DEFAULT_CONTEXT_WINDOW_TOKENS)
                .coerceIn(
                    LocalInferenceConfig.MIN_CONTEXT_WINDOW_TOKENS,
                    LocalInferenceConfig.MAX_CONTEXT_WINDOW_TOKENS
                )
        )
    }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit {
            it[enabledKey] = enabled
            it[booleanPreferencesKey(SmartCapabilitiesPreferences.KEY_LOCAL_INFERENCE)] = enabled
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

    override suspend fun setMaxTokens(maxTokens: Int) {
        val value = maxTokens.coerceIn(
            LocalInferenceConfig.MIN_MAX_TOKENS,
            LocalInferenceConfig.MAX_MAX_TOKENS
        )
        dataStore.edit { it[maxTokensKey] = value }
    }

    override suspend fun setTemperature(temperature: Float) {
        dataStore.edit { it[temperatureKey] = temperature }
    }

    override suspend fun setContextWindowTokens(tokens: Int) {
        val value = tokens.coerceIn(
            LocalInferenceConfig.MIN_CONTEXT_WINDOW_TOKENS,
            LocalInferenceConfig.MAX_CONTEXT_WINDOW_TOKENS
        )
        dataStore.edit { it[contextWindowKey] = value }
    }

    override suspend fun setShowThinking(show: Boolean) {
        dataStore.edit { it[showThinkingKey] = show }
    }

    override suspend fun isPreferLocal(): Boolean =
        dataStore.data.map { it[preferLocalKey] ?: false }.first()

    override suspend fun setPreferLocal(prefer: Boolean) {
        dataStore.edit { it[preferLocalKey] = prefer }
    }

    companion object {
        const val KEY_ENABLED = "local_inference_enabled"
        const val KEY_MODEL_PATH = "local_inference_model_path"
        const val KEY_MAX_TOKENS = "local_inference_max_tokens"
        const val KEY_TEMPERATURE = "local_inference_temperature"
        const val KEY_SHOW_THINKING = "local_inference_show_thinking"
        const val KEY_PREFER_LOCAL = "local_inference_prefer_local"
        const val KEY_CONTEXT_WINDOW = "local_inference_context_window"
    }
}
