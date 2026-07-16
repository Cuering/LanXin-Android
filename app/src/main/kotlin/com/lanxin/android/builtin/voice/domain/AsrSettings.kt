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

package com.lanxin.android.builtin.voice.domain

/**
 * 离线 ASR 配置读写（可替换，便于单测）。
 *
 * 默认实现 [com.lanxin.android.builtin.voice.data.AsrPreferences]。
 */
interface AsrSettings {

    suspend fun getConfig(): AsrConfig

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setModelPath(path: String?)

    suspend fun setLanguage(language: String)

    suspend fun setSampleRateHz(sampleRateHz: Int)
}
