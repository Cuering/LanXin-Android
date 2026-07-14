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

package com.lanxin.android.builtin.scheduler.di

import android.content.Context
import com.lanxin.android.builtin.scheduler.SchedulerPlugin
import com.lanxin.android.builtin.scheduler.data.SchedulerDao
import com.lanxin.android.builtin.scheduler.data.SchedulerDatabase
import com.lanxin.android.builtin.scheduler.domain.CrontabParser
import com.lanxin.android.plugin.PluginManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SchedulerModule {

    @Provides
    @Singleton
    fun provideSchedulerDatabase(@ApplicationContext context: Context): SchedulerDatabase =
        SchedulerDatabase.getInstance(context)

    @Provides
    fun provideSchedulerDao(db: SchedulerDatabase): SchedulerDao = db.schedulerDao()

    @Provides
    @Singleton
    fun provideCrontabParser(): CrontabParser = CrontabParser()

    @Provides
    @Singleton
    fun provideSchedulerPluginRegistration(
        pluginManager: PluginManager,
        plugin: SchedulerPlugin
    ): SchedulerPluginRegistration {
        pluginManager.register(plugin)
        return SchedulerPluginRegistration(plugin)
    }
}

/** 注册副作用载体，避免与 [SchedulerPlugin] 的 @Inject 绑定冲突。 */
data class SchedulerPluginRegistration(val plugin: SchedulerPlugin)
