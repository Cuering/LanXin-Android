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

package com.lanxin.android.builtin.pet.domain

import com.lanxin.android.plugins.memory.domain.memory.MemoryInjector
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全屏陪伴专用记忆增强：与 Chat 全量注入解耦，便于单测与快速路径。
 */
fun interface CompanionMemoryEnricher {
    /**
     * 将精简记忆拼到用户句前；无命中或关闭时原样返回。
     */
    suspend fun enrich(userText: String): String
}

/**
 * 默认实现：委托 [MemoryInjector.injectForCompanion]（稀疏、短预算）。
 */
@Singleton
class DefaultCompanionMemoryEnricher @Inject constructor(
    private val memoryInjector: MemoryInjector
) : CompanionMemoryEnricher {
    override suspend fun enrich(userText: String): String =
        memoryInjector.injectForCompanion(userText)
}

/** 单测 / 无记忆时的透传。 */
object NoOpCompanionMemoryEnricher : CompanionMemoryEnricher {
    override suspend fun enrich(userText: String): String = userText
}
