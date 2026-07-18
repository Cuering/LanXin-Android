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

import com.lanxin.android.builtin.localinference.data.ConnectivityNetworkStatusProvider
import com.lanxin.android.builtin.localinference.data.DefaultLocalInferenceProvider
import com.lanxin.android.builtin.localinference.data.LocalInferencePreferences
import com.lanxin.android.builtin.localinference.data.MnnLocalLlmEngine
import com.lanxin.android.builtin.localinference.domain.LocalInferenceProvider
import com.lanxin.android.builtin.localinference.domain.LocalInferenceSettings
import com.lanxin.android.builtin.localinference.domain.LocalLlmEngine
import com.lanxin.android.builtin.localinference.domain.NetworkStatusProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.migration.DisableInstallInCheck
import javax.inject.Singleton

/**
 * Phase 6.x 本地推理 DI。
 *
 * 绑定 [MnnLocalLlmEngine]：native 可用则真推理，否则路径合法时 stub 降级。
 * [com.lanxin.android.builtin.localinference.data.StubLocalLlmEngine] 保留单测。
 */
@Module
@DisableInstallInCheck
@InstallIn(SingletonComponent::class)
abstract class LocalInferenceModule {

    @Binds
    @Singleton
    abstract fun bindLocalLlmEngine(impl: MnnLocalLlmEngine): LocalLlmEngine

    @Binds
    @Singleton
    abstract fun bindLocalInferenceSettings(impl: LocalInferencePreferences): LocalInferenceSettings

    @Binds
    @Singleton
    abstract fun bindLocalInferenceProvider(impl: DefaultLocalInferenceProvider): LocalInferenceProvider

    @Binds
    @Singleton
    abstract fun bindNetworkStatusProvider(impl: ConnectivityNetworkStatusProvider): NetworkStatusProvider
}
