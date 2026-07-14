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

package com.lanxin.android.builtin.statistics.di

import android.content.Context
import com.lanxin.android.builtin.statistics.StatisticsPlugin
import com.lanxin.android.builtin.statistics.data.StatisticsDao
import com.lanxin.android.builtin.statistics.data.StatisticsDatabase
import com.lanxin.android.plugin.PluginManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 统计模块 DI。
 * StatisticsRepository / StatisticsPlugin 使用 @Inject constructor。
 */
@Module
@InstallIn(SingletonComponent::class)
object StatisticsModule {

    @Provides
    @Singleton
    fun provideStatisticsDatabase(@ApplicationContext context: Context): StatisticsDatabase =
        StatisticsDatabase.getInstance(context)

    @Provides
    fun provideStatisticsDao(db: StatisticsDatabase): StatisticsDao = db.statisticsDao()

    @Provides
    @Singleton
    fun provideStatisticsPluginRegistration(
        pluginManager: PluginManager,
        plugin: StatisticsPlugin
    ): StatisticsPluginRegistration {
        pluginManager.register(plugin)
        return StatisticsPluginRegistration(plugin)
    }
}

/** 注册副作用载体，避免与 [StatisticsPlugin] 的 @Inject 绑定冲突。 */
data class StatisticsPluginRegistration(val plugin: StatisticsPlugin)
