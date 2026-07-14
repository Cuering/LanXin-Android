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
import com.lanxin.android.builtin.knowledge.domain.EmbeddingService
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * GTE-small int8 ONNX 向量化实现。
 *
 * 模型加载优先级：
 * 1. `filesDir/models/gte-small/model_int8.onnx`（可运行时下载）
 * 2. `assets/models/gte-small/model_int8.onnx`
 *
 * 输出：mean-pooling（按 attention_mask）+ L2 归一化，384 维。
 */
@Singleton
class OnnxEmbeddingService @Inject constructor(
    @ApplicationContext private val context: Context
) : EmbeddingService {

    override val dimensions: Int = EmbeddingConstants.DIMENSIONS

    @Volatile
    private var session: OrtSession? = null

    @Volatile
    private var tokenizer: BertTokenizer? = null

    @Volatile
    private var env: OrtEnvironment? = null

    private val mutex = Mutex()

    override val isReady: Boolean
        get() = session != null && tokenizer != null

    /**
     * 懒加载模型与 tokenizer。可在 Application 启动时预热。
     * @return true 加载成功
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (session != null && tokenizer != null) return@withLock true
            runCatching {
                val modelBytes = loadModelBytes()
                    ?: error("未找到 GTE-small 模型，请将 model_int8.onnx 放到 assets 或 filesDir")
                val tok = loadTokenizer()
                    ?: error("未找到 tokenizer.json")

                val ortEnv = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }
                val ortSession = ortEnv.createSession(modelBytes, opts)

                env = ortEnv
                session = ortSession
                tokenizer = tok
                Log.i(TAG, "GTE-small ONNX 加载成功, modelBytes=${modelBytes.size}")
                true
            }.onFailure {
                Log.e(TAG, "GTE-small 加载失败: ${it.message}", it)
            }.getOrDefault(false)
        }
    }

    override suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        if (!isReady) {
            val ok = initialize()
            if (!ok) error("EmbeddingService 未就绪：模型或 tokenizer 缺失")
        }
        val tok = tokenizer!!
        val ortSession = session!!
        val ortEnv = env!!

        val encoding = tok.encode(text.trim().ifEmpty { " " })
        val inputNames = ortSession.inputNames

        val inputIds = OnnxTensor.createTensor(
            ortEnv,
            LongBuffer.wrap(encoding.inputIds),
            longArrayOf(1, encoding.inputIds.size.toLong())
        )
        val attentionMask = OnnxTensor.createTensor(
            ortEnv,
            LongBuffer.wrap(encoding.attentionMask),
            longArrayOf(1, encoding.attentionMask.size.toLong())
        )
        val tokenTypeIds = OnnxTensor.createTensor(
            ortEnv,
            LongBuffer.wrap(encoding.tokenTypeIds),
            longArrayOf(1, encoding.tokenTypeIds.size.toLong())
        )

        try {
            val feeds = LinkedHashMap<String, OnnxTensor>()
            // 兼容 input_ids / attention_mask / token_type_ids 命名
            for (name in inputNames) {
                when {
                    name.contains("input_ids", ignoreCase = true) || name == "input_ids" ->
                        feeds[name] = inputIds
                    name.contains("attention_mask", ignoreCase = true) || name == "attention_mask" ->
                        feeds[name] = attentionMask
                    name.contains("token_type", ignoreCase = true) || name == "token_type_ids" ->
                        feeds[name] = tokenTypeIds
                }
            }
            // 若模型只有 2 输入，不传 token_type_ids
            if (feeds.isEmpty()) {
                // 按字母序/插入序兜底
                val ordered = inputNames.toList()
                if (ordered.isNotEmpty()) feeds[ordered[0]] = inputIds
                if (ordered.size > 1) feeds[ordered[1]] = attentionMask
                if (ordered.size > 2) feeds[ordered[2]] = tokenTypeIds
            }

            ortSession.run(feeds).use { result ->
                val first = result[0].value
                meanPoolAndNormalize(first, encoding.attentionMask)
            }
        } finally {
            inputIds.close()
            attentionMask.close()
            tokenTypeIds.close()
        }
    }

    /**
     * 对 last_hidden_state [1, seq, hidden] 做 mask mean-pooling + L2 normalize。
     */
    @Suppress("UNCHECKED_CAST")
    private fun meanPoolAndNormalize(value: Any, attentionMask: LongArray): FloatArray {
        val hidden: Array<FloatArray> = when (value) {
            is Array<*> -> {
                // [batch, seq, hidden]
                val batch0 = value[0]
                when (batch0) {
                    is Array<*> -> batch0 as Array<FloatArray>
                    else -> error("不支持的 ONNX 输出类型: ${batch0?.javaClass}")
                }
            }
            is FloatArray -> {
                // 扁平输出，按 384 拆
                require(value.size % dimensions == 0) { "输出长度 ${value.size} 不能被 $dimensions 整除" }
                val seq = value.size / dimensions
                Array(seq) { i ->
                    FloatArray(dimensions) { d -> value[i * dimensions + d] }
                }
            }
            else -> error("不支持的 ONNX 输出类型: ${value.javaClass}")
        }

        val sum = FloatArray(dimensions)
        var count = 0f
        val seqLen = minOf(hidden.size, attentionMask.size)
        for (i in 0 until seqLen) {
            if (attentionMask[i] == 0L) continue
            val row = hidden[i]
            for (d in 0 until dimensions) {
                sum[d] += row[d]
            }
            count += 1f
        }
        if (count == 0f) count = 1f
        for (d in 0 until dimensions) {
            sum[d] /= count
        }
        return l2Normalize(sum)
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        var norm = 0.0
        for (x in v) norm += x * x
        norm = sqrt(norm)
        if (norm < 1e-12) return v
        val out = FloatArray(v.size)
        for (i in v.indices) out[i] = (v[i] / norm).toFloat()
        return out
    }

    private fun loadModelBytes(): ByteArray? {
        val local = File(context.filesDir, "${EmbeddingConstants.MODEL_DIR}/${EmbeddingConstants.MODEL_FILE}")
        if (local.exists() && local.length() > 0) {
            return local.readBytes()
        }
        return runCatching {
            context.assets.open(EmbeddingConstants.MODEL_ASSET_PATH).use { it.readBytes() }
        }.getOrNull()
    }

    private fun loadTokenizer(): BertTokenizer? {
        val local = File(context.filesDir, "${EmbeddingConstants.MODEL_DIR}/${EmbeddingConstants.TOKENIZER_FILE}")
        if (local.exists() && local.length() > 0) {
            return local.inputStream().use { BertTokenizer.fromInputStream(it) }
        }
        return runCatching {
            context.assets.open(EmbeddingConstants.TOKENIZER_ASSET_PATH).use {
                BertTokenizer.fromInputStream(it)
            }
        }.getOrNull()
    }

    fun close() {
        session?.close()
        session = null
        tokenizer = null
        // OrtEnvironment 全局单例，不关闭
    }

    companion object {
        private const val TAG = "OnnxEmbeddingService"
    }
}
