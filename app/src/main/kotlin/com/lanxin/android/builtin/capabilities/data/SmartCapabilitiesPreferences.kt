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
import androidx.datastore.preferences.core.MutablePreferences
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
 * - v1：从旧模块键抬默认 / 保留显式关
 * - v2：折叠为助手工具 / 位置与周边聚合键；任一项曾关 → 组关
 * - 导航 / 导游：独立键，默认 OFF，不参与 v2 聚合
 *
 * 聚合开关写入时同步回写旧模块键，保证细页与 Gate 一致。
 */
@Singleton
class SmartCapabilitiesPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SmartCapabilitiesSettings {

    private val masterKey = booleanPreferencesKey(KEY_MASTER)
    private val localKey = booleanPreferencesKey(KEY_LOCAL_INFERENCE)
    private val voiceKey = booleanPreferencesKey(KEY_VOICE)
    private val assistantToolsKey = booleanPreferencesKey(KEY_ASSISTANT_TOOLS)
    private val locationAroundKey = booleanPreferencesKey(KEY_LOCATION_AROUND)
    private val navigateKey = booleanPreferencesKey(KEY_NAVIGATE)
    private val guideKey = booleanPreferencesKey(KEY_GUIDE)
    private val sceneKey = booleanPreferencesKey(KEY_SCENE_VISION)
    private val migratedV1Key = booleanPreferencesKey(KEY_MIGRATED_V1)
    private val migratedV2Key = booleanPreferencesKey(KEY_MIGRATED_V2)

    // v1 细项键：迁移读源；v2 后与聚合键保持同步
    private val systemToolsKey = booleanPreferencesKey(KEY_SYSTEM_TOOLS)
    private val webSearchKey = booleanPreferencesKey(KEY_WEB_SEARCH)
    private val deviceSensingKey = booleanPreferencesKey(KEY_DEVICE_SENSING)
    private val locationKey = booleanPreferencesKey(KEY_LOCATION)

    private val migrateMutex = Mutex()
    private val migratedOnce = AtomicBoolean(false)

    override suspend fun ensureMigrated() {
        if (migratedOnce.get()) {
            val prefs = dataStore.data.first()
            if (prefs[migratedV1Key] == true && prefs[migratedV2Key] == true) return
        }
        migrateMutex.withLock {
            val prefs = dataStore.data.first()
            val hasV1 = prefs[migratedV1Key] == true
            val hasV2 = prefs[migratedV2Key] == true
            if (hasV1 && hasV2) {
                migratedOnce.set(true)
                return
            }
            if (!hasV1) {
                runV1Migration(prefs)
            }
            // v1 后可能刚写入，重读
            val afterV1 = dataStore.data.first()
            if (afterV1[migratedV2Key] != true) {
                runV2Migration(afterV1)
            }
            migratedOnce.set(true)
        }
    }

    private suspend fun runV1Migration(prefs: Preferences) {
        val legacy = SmartCapabilitiesMigration.LegacyCapabilitySnapshot(
            webSearch = prefs[booleanPreferencesKey(WebSearchPreferences.KEY_ENABLED)],
            deviceSensing = prefs[booleanPreferencesKey(DeviceSensingPreferences.KEY_ENABLED)],
            systemToolsMaster = prefs[booleanPreferencesKey(SystemToolsPreferences.KEY_MASTER)],
            voiceAsr = prefs[booleanPreferencesKey(AsrPreferences.KEY_ENABLED)],
            voiceTts = prefs[booleanPreferencesKey(TtsPreferences.KEY_ENABLED)],
            localInference = prefs[booleanPreferencesKey(LocalInferencePreferences.KEY_ENABLED)],
            sceneVision = prefs[booleanPreferencesKey(SceneSensingPreferences.KEY_ENABLED)],
            location = prefs[booleanPreferencesKey(LocationPreferences.KEY_ENABLED)]
        )
        val resolved = SmartCapabilitiesMigration.buildConfig(legacy)
        dataStore.edit { e ->
            writeResolved(e, prefs, resolved)
        }
    }

    private suspend fun runV2Migration(prefs: Preferences) {
        val resolved = SmartCapabilitiesMigration.collapseToV2(
            masterEnabled = prefs[masterKey] ?: SmartCapabilitiesConfig.DEFAULT_MASTER,
            localInferenceEnabled = prefs[localKey]
                ?: SmartCapabilitiesConfig.DEFAULT_LOCAL_INFERENCE,
            voiceEnabled = prefs[voiceKey] ?: SmartCapabilitiesConfig.DEFAULT_VOICE,
            systemTools = prefs[systemToolsKey]
                ?: prefs[booleanPreferencesKey(SystemToolsPreferences.KEY_MASTER)],
            webSearch = prefs[webSearchKey]
                ?: prefs[booleanPreferencesKey(WebSearchPreferences.KEY_ENABLED)],
            deviceSensing = prefs[deviceSensingKey]
                ?: prefs[booleanPreferencesKey(DeviceSensingPreferences.KEY_ENABLED)],
            location = prefs[locationKey]
                ?: prefs[booleanPreferencesKey(LocationPreferences.KEY_ENABLED)],
            sceneVisionEnabled = prefs[sceneKey]
                ?: SmartCapabilitiesConfig.DEFAULT_SCENE_VISION,
            existingAssistantTools = prefs[assistantToolsKey],
            existingLocationAround = prefs[locationAroundKey],
            navigateEnabled = prefs[navigateKey] ?: SmartCapabilitiesConfig.DEFAULT_NAVIGATE,
            guideEnabled = prefs[guideKey] ?: SmartCapabilitiesConfig.DEFAULT_GUIDE
        )
        dataStore.edit { e ->
            writeResolved(e, prefs, resolved)
        }
    }

    private fun writeResolved(
        e: MutablePreferences,
        prefs: Preferences,
        resolved: SmartCapabilitiesConfig
    ) {
        e[masterKey] = resolved.masterEnabled
        e[localKey] = resolved.localInferenceEnabled
        e[voiceKey] = resolved.voiceEnabled
        e[assistantToolsKey] = resolved.assistantToolsEnabled
        e[locationAroundKey] = resolved.locationAroundEnabled
        e[navigateKey] = resolved.navigateEnabled
        e[guideKey] = resolved.guideEnabled
        e[sceneKey] = resolved.sceneVisionEnabled
        e[migratedV1Key] = true
        e[migratedV2Key] = true

        // 细项键与旧模块键与聚合同步
        e[systemToolsKey] = resolved.assistantToolsEnabled
        e[webSearchKey] = resolved.assistantToolsEnabled
        e[deviceSensingKey] = resolved.assistantToolsEnabled
        e[locationKey] = resolved.locationAroundEnabled

        e[booleanPreferencesKey(WebSearchPreferences.KEY_ENABLED)] =
            resolved.assistantToolsEnabled
        e[booleanPreferencesKey(DeviceSensingPreferences.KEY_ENABLED)] =
            resolved.assistantToolsEnabled
        e[booleanPreferencesKey(SystemToolsPreferences.KEY_MASTER)] =
            resolved.assistantToolsEnabled
        if (resolved.assistantToolsEnabled) {
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
        e[booleanPreferencesKey(LocalInferencePreferences.KEY_ENABLED)] =
            resolved.localInferenceEnabled
        e[booleanPreferencesKey(SceneSensingPreferences.KEY_ENABLED)] =
            resolved.sceneVisionEnabled
        e[booleanPreferencesKey(LocationPreferences.KEY_ENABLED)] =
            resolved.locationAroundEnabled
    }

    override suspend fun getConfig(): SmartCapabilitiesConfig {
        ensureMigrated()
        val prefs = dataStore.data.first()
        val assistant = prefs[assistantToolsKey]
            ?: prefs[systemToolsKey]
            ?: SmartCapabilitiesConfig.DEFAULT_ASSISTANT_TOOLS
        val locationAround = prefs[locationAroundKey]
            ?: prefs[locationKey]
            ?: SmartCapabilitiesConfig.DEFAULT_LOCATION_AROUND
        return SmartCapabilitiesConfig(
            masterEnabled = prefs[masterKey] ?: SmartCapabilitiesConfig.DEFAULT_MASTER,
            localInferenceEnabled = prefs[localKey]
                ?: SmartCapabilitiesConfig.DEFAULT_LOCAL_INFERENCE,
            voiceEnabled = prefs[voiceKey] ?: SmartCapabilitiesConfig.DEFAULT_VOICE,
            assistantToolsEnabled = assistant,
            locationAroundEnabled = locationAround,
            navigateEnabled = prefs[navigateKey] ?: SmartCapabilitiesConfig.DEFAULT_NAVIGATE,
            guideEnabled = prefs[guideKey] ?: SmartCapabilitiesConfig.DEFAULT_GUIDE,
            sceneVisionEnabled = prefs[sceneKey] ?: SmartCapabilitiesConfig.DEFAULT_SCENE_VISION,
            migratedV1 = prefs[migratedV1Key] ?: false,
            migratedV2 = prefs[migratedV2Key] ?: false
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
                SmartCapabilityId.ASSISTANT_TOOLS,
                SmartCapabilityId.SYSTEM_TOOLS,
                SmartCapabilityId.WEB_SEARCH,
                SmartCapabilityId.DEVICE_SENSING -> {
                    // 聚合：一组开关同步三模块
                    e[assistantToolsKey] = enabled
                    e[systemToolsKey] = enabled
                    e[webSearchKey] = enabled
                    e[deviceSensingKey] = enabled
                    e[booleanPreferencesKey(SystemToolsPreferences.KEY_MASTER)] = enabled
                    e[booleanPreferencesKey(WebSearchPreferences.KEY_ENABLED)] = enabled
                    e[booleanPreferencesKey(DeviceSensingPreferences.KEY_ENABLED)] = enabled
                }
                SmartCapabilityId.LOCATION_AROUND,
                SmartCapabilityId.LOCATION -> {
                    e[locationAroundKey] = enabled
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
        const val KEY_ASSISTANT_TOOLS = "smart_capabilities_assistant_tools"
        const val KEY_LOCATION_AROUND = "smart_capabilities_location_around"
        const val KEY_NAVIGATE = "smart_capabilities_navigate"
        const val KEY_GUIDE = "smart_capabilities_guide"
        const val KEY_SCENE_VISION = "smart_capabilities_scene_vision"
        const val KEY_MIGRATED_V1 = "smart_capabilities_migrated_v1"
        const val KEY_MIGRATED_V2 = "smart_capabilities_migrated_v2"

        // 兼容细项键（v1）
        const val KEY_SYSTEM_TOOLS = "smart_capabilities_system_tools"
        const val KEY_WEB_SEARCH = "smart_capabilities_web_search"
        const val KEY_DEVICE_SENSING = "smart_capabilities_device_sensing"
        const val KEY_LOCATION = "smart_capabilities_location"
    }
}
