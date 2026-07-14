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

package com.lanxin.android.builtin.knowledge.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * P3 自动知识积累设置（DataStore）。
 */
@Singleton
class AutoKnowledgeSettings @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val enabledKey = booleanPreferencesKey(KEY_ENABLED)
    private val windowKey = intPreferencesKey(KEY_WINDOW)

    val enabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[enabledKey] ?: DEFAULT_ENABLED
    }

    val historyWindowSizeFlow: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[windowKey] ?: DEFAULT_WINDOW).coerceIn(MIN_WINDOW, MAX_WINDOW)
    }

    suspend fun isEnabled(): Boolean =
        dataStore.data.map { it[enabledKey] ?: DEFAULT_ENABLED }.first()

    suspend fun getHistoryWindowSize(): Int =
        dataStore.data.map {
            (it[windowKey] ?: DEFAULT_WINDOW).coerceIn(MIN_WINDOW, MAX_WINDOW)
        }.first()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[enabledKey] = enabled }
    }

    suspend fun setHistoryWindowSize(size: Int) {
        dataStore.edit {
            it[windowKey] = size.coerceIn(MIN_WINDOW, MAX_WINDOW)
        }
    }

    companion object {
        const val KEY_ENABLED = "auto_knowledge_enabled"
        const val KEY_WINDOW = "auto_knowledge_history_window"
        const val DEFAULT_ENABLED = true
        const val DEFAULT_WINDOW = 10
        const val MIN_WINDOW = 5
        const val MAX_WINDOW = 20
    }
}
