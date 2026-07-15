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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * FTS content + 虚拟表 DAO。
 *
 * 注意：Room `@Fts4` 对应 SQLite FTS4，无 `bm25()`（FTS5 专属）。
 * 排序由上层 [SparseStore] 的内存 BM25 负责；FTS 仅做候选召回。
 */
@Dao
interface SparseFtsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContent(entity: SparseFtsContentEntity): Long

    @Query("DELETE FROM knowledge_fts_content WHERE document_id = :documentId AND source = :source")
    suspend fun deleteByDocument(documentId: Long, source: String)

    @Query("DELETE FROM knowledge_fts_content WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("DELETE FROM knowledge_fts_content")
    suspend fun deleteAll()

    /**
     * FTS MATCH 候选召回（不排序打分）。
     */
    @Query(
        """
        SELECT c.document_id AS documentId,
               c.source AS source,
               c.body AS body,
               c.payload AS payload
        FROM knowledge_fts
        JOIN knowledge_fts_content c ON c.rowid = knowledge_fts.rowid
        WHERE knowledge_fts MATCH :matchQuery
        LIMIT :limit
        """
    )
    suspend fun searchFts(matchQuery: String, limit: Int): List<SparseFtsHit>

    @Query(
        """
        SELECT c.document_id AS documentId,
               c.source AS source,
               c.body AS body,
               c.payload AS payload
        FROM knowledge_fts
        JOIN knowledge_fts_content c ON c.rowid = knowledge_fts.rowid
        WHERE knowledge_fts MATCH :matchQuery
          AND c.source = :source
        LIMIT :limit
        """
    )
    suspend fun searchFtsBySource(
        matchQuery: String,
        source: String,
        limit: Int
    ): List<SparseFtsHit>

    @Query("SELECT COUNT(*) FROM knowledge_fts_content")
    suspend fun count(): Long

    @Query("SELECT COUNT(*) FROM knowledge_fts_content WHERE source = :source")
    suspend fun countBySource(source: String): Long

    @Query("SELECT * FROM knowledge_fts_content")
    suspend fun getAllContent(): List<SparseFtsContentEntity>

    @Query("SELECT * FROM knowledge_fts_content WHERE source = :source")
    suspend fun getContentBySource(source: String): List<SparseFtsContentEntity>

    @Query(
        "SELECT * FROM knowledge_fts_content WHERE document_id = :documentId AND source = :source LIMIT 1"
    )
    suspend fun getByDocument(documentId: Long, source: String): SparseFtsContentEntity?
}

/**
 * FTS 查询投影（无 rank，由 BM25 重排）。
 */
data class SparseFtsHit(
    val documentId: Long,
    val source: String,
    val body: String,
    val payload: String?
)
