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

import android.util.Log
import com.lanxin.android.builtin.knowledge.data.OnnxEmbeddingService
import com.lanxin.android.builtin.knowledge.data.sparse.SparseStore
import com.lanxin.android.builtin.knowledge.domain.sparse.SparseIndexItem
import com.lanxin.android.builtin.knowledge.domain.sparse.SparseRrf
import com.lanxin.android.builtin.knowledge.domain.sparse.SparseSearchResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

/**
 * 向量管道门面：embed + upsert + dense/sparse 检索。
 * 供 MemoryInjector / knowledge 导入使用。
 */
@Singleton
class VectorPipeline @Inject constructor(
    private val embeddingService: EmbeddingService,
    private val vectorStore: VectorStore,
    private val sparseStore: SparseStore
) {
    /**
     * 预热模型（Application 启动时调用）。
     */
    suspend fun warmUp(): Boolean {
        val svc = embeddingService as? OnnxEmbeddingService
        return svc?.initialize() ?: embeddingService.isReady
    }

    /**
     * 文本 → 向量 → 入库，并同步稀疏索引。
     * @return 向量库 id；embed 失败时返回 -1
     */
    suspend fun index(
        externalId: Long,
        source: String,
        text: String
    ): Long {
        if (text.isBlank()) return -1L
        val embedding = embeddingService.embed(text)
        val id = vectorStore.upsert(
            externalId = externalId,
            source = source,
            embedding = embedding,
            textPreview = text
        )
        // 稀疏路：失败不阻断 dense
        runCatching {
            sparseStore.upsert(
                SparseIndexItem(
                    documentId = externalId,
                    source = source,
                    text = text
                )
            )
        }.onFailure {
            Log.w(TAG, "sparse upsert failed: ${it.message}")
        }
        return id
    }

    /**
     * 语义（dense）检索。
     */
    suspend fun search(
        query: String,
        topK: Int = 10,
        source: String? = null
    ): List<VectorHit> {
        if (query.isBlank()) return emptyList()
        val embedding = embeddingService.embed(query)
        return vectorStore.search(embedding, topK, source)
    }

    /**
     * 稀疏（BM25）检索。
     */
    suspend fun searchSparse(
        query: String,
        topK: Int = 10,
        source: String? = null
    ): List<SparseSearchResult> {
        if (query.isBlank()) return emptyList()
        return try {
            sparseStore.search(query, topK, source)
        } catch (e: Exception) {
            Log.w(TAG, "sparse search failed: ${e.message}")
            // 再试 VectorStore 默认实现（通常为空）
            vectorStore.searchSparse(query, topK, source).map {
                SparseSearchResult(
                    documentId = it.externalId,
                    source = it.source,
                    text = it.textPreview,
                    score = it.score.toDouble()
                )
            }
        }
    }

    /**
     * Dense + Sparse RRF 融合检索。
     * sparse 失败时退化为纯 dense。
     */
    suspend fun searchHybrid(
        query: String,
        topK: Int = 10,
        source: String? = null
    ): List<VectorHit> {
        if (query.isBlank()) return emptyList()

        val dense = try {
            search(query, topK * 2, source)
        } catch (e: Exception) {
            Log.w(TAG, "dense search failed: ${e.message}")
            emptyList()
        }

        val sparse = try {
            searchSparse(query, topK * 2, source)
        } catch (_: Exception) {
            emptyList()
        }

        if (sparse.isEmpty()) return dense.take(topK)
        if (dense.isEmpty()) {
            return sparse.take(topK).map { it.toVectorHit() }
        }

        // key = source + ":" + documentId，避免文本碰撞
        val densePairs = dense.map {
            keyOf(it.externalId, it.source) to it
        }
        val sparsePairs = sparse.map {
            keyOf(it.documentId, it.source) to it
        }

        val fusedKeys = SparseRrf.fuse(
            rankedLists = listOf(
                densePairs.map { it.first },
                sparsePairs.map { it.first }
            ),
            k = SparseRrf.DEFAULT_K,
            topK = topK
        )

        val denseByKey = densePairs.associate { it.first to it.second }
        val sparseByKey = sparsePairs.associate { it.first to it.second }

        return fusedKeys.mapNotNull { key ->
            denseByKey[key] ?: sparseByKey[key]?.toVectorHit()
        }
    }

    /**
     * 删除向量与稀疏条目。
     */
    suspend fun delete(externalId: Long, source: String) {
        vectorStore.deleteByExternal(externalId, source)
        runCatching { sparseStore.delete(externalId, source) }
    }

    /**
     * 端到端延迟探测：embed + search（空库也可）。
     * @return 耗时毫秒
     */
    suspend fun measureLatencyMs(sampleText: String = "兰心知识库向量管道延迟测试"): Long {
        var total = 0L
        total += measureTimeMillis {
            val emb = embeddingService.embed(sampleText)
            vectorStore.search(emb, topK = 5)
        }
        Log.i(TAG, "vector pipeline latency=${total}ms ready=${embeddingService.isReady}")
        return total
    }

    private fun SparseSearchResult.toVectorHit(): VectorHit = VectorHit(
        id = documentId,
        externalId = documentId,
        source = source,
        textPreview = text,
        score = score.toFloat()
    )

    companion object {
        private const val TAG = "VectorPipeline"

        fun keyOf(documentId: Long, source: String): String = "$source:$documentId"
    }
}
