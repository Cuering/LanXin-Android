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

package com.lanxin.android.builtin.systemtools.di

import com.lanxin.android.builtin.systemtools.SystemToolsPlugin
import com.lanxin.android.builtin.systemtools.data.SystemToolsPreferences
import com.lanxin.android.builtin.systemtools.domain.SystemToolsSettings
import com.lanxin.android.plugin.PluginManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.migration.DisableInstallInCheck
import javax.inject.Singleton

/**
 * Phase 7 系统能力 DI — 绑定。
 */
@Module
@DisableInstallInCheck
@InstallIn(SingletonComponent::class)
abstract class SystemToolsModule {

    @Binds
    @Singleton
    abstract fun bindSystemToolsSettings(impl: SystemToolsPreferences): SystemToolsSettings
}

/**
 * Phase 7 系统能力 DI — 插件注册。
 */
@Module
@InstallIn(SingletonComponent::class)
object SystemToolsRegistrationModule {

    @Provides
    @Singleton
    fun provideSystemToolsPluginRegistration(
        pluginManager: PluginManager,
        plugin: SystemToolsPlugin
    ): SystemToolsPluginRegistration {
        pluginManager.register(plugin)
        return SystemToolsPluginRegistration(plugin)
    }
}

/** 注册副作用载体。 */
data class SystemToolsPluginRegistration(val plugin: SystemToolsPlugin)
