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

package com.lanxin.android.builtin.knowledge.data

import android.content.Context
import android.util.Log
import com.lanxin.android.builtin.knowledge.domain.VectorHit
import com.lanxin.android.builtin.knowledge.domain.VectorStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import io.objectbox.query.QueryBuilder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * ObjectBox VectorDB 实现。
 *
 * 独立库文件 `objectbox-vectors`，不与 Room 混用。
 * HNSW 余弦近邻检索；score = 1 - distance（越大越相似）。
 */
@Singleton
class ObjectBoxVectorStore @Inject constructor(
    @ApplicationContext private val context: Context
) : VectorStore {

    private val mutex = Mutex()

    @Volatile
    private var store: BoxStore? = null

    private fun box(): Box<VectorItem> {
        val s = store ?: synchronized(this) {
            store ?: MyObjectBox.builder()
                .androidContext(context.applicationContext)
                .name(DB_NAME)
                .build()
                .also {
                    store = it
                    Log.i(TAG, "ObjectBox VectorStore 已打开: $DB_NAME")
                }
        }
        return s.boxFor()
    }

    override suspend fun upsert(
        externalId: Long,
        source: String,
        embedding: FloatArray,
        textPreview: String
    ): Long = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(embedding.size == EmbeddingConstants.DIMENSIONS) {
                "embedding 维度 ${embedding.size} != ${EmbeddingConstants.DIMENSIONS}"
            }
            val b = box()
            val existing = b.query(
                VectorItem_.externalId.equal(externalId)
                    .and(VectorItem_.source.equal(source))
            ).build().findFirst()

            val item = existing?.also {
                it.embedding = embedding
                it.textPreview = textPreview.take(PREVIEW_MAX)
            } ?: VectorItem(
                externalId = externalId,
                source = source,
                textPreview = textPreview.take(PREVIEW_MAX),
                embedding = embedding
            )
            b.put(item)
        }
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        mutex.withLock { box().remove(id) }
        Unit
    }

    override suspend fun deleteByExternal(externalId: Long, source: String) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val b = box()
                val found = b.query(
                    VectorItem_.externalId.equal(externalId)
                        .and(VectorItem_.source.equal(source))
                ).build().find()
                if (found.isNotEmpty()) {
                    b.remove(found)
                }
            }
            Unit
        }

    override suspend fun deleteBySource(source: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val b = box()
            val found = b.query(VectorItem_.source.equal(source)).build().find()
            if (found.isNotEmpty()) {
                b.remove(found)
            }
        }
        Unit
    }

    /**
     * ObjectBox 仅 dense；稀疏检索由 [com.lanxin.android.builtin.knowledge.data.sparse.SparseStore] 负责。
     */
    override suspend fun searchSparse(
        query: String,
        topK: Int,
        source: String?
    ): List<VectorHit> = emptyList()

    override suspend fun search(
        query: FloatArray,
        topK: Int,
        source: String?
    ): List<VectorHit> = withContext(Dispatchers.IO) {
        require(query.size == EmbeddingConstants.DIMENSIONS) {
            "query 维度 ${query.size} != ${EmbeddingConstants.DIMENSIONS}"
        }
        val k = topK.coerceIn(1, 100)
        val b = box()

        // nearestNeighbors 返回按距离升序（越近越好）
        val condition = VectorItem_.embedding.nearestNeighbors(query, k)
        val qb: QueryBuilder<VectorItem> = b.query(condition)
        if (source != null) {
            // ObjectBox 向量查询与过滤：先近邻再内存过滤（P0 规模可接受）
            // 带 source 时多取一些再过滤
        }
        qb.build().use { q ->
            val results = q.findWithScores()
            results
                .asSequence()
                .map { scored ->
                    val item = scored.get()
                    val distance = scored.score.toFloat()
                    // COSINE distance: 0=identical, 2=opposite → score = 1 - distance/2 映射到 [0,1]
                    // ObjectBox COSINE 距离通常为 1 - cosine_similarity ∈ [0, 2]
                    val similarity = (1f - distance).coerceIn(-1f, 1f)
                    VectorHit(
                        id = item.id,
                        externalId = item.externalId,
                        source = item.source,
                        textPreview = item.textPreview,
                        score = similarity
                    )
                }
                .filter { source == null || it.source == source }
                .take(k)
                .toList()
        }
    }

    override suspend fun count(source: String?): Long = withContext(Dispatchers.IO) {
        val b = box()
        if (source == null) {
            b.count()
        } else {
            b.query(VectorItem_.source.equal(source)).build().use { it.count() }
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock { box().removeAll() }
        Unit
    }

    fun close() {
        store?.close()
        store = null
    }

    companion object {
        private const val TAG = "ObjectBoxVectorStore"
        private const val DB_NAME = "objectbox-vectors"
        private const val PREVIEW_MAX = 200
    }
}
