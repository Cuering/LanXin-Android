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

package com.lanxin.android.builtin.knowledge.di

import android.content.Context
import com.lanxin.android.builtin.knowledge.data.sparse.SparseDatabase
import com.lanxin.android.builtin.knowledge.data.sparse.SparseFtsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.migration.DisableInstallInCheck
import javax.inject.Singleton

/**
 * 稀疏检索 Room 库 DI。
 * SparseStore 使用 @Inject constructor，由 Hilt 自动提供。
 */
@Module
@DisableInstallInCheck
@InstallIn(SingletonComponent::class)
object SparseModule {

    @Provides
    @Singleton
    fun provideSparseDatabase(@ApplicationContext context: Context): SparseDatabase =
        SparseDatabase.getInstance(context)

    @Provides
    fun provideSparseFtsDao(db: SparseDatabase): SparseFtsDao = db.sparseFtsDao()
}
