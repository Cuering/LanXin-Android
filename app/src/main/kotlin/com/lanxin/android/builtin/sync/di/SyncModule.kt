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
import com.lanxin.android.builtin.sync.domain.SyncRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.migration.DisableInstallInCheck
import javax.inject.Singleton

/**
 * Phase 5.1 同步引擎 DI。
 *
 * 对齐 KnowledgeModule：abstract + @Binds + @DisableInstallInCheck。
 * HttpSyncClient / SyncPreferences / InMemorySyncOutbox 使用 @Inject constructor。
 * 不绑定 HTTP 抽象接口（KSP 对 SyncClient/SyncApi 解析为 ERROR type）。
 */
@Module
@DisableInstallInCheck
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    @Singleton
    abstract fun bindSyncRepository(impl: DefaultSyncRepository): SyncRepository
}
