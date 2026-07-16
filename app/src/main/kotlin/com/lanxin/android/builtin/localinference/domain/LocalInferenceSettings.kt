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

package com.lanxin.android.builtin.localinference.domain

/**
 * 本地推理配置读写（可替换，便于单测）。
 *
 * 默认实现 [com.lanxin.android.builtin.localinference.data.LocalInferencePreferences]。
 */
interface LocalInferenceSettings {

    suspend fun getConfig(): LocalInferenceConfig

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setModelPath(path: String?)

    suspend fun setMaxTokens(maxTokens: Int)

    suspend fun setTemperature(temperature: Float)

    /**
     * 是否偏好本地路由（为 6.2/6.3 预留；默认 false）。
     */
    suspend fun isPreferLocal(): Boolean

    suspend fun setPreferLocal(prefer: Boolean)
}
