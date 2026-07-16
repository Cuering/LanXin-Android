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
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lanxin.android.builtin.voice.domain.TtsConfig
import com.lanxin.android.builtin.voice.domain.TtsSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * TTS 配置（DataStore）。
 *
 * 键：
 * - `tts_enabled`
 * - `tts_model_path`（兼容旧单文件）
 * - `tts_model_dir`（模型目录，一等公民）
 * - `tts_reference_audio`（参考音）
 * - `tts_voice_id`
 *
 * 大文件不入库；debug 下空路径可由 PetResourceResolver 旁路 meiju-ref。
 */
@Singleton
class TtsPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : TtsSettings {

    private val enabledKey = booleanPreferencesKey(KEY_ENABLED)
    private val modelPathKey = stringPreferencesKey(KEY_MODEL_PATH)
    private val modelDirKey = stringPreferencesKey(KEY_MODEL_DIR)
    private val referenceAudioKey = stringPreferencesKey(KEY_REFERENCE_AUDIO)
    private val voiceIdKey = stringPreferencesKey(KEY_VOICE_ID)

    override suspend fun getConfig(): TtsConfig {
        val prefs = dataStore.data.first()
        return TtsConfig(
            enabled = prefs[enabledKey] ?: false,
            modelPath = prefs[modelPathKey].orEmpty(),
            modelDir = prefs[modelDirKey].orEmpty(),
            referenceAudio = prefs[referenceAudioKey].orEmpty(),
            voiceId = prefs[voiceIdKey] ?: TtsConfig.DEFAULT_VOICE_ID
        )
    }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[enabledKey] = enabled }
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

    override suspend fun setModelDir(path: String?) {
        dataStore.edit { prefs ->
            if (path.isNullOrBlank()) {
                prefs.remove(modelDirKey)
            } else {
                prefs[modelDirKey] = path.trim()
            }
        }
    }

    override suspend fun setReferenceAudio(path: String?) {
        dataStore.edit { prefs ->
            if (path.isNullOrBlank()) {
                prefs.remove(referenceAudioKey)
            } else {
                prefs[referenceAudioKey] = path.trim()
            }
        }
    }

    override suspend fun setVoiceId(voiceId: String) {
        dataStore.edit { it[voiceIdKey] = voiceId.ifBlank { TtsConfig.DEFAULT_VOICE_ID } }
    }

    companion object {
        const val KEY_ENABLED = "tts_enabled"
        const val KEY_MODEL_PATH = "tts_model_path"
        const val KEY_MODEL_DIR = "tts_model_dir"
        const val KEY_REFERENCE_AUDIO = "tts_reference_audio"
        const val KEY_VOICE_ID = "tts_voice_id"
    }
}
