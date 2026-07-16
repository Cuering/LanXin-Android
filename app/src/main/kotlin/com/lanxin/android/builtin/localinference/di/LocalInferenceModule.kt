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

package com.lanxin.android.builtin.localinference.di

import com.lanxin.android.builtin.localinference.data.DefaultLocalInferenceProvider
import com.lanxin.android.builtin.localinference.data.LocalInferencePreferences
import com.lanxin.android.builtin.localinference.data.StubLocalLlmEngine
import com.lanxin.android.builtin.localinference.domain.LocalInferenceProvider
import com.lanxin.android.builtin.localinference.domain.LocalInferenceSettings
import com.lanxin.android.builtin.localinference.domain.LocalLlmEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.migration.DisableInstallInCheck
import javax.inject.Singleton

/**
 * Phase 6.1 本地推理 DI。
 *
 * 对齐 KnowledgeModule / SyncModule 风格。
 * 后续 MNN 实现就绪后，将 [StubLocalLlmEngine] 绑定替换为 MnnLocalLlmEngine。
 */
@Module
@DisableInstallInCheck
@InstallIn(SingletonComponent::class)
abstract class LocalInferenceModule {

    @Binds
    @Singleton
    abstract fun bindLocalLlmEngine(impl: StubLocalLlmEngine): LocalLlmEngine

    @Binds
    @Singleton
    abstract fun bindLocalInferenceSettings(impl: LocalInferencePreferences): LocalInferenceSettings

    @Binds
    @Singleton
    abstract fun bindLocalInferenceProvider(impl: DefaultLocalInferenceProvider): LocalInferenceProvider
}
