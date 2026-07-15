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
 * Reciprocal Rank Fusion（通用文本 key 版）。
 *
 * score += 1/(k + rank)，多路按 key 对齐后取 topK。
 */
object SparseRrf {

    const val DEFAULT_K = 60

    /**
     * 多路排名列表融合；每路为已排序的 key 列表（越前越好）。
     *
     * @return 融合后的 key 列表（最多 topK）
     */
    fun fuse(
        rankedLists: List<List<String>>,
        k: Int = DEFAULT_K,
        topK: Int = 10
    ): List<String> {
        if (rankedLists.isEmpty() || topK <= 0) return emptyList()
        val scores = mutableMapOf<String, Double>()
        for (list in rankedLists) {
            list.forEachIndexed { index, key ->
                if (key.isBlank()) return@forEachIndexed
                scores[key] = scores.getOrDefault(key, 0.0) + 1.0 / (k + index + 1)
            }
        }
        return scores.entries
            .sortedByDescending { it.value }
            .take(topK)
            .map { it.key }
    }

    /**
     * 两路 RRF，并带回第一路/第二路的展示文本映射。
     */
    fun fuseWithPayload(
        primary: List<Pair<String, String>>,
        secondary: List<Pair<String, String>>,
        k: Int = DEFAULT_K,
        topK: Int = 10
    ): List<Pair<String, String>> {
        val textByKey = LinkedHashMap<String, String>()
        primary.forEach { (key, text) -> textByKey.putIfAbsent(key, text) }
        secondary.forEach { (key, text) -> textByKey.putIfAbsent(key, text) }
        val keys = fuse(
            rankedLists = listOf(
                primary.map { it.first },
                secondary.map { it.first }
            ),
            k = k,
            topK = topK
        )
        return keys.mapNotNull { key ->
            textByKey[key]?.let { key to it }
        }
    }
}
