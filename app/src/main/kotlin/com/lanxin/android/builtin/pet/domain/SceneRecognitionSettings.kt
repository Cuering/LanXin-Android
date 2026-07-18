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

package com.lanxin.android.builtin.pet.domain

/**
 * 场景识别配置读写抽象（DataStore 实现）。
 */
interface SceneRecognitionSettings {
    suspend fun getConfig(): SceneRecognitionConfig
    suspend fun setEnabled(enabled: Boolean)
    suspend fun setConsentGranted(granted: Boolean)

    /** 原子写入开关 + 确认状态。 */
    suspend fun update(config: SceneRecognitionConfig)
}
