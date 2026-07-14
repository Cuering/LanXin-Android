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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

/**
 * P0 向量管道门面：embed + upsert + search。
 * 供后续 MemoryInjector / knowledge 导入使用。
 */
@Singleton
class VectorPipeline @Inject constructor(
    private val embeddingService: EmbeddingService,
    private val vectorStore: VectorStore
) {
    /**
     * 预热模型（Application 启动时调用）。
     */
    suspend fun warmUp(): Boolean {
        val svc = embeddingService as? OnnxEmbeddingService
        return svc?.initialize() ?: embeddingService.isReady
    }

    /**
     * 文本 → 向量 → 入库。
     * @return 向量库 id；embed 失败时返回 -1
     */
    suspend fun index(
        externalId: Long,
        source: String,
        text: String
    ): Long {
        if (text.isBlank()) return -1L
        val embedding = embeddingService.embed(text)
        return vectorStore.upsert(
            externalId = externalId,
            source = source,
            embedding = embedding,
            textPreview = text
        )
    }

    /**
     * 语义检索。
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

    companion object {
        private const val TAG = "VectorPipeline"
    }
}
