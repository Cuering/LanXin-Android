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

package com.lanxin.android.builtin.persona.di

import android.content.Context
import com.lanxin.android.builtin.persona.PersonaPlugin
import com.lanxin.android.builtin.persona.data.PersonaDao
import com.lanxin.android.builtin.persona.data.PersonaDatabase
import com.lanxin.android.plugin.PluginManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 人格模块 DI。
 *
 * - PersonaDatabase / PersonaDao 由此提供
 * - PersonaRepository / PersonaPlugin 使用 @Inject constructor
 * - 通过 [providePersonaPluginRegistration] 注册到 PluginManager
 */
@Module
@InstallIn(SingletonComponent::class)
object PersonaModule {

    @Provides
    @Singleton
    fun providePersonaDatabase(@ApplicationContext context: Context): PersonaDatabase =
        PersonaDatabase.getInstance(context)

    @Provides
    fun providePersonaDao(db: PersonaDatabase): PersonaDao = db.personaDao()

    /**
     * 注册 PersonaPlugin 到 PluginManager。
     * 返回包装类型，避免与 PersonaPlugin 的 @Inject 绑定冲突。
     */
    @Provides
    @Singleton
    fun providePersonaPluginRegistration(
        pluginManager: PluginManager,
        plugin: PersonaPlugin
    ): PersonaPluginRegistration {
        pluginManager.register(plugin)
        return PersonaPluginRegistration(plugin)
    }
}

/** 注册副作用载体，避免与 [PersonaPlugin] 的 @Inject 绑定冲突。 */
data class PersonaPluginRegistration(val plugin: PersonaPlugin)
