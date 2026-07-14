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

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.VectorDistanceType

/**
 * ObjectBox 向量条目。
 *
 * - [externalId] + [source] 关联 Room 业务表（记忆 / 知识库 chunk）
 * - [embedding] 384 维 GTE-small 向量，HNSW + 余弦距离
 */
@Entity
data class VectorItem(
    @Id var id: Long = 0,
    /** Room 侧业务主键 */
    @Index var externalId: Long = 0,
    /** memory / knowledge 等 */
    @Index var source: String = "",
    /** 便于调试的文本预览（截断） */
    var textPreview: String = "",
    @HnswIndex(
        dimensions = EmbeddingConstants.DIMENSIONS.toLong(),
        distanceType = VectorDistanceType.COSINE
    )
    var embedding: FloatArray = floatArrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VectorItem) return false
        return id == other.id &&
            externalId == other.externalId &&
            source == other.source &&
            textPreview == other.textPreview &&
            embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + externalId.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + textPreview.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

object EmbeddingConstants {
    /** GTE-small 输出维度 */
    const val DIMENSIONS = 384

    /** BERT / GTE 最大序列长度 */
    const val MAX_SEQ_LEN = 512

    /** assets 内模型相对路径 */
    const val MODEL_ASSET_PATH = "models/gte-small/model_int8.onnx"

    /** assets 内 tokenizer 相对路径 */
    const val TOKENIZER_ASSET_PATH = "models/gte-small/tokenizer.json"

    /** 本地 filesDir 子目录（支持运行时下载覆盖 assets） */
    const val MODEL_DIR = "models/gte-small"
    const val MODEL_FILE = "model_int8.onnx"
    const val TOKENIZER_FILE = "tokenizer.json"
}
