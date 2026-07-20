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

package com.lanxin.android.builtin.navigate.di

import com.lanxin.android.builtin.navigate.NavigatePlugin
import com.lanxin.android.builtin.navigate.domain.NavigateConfig
import com.lanxin.android.plugin.PluginManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 导航 Navigate 插件 DI — 注册到 PluginManager，**默认 OFF**。
 */
@Module
@InstallIn(SingletonComponent::class)
object NavigateModule {

    @Provides
    @Singleton
    fun provideNavigatePluginRegistration(
        pluginManager: PluginManager,
        plugin: NavigatePlugin
    ): NavigatePluginRegistration {
        pluginManager.register(plugin, defaultEnabled = NavigateConfig.DEFAULT_ENABLED)
        return NavigatePluginRegistration(plugin)
    }
}

/** 注册副作用载体。 */
data class NavigatePluginRegistration(val plugin: NavigatePlugin)
