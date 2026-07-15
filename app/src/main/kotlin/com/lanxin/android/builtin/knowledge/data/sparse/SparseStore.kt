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

package com.lanxin.android.builtin.knowledge.data.sparse

import android.util.Log
import com.lanxin.android.builtin.knowledge.domain.sparse.Bm25Index
import com.lanxin.android.builtin.knowledge.domain.sparse.SparseIndexItem
import com.lanxin.android.builtin.knowledge.domain.sparse.SparseSearchResult
import com.lanxin.android.builtin.knowledge.domain.sparse.Tokenizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 稀疏检索存储：Room FTS 持久化 + 内存 [Bm25Index] 打分。
 *
 * - upsert/delete 写 FTS content 表，并标记内存索引脏
 * - search：优先加载/刷新 BM25 后打分；FTS MATCH 作为候选过滤（可选）
 * - 索引不可用时由调用方 fallback LIKE
 */
@Singleton
class SparseStore @Inject constructor(
    private val dao: SparseFtsDao
) {
    private val mutex = Mutex()
    private val bm25 = Bm25Index()

    @Volatile
    private var bm25Dirty: Boolean = true

    /**
     * 写入或覆盖一条稀疏文档。
     */
    suspend fun upsert(item: SparseIndexItem): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            dao.deleteByDocument(item.documentId, item.source)
            dao.upsertContent(
                SparseFtsContentEntity(
                    rowId = contentRowId(item.documentId, item.source),
                    documentId = item.documentId,
                    source = item.source,
                    body = item.text,
                    payload = item.payload
                )
            )
            bm25Dirty = true
        }
    }

    /**
     * 批量 upsert。
     */
    suspend fun upsertAll(items: List<SparseIndexItem>): Unit = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        mutex.withLock {
            for (item in items) {
                dao.deleteByDocument(item.documentId, item.source)
                dao.upsertContent(
                    SparseFtsContentEntity(
                        rowId = contentRowId(item.documentId, item.source),
                        documentId = item.documentId,
                        source = item.source,
                        body = item.text,
                        payload = item.payload
                    )
                )
            }
            bm25Dirty = true
        }
    }

    suspend fun delete(documentId: Long, source: String): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            dao.deleteByDocument(documentId, source)
            bm25Dirty = true
        }
    }

    suspend fun deleteBySource(source: String): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            dao.deleteBySource(source)
            bm25Dirty = true
        }
    }

    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            dao.deleteAll()
            bm25.clear()
            bm25Dirty = false
        }
    }

    /**
     * 稀疏检索：BM25 打分排序。
     * FTS 失败不影响（全量 BM25）。
     */
    suspend fun search(
        query: String,
        topK: Int = 10,
        source: String? = null
    ): List<SparseSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank() || topK <= 0) return@withContext emptyList()

        // 尝试 FTS 候选；失败则全量 BM25
        val candidates = tryFtsCandidates(query, topK * 4, source)
        if (candidates.isNotEmpty()) {
            val tmp = Bm25Index()
            tmp.index(candidates)
            return@withContext tmp.search(query, topK)
        }

        ensureBm25Loaded()
        val results = bm25.search(query, if (source == null) topK else topK * 4)
        if (source == null) {
            results
        } else {
            results.filter { it.source == source }.take(topK)
        }
    }

    suspend fun count(source: String? = null): Long = withContext(Dispatchers.IO) {
        if (source == null) dao.count() else dao.countBySource(source)
    }

    /**
     * 用外部 items 直接刷新内存 BM25（不经 FTS），供 Memory 注入路径使用。
     */
    fun indexInMemory(items: List<SparseIndexItem>) {
        bm25.index(items)
        bm25Dirty = false
    }

    /**
     * 仅内存 BM25 搜索（调用方保证已 index）。
     */
    fun searchInMemory(query: String, topK: Int): List<SparseSearchResult> =
        bm25.search(query, topK)

    private suspend fun tryFtsCandidates(
        query: String,
        limit: Int,
        source: String?
    ): List<SparseIndexItem> {
        return try {
            val matchQuery = buildMatchQuery(query)
            if (matchQuery.isBlank()) return emptyList()
            val hits = if (source == null) {
                dao.searchFts(matchQuery, limit)
            } else {
                dao.searchFtsBySource(matchQuery, source, limit)
            }
            hits.map {
                SparseIndexItem(
                    documentId = it.documentId,
                    source = it.source,
                    text = it.body,
                    payload = it.payload
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "FTS search failed, fallback BM25: ${e.message}")
            emptyList()
        }
    }

    private suspend fun ensureBm25Loaded() {
        if (!bm25Dirty && !bm25.isEmpty) return
        mutex.withLock {
            if (!bm25Dirty && !bm25.isEmpty) return
            val content = dao.getAllContent()
            val items = content.map {
                SparseIndexItem(
                    documentId = it.documentId,
                    source = it.source,
                    text = it.body,
                    payload = it.payload
                )
            }
            bm25.index(items)
            bm25Dirty = false
        }
    }

    /**
     * 将自然语言 query 转为 FTS MATCH 表达式。
     */
    internal fun buildMatchQuery(query: String): String {
        val tokens = Tokenizer.tokenizeUnique(query)
            .map { sanitizeToken(it) }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return ""
        return tokens.joinToString(" OR ") { "\"$it\"" }
    }

    private fun sanitizeToken(token: String): String =
        token.replace(Regex("[\"'*:(){}\\[\\]^~]"), "")

    /**
     * 稳定 rowid：documentId 与 source hash 混合为正 long。
     */
    private fun contentRowId(documentId: Long, source: String): Long {
        val h = source.hashCode().toLong() and 0x7fffffffL
        val mixed = (documentId xor (h shl 32)) and Long.MAX_VALUE
        return if (mixed == 0L) 1L else mixed
    }

    companion object {
        private const val TAG = "SparseStore"
    }
}
