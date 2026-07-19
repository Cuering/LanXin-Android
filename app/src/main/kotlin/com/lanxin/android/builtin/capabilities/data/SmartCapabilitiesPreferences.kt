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
import com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesConfig
import com.lanxin.android.builtin.capabilities.domain.SmartCapabilityId
import com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesMigration
import com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesSettings
import com.lanxin.android.builtin.localinference.data.LocalInferencePreferences
import com.lanxin.android.builtin.platform.data.DeviceSensingPreferences
import com.lanxin.android.builtin.platform.data.SceneSensingPreferences
import com.lanxin.android.builtin.platform.data.WebSearchPreferences
import com.lanxin.android.builtin.platform.domain.SceneSensingGate
import com.lanxin.android.builtin.systemtools.data.SystemToolsPreferences
import com.lanxin.android.builtin.voice.data.AsrPreferences
import com.lanxin.android.builtin.voice.data.TtsPreferences
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first

/**
 * 智能能力总配置（DataStore）。
 *
 * 键前缀 `smart_capabilities_`。
 * 首次 [getConfig]/[ensureMigrated] 时从旧模块键迁移：
 * - 从未配置 → 新默认（语音/搜索/系统工具/设备感知 ON）
 * - 用户曾显式关 → 保留
 * - 本地脑/场景不抬 ON
 *
 * 子开关写入时同步回写旧模块键，保证细页与 Gate 一致。
 */
@Singleton
class SmartCapabilitiesPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SmartCapabilitiesSettings {

    private val masterKey = booleanPreferencesKey(KEY_MASTER)
    private val localKey = booleanPreferencesKey(KEY_LOCAL_INFERENCE)
    private val voiceKey = booleanPreferencesKey(KEY_VOICE)
    private val systemToolsKey = booleanPreferencesKey(KEY_SYSTEM_TOOLS)
    private val webSearchKey = booleanPreferencesKey(KEY_WEB_SEARCH)
    private val deviceSensingKey = booleanPreferencesKey(KEY_DEVICE_SENSING)
    private val locationKey = booleanPreferencesKey(KEY_LOCATION)
    private val navigateKey = booleanPreferencesKey(KEY_NAVIGATE)
    private val guideKey = booleanPreferencesKey(KEY_GUIDE)
    private val sceneKey = booleanPreferencesKey(KEY_SCENE_VISION)
    private val migratedKey = booleanPreferencesKey(KEY_MIGRATED_V1)

    private val migrateMutex = Mutex()
    private val migratedOnce = AtomicBoolean(false)

    override suspend fun ensureMigrated() {
        if (migratedOnce.get()) {
            val prefs = dataStore.data.first()
            if (prefs[migratedKey] == true) return
        }
        migrateMutex.withLock {
            val prefs = dataStore.data.first()
            if (prefs[migratedKey] == true) {
                migratedOnce.set(true)
                return
            }
            val legacy = SmartCapabilitiesMigration.LegacyCapabilitySnapshot(
                webSearch = prefs[booleanPreferencesKey(WebSearchPreferences.KEY_ENABLED)],
                deviceSensing = prefs[booleanPreferencesKey(DeviceSensingPreferences.KEY_ENABLED)],
                systemToolsMaster = prefs[booleanPreferencesKey(SystemToolsPreferences.KEY_MASTER)],
                voiceAsr = prefs[booleanPreferencesKey(AsrPreferences.KEY_ENABLED)],
                voiceTts = prefs[booleanPreferencesKey(TtsPreferences.KEY_ENABLED)],
                localInference = prefs[booleanPreferencesKey(LocalInferencePreferences.KEY_ENABLED)],
                sceneVision = prefs[booleanPreferencesKey(SceneSensingPreferences.KEY_ENABLED)]
            )
            val resolved = SmartCapabilitiesMigration.buildConfig(legacy)
            dataStore.edit { e ->
                e[masterKey] = resolved.masterEnabled
                e[localKey] = resolved.localInferenceEnabled
                e[voiceKey] = resolved.voiceEnabled
                e[systemToolsKey] = resolved.systemToolsEnabled
                e[webSearchKey] = resolved.webSearchEnabled
                e[deviceSensingKey] = resolved.deviceSensingEnabled
                e[locationKey] = resolved.locationEnabled
                e[navigateKey] = resolved.navigateEnabled
                e[guideKey] = resolved.guideEnabled
                e[sceneKey] = resolved.sceneVisionEnabled
                e[migratedKey] = true
                // 回写旧模块键：从未配置的抬到新默认；显式值已等于 resolved
                e[booleanPreferencesKey(WebSearchPreferences.KEY_ENABLED)] = resolved.webSearchEnabled
                e[booleanPreferencesKey(DeviceSensingPreferences.KEY_ENABLED)] =
                    resolved.deviceSensingEnabled
                e[booleanPreferencesKey(SystemToolsPreferences.KEY_MASTER)] =
                    resolved.systemToolsEnabled
                // 系统工具子项：若 master 被抬 ON 且子项从未写，一并抬 ON（写仍确认）
                if (resolved.systemToolsEnabled) {
                    val cal = booleanPreferencesKey(SystemToolsPreferences.KEY_CALENDAR)
                    val alarm = booleanPreferencesKey(SystemToolsPreferences.KEY_ALARM)
                    val notes = booleanPreferencesKey(SystemToolsPreferences.KEY_NOTES)
                    val userFile = booleanPreferencesKey(SystemToolsPreferences.KEY_USER_FILE)
                    if (prefs[cal] == null) e[cal] = true
                    if (prefs[alarm] == null) e[alarm] = true
                    if (prefs[notes] == null) e[notes] = true
                    if (prefs[userFile] == null) e[userFile] = true
                }
                e[booleanPreferencesKey(AsrPreferences.KEY_ENABLED)] = resolved.voiceEnabled
                e[booleanPreferencesKey(TtsPreferences.KEY_ENABLED)] = resolved.voiceEnabled
                // 本地脑 / 场景：仅当 resolved 为 true 才写 true；否则不强制写 false 覆盖用户后续
                // 但为一致性：若 legacy null 保持 false 写入
                e[booleanPreferencesKey(LocalInferencePreferences.KEY_ENABLED)] =
                    resolved.localInferenceEnabled
                e[booleanPreferencesKey(SceneSensingPreferences.KEY_ENABLED)] =
                    resolved.sceneVisionEnabled
            }
            migratedOnce.set(true)
        }
    }

    override suspend fun getConfig(): SmartCapabilitiesConfig {
        ensureMigrated()
        val prefs = dataStore.data.first()
        return SmartCapabilitiesConfig(
            masterEnabled = prefs[masterKey] ?: SmartCapabilitiesConfig.DEFAULT_MASTER,
            localInferenceEnabled = prefs[localKey]
                ?: SmartCapabilitiesConfig.DEFAULT_LOCAL_INFERENCE,
            voiceEnabled = prefs[voiceKey] ?: SmartCapabilitiesConfig.DEFAULT_VOICE,
            systemToolsEnabled = prefs[systemToolsKey]
                ?: SmartCapabilitiesConfig.DEFAULT_SYSTEM_TOOLS,
            webSearchEnabled = prefs[webSearchKey]
                ?: SmartCapabilitiesConfig.DEFAULT_WEB_SEARCH,
            deviceSensingEnabled = prefs[deviceSensingKey]
                ?: SmartCapabilitiesConfig.DEFAULT_DEVICE_SENSING,
            locationEnabled = prefs[locationKey] ?: SmartCapabilitiesConfig.DEFAULT_LOCATION,
            navigateEnabled = prefs[navigateKey] ?: SmartCapabilitiesConfig.DEFAULT_NAVIGATE,
            guideEnabled = prefs[guideKey] ?: SmartCapabilitiesConfig.DEFAULT_GUIDE,
            sceneVisionEnabled = prefs[sceneKey] ?: SmartCapabilitiesConfig.DEFAULT_SCENE_VISION,
            migratedV1 = prefs[migratedKey] ?: false
        )
    }

    override suspend fun setMasterEnabled(enabled: Boolean) {
        ensureMigrated()
        dataStore.edit { it[masterKey] = enabled }
    }

    override suspend fun setChildEnabled(id: SmartCapabilityId, enabled: Boolean) {
        ensureMigrated()
        dataStore.edit { e ->
            when (id) {
                SmartCapabilityId.LOCAL_INFERENCE -> {
                    e[localKey] = enabled
                    e[booleanPreferencesKey(LocalInferencePreferences.KEY_ENABLED)] = enabled
                }
                SmartCapabilityId.VOICE -> {
                    e[voiceKey] = enabled
                    e[booleanPreferencesKey(AsrPreferences.KEY_ENABLED)] = enabled
                    e[booleanPreferencesKey(TtsPreferences.KEY_ENABLED)] = enabled
                }
                SmartCapabilityId.SYSTEM_TOOLS -> {
                    e[systemToolsKey] = enabled
                    e[booleanPreferencesKey(SystemToolsPreferences.KEY_MASTER)] = enabled
                }
                SmartCapabilityId.WEB_SEARCH -> {
                    e[webSearchKey] = enabled
                    e[booleanPreferencesKey(WebSearchPreferences.KEY_ENABLED)] = enabled
                }
                SmartCapabilityId.DEVICE_SENSING -> {
                    e[deviceSensingKey] = enabled
                    e[booleanPreferencesKey(DeviceSensingPreferences.KEY_ENABLED)] = enabled
                }
                SmartCapabilityId.LOCATION -> {
                    e[locationKey] = enabled
                    e[booleanPreferencesKey(LocationPreferences.KEY_ENABLED)] = enabled
                }
                SmartCapabilityId.NAVIGATE -> {
                    e[navigateKey] = enabled
                }
                SmartCapabilityId.GUIDE -> {
                    e[guideKey] = enabled
                }
                SmartCapabilityId.SCENE_VISION -> {
                    // 与 SceneSensingPreferences.setEnabled 一致：无 consent 不可写 true
                    val consent = e[
                        booleanPreferencesKey(SceneSensingPreferences.KEY_CONSENT)
                    ] ?: false
                    val clamped = SceneSensingGate.clampEnabled(enabled, consent)
                    e[sceneKey] = clamped
                    e[booleanPreferencesKey(SceneSensingPreferences.KEY_ENABLED)] = clamped
                }
            }
        }
    }

    companion object {
        const val KEY_MASTER = "smart_capabilities_master"
        const val KEY_LOCAL_INFERENCE = "smart_capabilities_local_inference"
        const val KEY_VOICE = "smart_capabilities_voice"
        const val KEY_SYSTEM_TOOLS = "smart_capabilities_system_tools"
        const val KEY_WEB_SEARCH = "smart_capabilities_web_search"
        const val KEY_DEVICE_SENSING = "smart_capabilities_device_sensing"
        const val KEY_LOCATION = "smart_capabilities_location"
        const val KEY_NAVIGATE = "smart_capabilities_navigate"
        const val KEY_GUIDE = "smart_capabilities_guide"
        const val KEY_SCENE_VISION = "smart_capabilities_scene_vision"
        const val KEY_MIGRATED_V1 = "smart_capabilities_migrated_v1"
    }
}
