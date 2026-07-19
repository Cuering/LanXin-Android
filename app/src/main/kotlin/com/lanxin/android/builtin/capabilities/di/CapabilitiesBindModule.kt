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

package com.lanxin.android.builtin.capabilities.di

import com.lanxin.android.builtin.capabilities.data.LocationPreferences
import com.lanxin.android.builtin.capabilities.data.SmartCapabilitiesPreferences
import com.lanxin.android.builtin.capabilities.domain.LocationSettings
import com.lanxin.android.builtin.capabilities.domain.SmartCapabilitiesSettings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.migration.DisableInstallInCheck
import javax.inject.Singleton

/**
 * 智能能力模块 DI。
 */
@Module
@DisableInstallInCheck
@InstallIn(SingletonComponent::class)
abstract class CapabilitiesBindModule {

    @Binds
    @Singleton
    abstract fun bindSmartCapabilitiesSettings(
        impl: SmartCapabilitiesPreferences
    ): SmartCapabilitiesSettings

    @Binds
    @Singleton
    abstract fun bindLocationSettings(impl: LocationPreferences): LocationSettings
}
