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

import kotlin.math.ln

/**
 * 纯 Kotlin BM25Okapi 倒排索引。
 *
 * 线程安全：读多写少场景下，[index] 替换整份快照；[search] 读当前快照。
 * 支持 cache/reuse：相同 items 指纹时 [index] 可跳过重建。
 */
class Bm25Index(
    private val k1: Double = DEFAULT_K1,
    private val b: Double = DEFAULT_B
) {

    @Volatile
    private var snapshot: Snapshot = Snapshot.EMPTY

    /** 当前已索引文档数。 */
    val size: Int get() = snapshot.docs.size

    /** 是否为空。 */
    val isEmpty: Boolean get() = snapshot.docs.isEmpty()

    /**
     * 构建 / 重建倒排索引。
     * 若 items 指纹与当前一致则复用，不重建。
     */
    fun index(items: List<SparseIndexItem>) {
        val fingerprint = fingerprintOf(items)
        val current = snapshot
        if (current.fingerprint == fingerprint && current.docs.isNotEmpty()) {
            return
        }
        if (items.isEmpty()) {
            snapshot = Snapshot.EMPTY
            return
        }

        val docs = ArrayList<DocEntry>(items.size)
        val df = HashMap<String, Int>()
        var totalLen = 0

        for (item in items) {
            val tokens = Tokenizer.tokenize(item.text)
            val tf = HashMap<String, Int>()
            for (t in tokens) {
                tf[t] = (tf[t] ?: 0) + 1
            }
            for (term in tf.keys) {
                df[term] = (df[term] ?: 0) + 1
            }
            totalLen += tokens.size
            docs.add(
                DocEntry(
                    item = item,
                    tf = tf,
                    length = tokens.size
                )
            )
        }

        val n = docs.size
        val avgdl = if (n == 0) 0.0 else totalLen.toDouble() / n
        val idf = HashMap<String, Double>(df.size)
        for ((term, docFreq) in df) {
            // BM25+ style smooth IDF: ln((N - n + 0.5)/(n + 0.5) + 1)
            idf[term] = ln((n - docFreq + 0.5) / (docFreq + 0.5) + 1.0)
        }

        snapshot = Snapshot(
            docs = docs,
            idf = idf,
            avgdl = avgdl,
            fingerprint = fingerprint
        )
    }

    /**
     * BM25 打分检索，按分数降序取 topK。
     */
    fun search(query: String, topK: Int = 10): List<SparseSearchResult> {
        if (query.isBlank() || topK <= 0) return emptyList()
        val snap = snapshot
        if (snap.docs.isEmpty()) return emptyList()

        val queryTerms = Tokenizer.tokenizeUnique(query)
        if (queryTerms.isEmpty()) return emptyList()

        val scores = DoubleArray(snap.docs.size)
        for (qi in queryTerms) {
            val idf = snap.idf[qi] ?: continue
            for (i in snap.docs.indices) {
                val doc = snap.docs[i]
                val freq = doc.tf[qi] ?: continue
                val denom = freq + k1 * (1.0 - b + b * doc.length / snap.avgdl.coerceAtLeast(1e-9))
                scores[i] += idf * (freq * (k1 + 1.0)) / denom
            }
        }

        val k = topK.coerceAtMost(snap.docs.size)
        // partial top-k via indexed sort
        val indices = scores.indices
            .filter { scores[it] > 0.0 }
            .sortedByDescending { scores[it] }
            .take(k)

        return indices.map { i ->
            val doc = snap.docs[i]
            SparseSearchResult(
                documentId = doc.item.documentId,
                source = doc.item.source,
                text = doc.item.text,
                score = scores[i],
                payload = doc.item.payload
            )
        }
    }

    /** 清空索引。 */
    fun clear() {
        snapshot = Snapshot.EMPTY
    }

    private data class DocEntry(
        val item: SparseIndexItem,
        val tf: Map<String, Int>,
        val length: Int
    )

    private data class Snapshot(
        val docs: List<DocEntry>,
        val idf: Map<String, Double>,
        val avgdl: Double,
        val fingerprint: Long
    ) {
        companion object {
            val EMPTY = Snapshot(emptyList(), emptyMap(), 0.0, 0L)
        }
    }

    companion object {
        const val DEFAULT_K1 = 1.5
        const val DEFAULT_B = 0.75

        /**
         * 轻量指纹：size + documentId xor + text/source 内容 hash。
         * 必须纳入 text 内容本身，否则同 id/同长度文本改写会误判为 unchanged。
         * 使用简单 31 乘数避免 signed Long 溢出不稳定。
         */
        fun fingerprintOf(items: List<SparseIndexItem>): Long {
            if (items.isEmpty()) return 0L
            var h = items.size.toLong() * 31
            for (item in items) {
                h = h xor item.documentId
                h = h * 31 + item.text.hashCode()
                h = h * 31 + item.source.hashCode()
                val payload = item.payload
                if (payload != null) {
                    h = h * 31 + payload.hashCode()
                }
            }
            return h
        }
    }
}
