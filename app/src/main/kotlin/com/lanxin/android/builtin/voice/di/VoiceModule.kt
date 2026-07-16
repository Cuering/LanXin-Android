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

package com.lanxin.android.builtin.voice.di

import com.lanxin.android.builtin.voice.data.AndroidMicPermissionChecker
import com.lanxin.android.builtin.voice.data.AsrPreferences
import com.lanxin.android.builtin.voice.data.StubAsrEngine
import com.lanxin.android.builtin.voice.domain.AsrEngine
import com.lanxin.android.builtin.voice.domain.AsrSettings
import com.lanxin.android.builtin.voice.domain.MicPermissionChecker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.migration.DisableInstallInCheck
import javax.inject.Singleton

/**
 * Phase 6.4 离线 ASR DI。
 *
 * 对齐 LocalInferenceModule 风格。
 * 后续 Sherpa 实现就绪后，将 [StubAsrEngine] 绑定替换为 SherpaAsrEngine。
 */
@Module
@DisableInstallInCheck
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {

    @Binds
    @Singleton
    abstract fun bindAsrEngine(impl: StubAsrEngine): AsrEngine

    @Binds
    @Singleton
    abstract fun bindAsrSettings(impl: AsrPreferences): AsrSettings

    @Binds
    @Singleton
    abstract fun bindMicPermissionChecker(impl: AndroidMicPermissionChecker): MicPermissionChecker
}
