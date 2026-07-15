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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

/**
 * FTS 内容表（普通表，供 FTS4 外部内容引用）。
 * Room 的 FTS5 注解在部分版本支持不完善，采用 FTS4 unicode61 兼容方案。
 */
@Entity(tableName = "knowledge_fts_content")
data class SparseFtsContentEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long = 0,
    /** 业务 documentId（memory id / knowledge externalId） */
    @ColumnInfo(name = "document_id")
    val documentId: Long,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "body")
    val body: String,
    @ColumnInfo(name = "payload")
    val payload: String? = null
)

/**
 * FTS4 虚拟表，content 指向 [SparseFtsContentEntity]。
 * 使用 unicode61 tokenizer 以更好支持 CJK 字符匹配。
 */
@Fts4(
    contentEntity = SparseFtsContentEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61
)
@Entity(tableName = "knowledge_fts")
data class SparseFtsEntity(
    @ColumnInfo(name = "body")
    val body: String
)
