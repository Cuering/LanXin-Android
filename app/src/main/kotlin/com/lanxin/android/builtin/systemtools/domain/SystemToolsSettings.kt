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

package com.lanxin.android.builtin.systemtools.domain

/**
 * 系统能力设置读写抽象（DataStore 实现）。
 */
interface SystemToolsSettings {
    suspend fun getConfig(): SystemToolsConfig
    suspend fun setMasterEnabled(enabled: Boolean)
    suspend fun setCalendarEnabled(enabled: Boolean)
    suspend fun setAlarmEnabled(enabled: Boolean)
    suspend fun setNotesEnabled(enabled: Boolean)
    suspend fun setUserFileEnabled(enabled: Boolean)
    suspend fun setRequireConfirmOnWrite(require: Boolean)
}
