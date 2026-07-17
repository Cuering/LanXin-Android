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

package com.lanxin.android.builtin.platform.di

import com.lanxin.android.builtin.platform.PlatformPlugin
import com.lanxin.android.builtin.platform.data.DeviceSensingPreferences
import com.lanxin.android.builtin.platform.data.WebSearchPreferences
import com.lanxin.android.builtin.platform.domain.DeviceSensingSettings
import com.lanxin.android.builtin.platform.domain.WebSearchSettings
import com.lanxin.android.plugin.PluginManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.migration.DisableInstallInCheck
import javax.inject.Singleton

/**
 * 手机平台工具模块 DI — 绑定。
 *
 * - WebSearchSettings → WebSearchPreferences（DataStore）
 * - DeviceSensingSettings → DeviceSensingPreferences（DataStore）
 */
@Module
@DisableInstallInCheck
@InstallIn(SingletonComponent::class)
abstract class PlatformBindModule {

    @Binds
    @Singleton
    abstract fun bindWebSearchSettings(impl: WebSearchPreferences): WebSearchSettings

    @Binds
    @Singleton
    abstract fun bindDeviceSensingSettings(impl: DeviceSensingPreferences): DeviceSensingSettings
}

/**
 * 手机平台工具模块 DI — 注册。
 *
 * - ClipboardTool / AppInstallCheckTool / SystemInfoTool / PlatformPlugin 使用 @Inject constructor
 * - 通过 [providePlatformPluginRegistration] 注册到 PluginManager
 */
@Module
@InstallIn(SingletonComponent::class)
object PlatformModule {

    /**
     * 注册 PlatformPlugin 到 PluginManager。
     * 返回包装类型，避免与 PlatformPlugin 的 @Inject 绑定冲突。
     */
    @Provides
    @Singleton
    fun providePlatformPluginRegistration(
        pluginManager: PluginManager,
        plugin: PlatformPlugin
    ): PlatformPluginRegistration {
        pluginManager.register(plugin)
        return PlatformPluginRegistration(plugin)
    }
}

/** 注册副作用载体，避免与 [PlatformPlugin] 的 @Inject 绑定冲突。 */
data class PlatformPluginRegistration(val plugin: PlatformPlugin)
