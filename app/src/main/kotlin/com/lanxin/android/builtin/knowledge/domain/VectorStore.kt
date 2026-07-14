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

package com.lanxin.android.builtin.knowledge.domain

/**
 * 向量存储接口。
 * P0：ObjectBox 实现 HNSW 余弦近邻检索。
 */
interface VectorStore {
    /** 写入或覆盖向量 */
    suspend fun upsert(
        externalId: Long,
        source: String,
        embedding: FloatArray,
        textPreview: String
    ): Long

    /** 按内部 id 删除 */
    suspend fun delete(id: Long)

    /** 按业务 id + source 删除 */
    suspend fun deleteByExternal(externalId: Long, source: String)

    /** 余弦近邻搜索 */
    suspend fun search(
        query: FloatArray,
        topK: Int,
        source: String? = null
    ): List<VectorHit>

    /** 统计条目 */
    suspend fun count(source: String? = null): Long

    /** 清空 */
    suspend fun clear()
}

/**
 * 检索命中结果。
 */
data class VectorHit(
    val id: Long,
    val externalId: Long,
    val source: String,
    val textPreview: String,
    // 余弦相似度 [0,1]，越大越相似
    val score: Float,
)

/**
 * 来源枚举常量。
 */
object VectorSource {
    const val MEMORY = "memory"
    const val KNOWLEDGE = "knowledge"
}
