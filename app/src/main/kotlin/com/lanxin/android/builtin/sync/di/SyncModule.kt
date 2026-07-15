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

package com.lanxin.android.builtin.sync.di

import com.lanxin.android.builtin.sync.data.DefaultSyncRepository
import com.lanxin.android.builtin.sync.data.HttpSyncClient
import com.lanxin.android.builtin.sync.domain.SyncClient
import com.lanxin.android.builtin.sync.domain.SyncRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase 5.1 同步引擎 DI。
 *
 * SyncPreferences / InMemorySyncOutbox 使用 @Inject constructor，Hilt 自动提供。
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideSyncClient(impl: HttpSyncClient): SyncClient = impl

    @Provides
    @Singleton
    fun provideSyncRepository(impl: DefaultSyncRepository): SyncRepository = impl
}
