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

import com.lanxin.android.builtin.knowledge.data.CompositeDocumentParser
import com.lanxin.android.builtin.knowledge.data.ObjectBoxVectorStore
import com.lanxin.android.builtin.knowledge.data.OnnxEmbeddingService
import com.lanxin.android.builtin.knowledge.domain.DocumentParser
import com.lanxin.android.builtin.knowledge.domain.EmbeddingService
import com.lanxin.android.builtin.knowledge.domain.VectorStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.migration.DisableInstallInCheck
import javax.inject.Singleton

/**
 * 知识库模块 Hilt DI 绑定。
 *
 * 注册时机：由 KnowledgePluginRegistration 或内置 KnowledgePlugin 引导。
 * 作为 app/builtin 的一部分，@InstallIn(SingletonComponent) 自动注册到 Hilt 图。
 */
@Module
@DisableInstallInCheck
@InstallIn(SingletonComponent::class)
abstract class KnowledgeModule {

    @Binds
    @Singleton
    abstract fun bindEmbeddingService(impl: OnnxEmbeddingService): EmbeddingService

    @Binds
    @Singleton
    abstract fun bindVectorStore(impl: ObjectBoxVectorStore): VectorStore

    @Binds
    @Singleton
    abstract fun bindDocumentParser(impl: CompositeDocumentParser): DocumentParser

    // VectorPipeline / KnowledgePlugin / TextChunker / KnowledgeImportService
    // 使用 @Inject constructor 自动解析
}
