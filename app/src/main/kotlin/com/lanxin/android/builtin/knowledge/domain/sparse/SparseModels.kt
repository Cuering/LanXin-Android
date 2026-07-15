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

package com.lanxin.android.builtin.knowledge.domain.sparse

/**
 * 稀疏索引条目（BM25 / FTS 共用）。
 *
 * [documentId] 与业务侧主键对齐（memory id 或 knowledge externalId）。
 * [source] 与 [com.lanxin.android.builtin.knowledge.domain.VectorSource] 一致。
 */
data class SparseIndexItem(
    val documentId: Long,
    val source: String,
    val text: String,
    /** 可选透传字段（如记忆 type），检索结果原样带回。 */
    val payload: String? = null
)

/**
 * 稀疏检索命中。
 */
data class SparseSearchResult(
    val documentId: Long,
    val source: String,
    val text: String,
    /** BM25 原始分数，越大越相关 */
    val score: Double,
    val payload: String? = null
)
