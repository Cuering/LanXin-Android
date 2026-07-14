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
 * 文本嵌入服务接口。
 * GTE-small ONNX 封装，384 维输出。
 */
interface EmbeddingService {
    val dimensions: Int
    val isReady: Boolean

    /**
     * 将文本转为 384 维归一化向量。
     * @param text 输入文本
     * @return [dimensions] 维 float 数组，L2 归一化
     */
    suspend fun embed(text: String): FloatArray
}
