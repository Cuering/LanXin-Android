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

package com.lanxin.android.builtin.capabilities.domain

/**
 * 智能能力配置读写抽象（DataStore 实现）。
 *
 * [getConfig] 会在首次调用时执行 v1 迁移（从未配置 → 新默认；显式关 → 保留）。
 */
interface SmartCapabilitiesSettings {
    suspend fun getConfig(): SmartCapabilitiesConfig
    suspend fun setMasterEnabled(enabled: Boolean)
    suspend fun setChildEnabled(id: SmartCapabilityId, enabled: Boolean)

    /** 确保迁移已跑完（应用启动或设置页进入时可显式调用）。 */
    suspend fun ensureMigrated()
}
