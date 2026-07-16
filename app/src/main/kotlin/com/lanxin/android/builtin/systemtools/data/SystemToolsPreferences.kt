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

package com.lanxin.android.builtin.systemtools.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.lanxin.android.builtin.systemtools.domain.SystemToolsConfig
import com.lanxin.android.builtin.systemtools.domain.SystemToolsSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 系统能力配置（DataStore）。
 *
 * 键前缀 `system_tools_`，与 local_inference_ / offline_asr_ / desktop_pet_ 隔离。
 * **默认全关**；写操作默认需确认。
 */
@Singleton
class SystemToolsPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SystemToolsSettings {

    private val masterKey = booleanPreferencesKey(KEY_MASTER)
    private val calendarKey = booleanPreferencesKey(KEY_CALENDAR)
    private val alarmKey = booleanPreferencesKey(KEY_ALARM)
    private val notesKey = booleanPreferencesKey(KEY_NOTES)
    private val userFileKey = booleanPreferencesKey(KEY_USER_FILE)
    private val confirmWriteKey = booleanPreferencesKey(KEY_CONFIRM_WRITE)

    override suspend fun getConfig(): SystemToolsConfig {
        val prefs = dataStore.data.first()
        return SystemToolsConfig(
            masterEnabled = prefs[masterKey] ?: false,
            calendarEnabled = prefs[calendarKey] ?: false,
            alarmEnabled = prefs[alarmKey] ?: false,
            notesEnabled = prefs[notesKey] ?: false,
            userFileEnabled = prefs[userFileKey] ?: false,
            requireConfirmOnWrite = prefs[confirmWriteKey] ?: true
        )
    }

    override suspend fun setMasterEnabled(enabled: Boolean) {
        dataStore.edit { it[masterKey] = enabled }
    }

    override suspend fun setCalendarEnabled(enabled: Boolean) {
        dataStore.edit { it[calendarKey] = enabled }
    }

    override suspend fun setAlarmEnabled(enabled: Boolean) {
        dataStore.edit { it[alarmKey] = enabled }
    }

    override suspend fun setNotesEnabled(enabled: Boolean) {
        dataStore.edit { it[notesKey] = enabled }
    }

    override suspend fun setUserFileEnabled(enabled: Boolean) {
        dataStore.edit { it[userFileKey] = enabled }
    }

    override suspend fun setRequireConfirmOnWrite(require: Boolean) {
        dataStore.edit { it[confirmWriteKey] = require }
    }

    companion object {
        const val KEY_MASTER = "system_tools_master_enabled"
        const val KEY_CALENDAR = "system_tools_calendar_enabled"
        const val KEY_ALARM = "system_tools_alarm_enabled"
        const val KEY_NOTES = "system_tools_notes_enabled"
        const val KEY_USER_FILE = "system_tools_user_file_enabled"
        const val KEY_CONFIRM_WRITE = "system_tools_require_confirm_on_write"
    }
}
