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

import android.content.Context
import com.lanxin.android.builtin.systemtools.SystemToolsPlugin
import com.lanxin.android.builtin.systemtools.data.AndroidAlarmSetter
import com.lanxin.android.builtin.systemtools.data.AndroidCalendarReader
import com.lanxin.android.builtin.systemtools.data.AndroidSystemToolsIntentLauncher
import com.lanxin.android.builtin.systemtools.data.AndroidSystemToolsPermissionChecker
import com.lanxin.android.builtin.systemtools.data.SystemToolsPreferences
import com.lanxin.android.builtin.systemtools.data.files.AndroidUserFileIoGateway
import com.lanxin.android.builtin.systemtools.data.files.InMemoryUserFileCatalog
import com.lanxin.android.builtin.systemtools.data.notes.AndroidNotesSafGateway
import com.lanxin.android.builtin.systemtools.data.notes.NoteDao
import com.lanxin.android.builtin.systemtools.data.notes.NotesDatabase
import com.lanxin.android.builtin.systemtools.data.notes.RoomNotesStore
import com.lanxin.android.builtin.systemtools.domain.AlarmClockGateway
import com.lanxin.android.builtin.systemtools.domain.CalendarGateway
import com.lanxin.android.builtin.systemtools.domain.NotesSafGateway
import com.lanxin.android.builtin.systemtools.domain.NotesStore
import com.lanxin.android.builtin.systemtools.domain.SystemToolsIntentLauncher
import com.lanxin.android.builtin.systemtools.domain.SystemToolsPermissionChecker
import com.lanxin.android.builtin.systemtools.domain.SystemToolsSettings
import com.lanxin.android.builtin.systemtools.domain.UserFileCatalog
import com.lanxin.android.builtin.systemtools.domain.UserFileIoGateway
import com.lanxin.android.plugin.PluginManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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

    @Binds
    @Singleton
    abstract fun bindCalendarGateway(impl: AndroidCalendarReader): CalendarGateway

    @Binds
    @Singleton
    abstract fun bindAlarmClockGateway(impl: AndroidAlarmSetter): AlarmClockGateway

    @Binds
    @Singleton
    abstract fun bindIntentLauncher(
        impl: AndroidSystemToolsIntentLauncher
    ): SystemToolsIntentLauncher

    @Binds
    @Singleton
    abstract fun bindPermissionChecker(
        impl: AndroidSystemToolsPermissionChecker
    ): SystemToolsPermissionChecker

    @Binds
    @Singleton
    abstract fun bindNotesStore(impl: RoomNotesStore): NotesStore

    @Binds
    @Singleton
    abstract fun bindNotesSafGateway(impl: AndroidNotesSafGateway): NotesSafGateway

    @Binds
    @Singleton
    abstract fun bindUserFileCatalog(impl: InMemoryUserFileCatalog): UserFileCatalog

    @Binds
    @Singleton
    abstract fun bindUserFileIoGateway(impl: AndroidUserFileIoGateway): UserFileIoGateway
}

/**
 * Phase 7 系统能力 DI — Room 与插件注册。
 */
@Module
@InstallIn(SingletonComponent::class)
object SystemToolsRegistrationModule {

    @Provides
    @Singleton
    fun provideNotesDatabase(@ApplicationContext context: Context): NotesDatabase =
        NotesDatabase.getInstance(context)

    @Provides
    fun provideNoteDao(db: NotesDatabase): NoteDao = db.noteDao()

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
