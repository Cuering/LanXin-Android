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

package com.lanxin.android.builtin.platform.domain

/**
 * 场景识别设置门面（DataStore 实现）。
 */
interface SceneSensingSettings {
    suspend fun getConfig(): SceneSensingConfig

    /** 总开关；关时不识别。 */
    suspend fun setEnabled(enabled: Boolean)

    /**
     * 记录用户同意隐私说明。
     * 同意后才允许 [setEnabled](true) 生效（由 Gate / ViewModel 约束）。
     */
    suspend fun setConsentGranted(granted: Boolean)

    /** 缓存最近识别结果（仅本地；可 [clearLastScene]）。 */
    suspend fun setLastScene(sceneId: String, statusText: String)

    /** 清除最近场景缓存。 */
    suspend fun clearLastScene()
}
