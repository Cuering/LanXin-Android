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
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lanxin.android.builtin.pet.domain.OverlayPosition
import com.lanxin.android.builtin.pet.domain.PetConfig
import com.lanxin.android.builtin.pet.domain.PetSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 桌宠配置（DataStore）。
 *
 * 键：
 * - `desktop_pet_*` 开关类
 * - `live2d_model_path` Live2D 模型路径（一等公民；换模型不改状态机）
 * - `desktop_pet_overlay_x/y` 悬浮窗位置（拖拽记忆）
 *
 * 默认全关。路径空时由 [com.lanxin.android.builtin.pet.domain.PetResourceResolver]
 * 在 **debug** 下尝试 meiju-ref 旁路。
 */
@Singleton
class PetPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : PetSettings {

    private val enabledKey = booleanPreferencesKey(KEY_ENABLED)
    private val overlayKey = booleanPreferencesKey(KEY_OVERLAY_RUNNING)
    private val autoListenKey = booleanPreferencesKey(KEY_AUTO_LISTEN)
    private val live2dPathKey = stringPreferencesKey(KEY_LIVE2D_MODEL_PATH)
    private val musicBeatSwayKey = booleanPreferencesKey(KEY_MUSIC_BEAT_SWAY)
    private val overlayXKey = intPreferencesKey(KEY_OVERLAY_X)
    private val overlayYKey = intPreferencesKey(KEY_OVERLAY_Y)

    override suspend fun getConfig(): PetConfig {
        val prefs = dataStore.data.first()
        return PetConfig(
            enabled = prefs[enabledKey] ?: false,
            overlayRunning = prefs[overlayKey] ?: false,
            autoListen = prefs[autoListenKey] ?: false,
            live2dModelPath = prefs[live2dPathKey].orEmpty(),
            musicBeatSway = prefs[musicBeatSwayKey] ?: false,
            overlayPosition = OverlayPosition(
                x = prefs[overlayXKey] ?: OverlayPosition.UNSET,
                y = prefs[overlayYKey] ?: OverlayPosition.UNSET
            )
        )
    }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[enabledKey] = enabled }
    }

    override suspend fun setOverlayRunning(running: Boolean) {
        dataStore.edit { it[overlayKey] = running }
    }

    override suspend fun setAutoListen(autoListen: Boolean) {
        dataStore.edit { it[autoListenKey] = autoListen }
    }

    override suspend fun setLive2dModelPath(path: String?) {
        dataStore.edit { prefs ->
            if (path.isNullOrBlank()) {
                prefs.remove(live2dPathKey)
            } else {
                prefs[live2dPathKey] = path.trim()
            }
        }
    }

    override suspend fun setMusicBeatSway(enabled: Boolean) {
        dataStore.edit { it[musicBeatSwayKey] = enabled }
    }

    override suspend fun setOverlayPosition(x: Int, y: Int) {
        dataStore.edit {
            it[overlayXKey] = x
            it[overlayYKey] = y
        }
    }

    companion object {
        const val KEY_ENABLED = "desktop_pet_enabled"
        const val KEY_OVERLAY_RUNNING = "desktop_pet_overlay_running"
        const val KEY_AUTO_LISTEN = "desktop_pet_auto_listen"

        /** 与产品决策对齐的公开键名。 */
        const val KEY_LIVE2D_MODEL_PATH = "live2d_model_path"

        /** 跟随音乐节拍晃动。 */
        const val KEY_MUSIC_BEAT_SWAY = "desktop_pet_music_beat_sway"

        const val KEY_OVERLAY_X = "desktop_pet_overlay_x"
        const val KEY_OVERLAY_Y = "desktop_pet_overlay_y"
    }
}
