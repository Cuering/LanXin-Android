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

package com.lanxin.android.builtin.knowledge

import com.lanxin.android.builtin.knowledge.domain.EmbeddingService
import com.lanxin.android.builtin.knowledge.domain.TextChunker
import com.lanxin.android.builtin.knowledge.domain.VectorPipeline
import com.lanxin.android.builtin.knowledge.domain.VectorSource
import com.lanxin.android.builtin.knowledge.domain.VectorStore
import com.lanxin.android.plugin.LanXinPlugin
import com.lanxin.android.plugin.PluginContext
import com.lanxin.android.plugin.ToolDef
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 知识库插件（Phase 3 P0：向量管道打通）。
 *
 * MCP 工具：
 * - kb_status：查询状态/容量
 * - kb_embed：将文本转换为 384 维向量（返回头 8 个元素和 L2 范数）
 * - kb_index：将文本向量化并写入 ObjectBox（source 默认 knowledge）
 * - kb_search：语义检索 Top-K
 * - kb_latency：测量 embed+search 端到端延迟
 * - kb_chunk：预览文本滑动窗口分段结果（不入库）
 */
@Singleton
class KnowledgePlugin @Inject constructor(
    private val embeddingService: EmbeddingService,
    private val vectorStore: VectorStore,
    private val pipeline: VectorPipeline,
    private val textChunker: TextChunker
) : LanXinPlugin {

    override val id = "lanxin.knowledge"
    override val name = "知识库"
    override val version = "0.2.0"
    override val description = "端侧向量检索 + 文档导入分段（GTE-small + ObjectBox）"

    override suspend fun onLoad(context: PluginContext) {
        // 后台预热，不阻塞插件加载
        runCatching { pipeline.warmUp() }

        context.registerTool(
            ToolDef(
                name = "kb_status",
                description = "知识库/向量管道状态：模型是否就绪、容量、阶段",
                parameters = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject { })
                },
                handler = {
                    buildJsonObject {
                        put("ok", true)
                        put("embedding_ready", embeddingService.isReady)
                        put("dimensions", embeddingService.dimensions)
                        put("vector_count", vectorStore.count())
                        put("phase", "P2")
                        put("chunk_window", TextChunker.DEFAULT_WINDOW)
                        put("chunk_overlap", TextChunker.DEFAULT_OVERLAP)
                    }
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "kb_embed",
                description = "将文本转换为 384 维向量（返回头 8 个元素和 L2 范数）",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("text", stringProp("需要转换的文本"))
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("text")) })
                },
                handler = { args ->
                    runCatching {
                        val text = args.string("text") ?: error("text 缺失")
                        val emb = embeddingService.embed(text)
                        var norm = 0.0
                        for (x in emb) norm += x * x
                        norm = kotlin.math.sqrt(norm)
                        buildJsonObject {
                            put("ok", true)
                            put("dimensions", emb.size)
                            put("l2_norm", norm)
                            put(
                                "preview",
                                buildJsonArray {
                                    emb.take(8).forEach { add(JsonPrimitive(it)) }
                                }
                            )
                        }
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "kb_index",
                description = "将文本向量化并写入 ObjectBox（source 默认 knowledge）",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("text", stringProp("要索引的文本"))
                            put("external_id", intProp("业务侧 ID，可选自动生成时间戳"))
                            put("source", stringProp("来源：memory / knowledge，默认 knowledge"))
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("text")) })
                },
                handler = { args ->
                    runCatching {
                        val text = args.string("text") ?: error("text 缺失")
                        val externalId = args.long("external_id")
                            ?: (System.currentTimeMillis() % Int.MAX_VALUE)
                        val source = args.string("source") ?: VectorSource.KNOWLEDGE
                        val id = pipeline.index(externalId, source, text)
                        buildJsonObject {
                            put("ok", id > 0)
                            put("vector_id", id)
                            put("external_id", externalId)
                            put("source", source)
                        }
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "kb_search",
                description = "语义检索 Top-K",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("query", stringProp("搜索文本"))
                            put("top_k", intProp("返回数量，默认 5，上限 50"))
                            put("source", stringProp("可选限定来源 memory/knowledge"))
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("query")) })
                },
                handler = { args ->
                    runCatching {
                        val query = args.string("query") ?: error("query 缺失")
                        val topK = (args.int("top_k") ?: 5).coerceIn(1, 50)
                        val source = args.string("source")
                        val hits = pipeline.search(query, topK, source)
                        buildJsonObject {
                            put("ok", true)
                            put("count", hits.size)
                            put(
                                "hits",
                                buildJsonArray {
                                    hits.forEach { h ->
                                        add(
                                            buildJsonObject {
                                                put("id", h.id)
                                                put("external_id", h.externalId)
                                                put("source", h.source)
                                                put("score", h.score)
                                                put("text_preview", h.textPreview)
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "kb_latency",
                description = "测量 embed+search 端到端延迟（空库也可），目标 < 50ms",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("text", stringProp("测试文本，可选默认"))
                        }
                    )
                },
                handler = { args ->
                    runCatching {
                        val text = args.string("text") ?: "知识库向量管道延迟测试"
                        val ms = pipeline.measureLatencyMs(text)
                        buildJsonObject {
                            put("ok", true)
                            put("latency_ms", ms)
                            put("target_ms", 50)
                            put("pass", ms < 50)
                            put("embedding_ready", embeddingService.isReady)
                        }
                    }.toToolResult()
                }
            )
        )

        context.registerTool(
            ToolDef(
                name = "kb_chunk",
                description = "预览文本滑动窗口分段（默认 512/50），不入库",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put("text", stringProp("待分段文本"))
                            put("window", intProp("窗口 token，默认 512"))
                            put("overlap", intProp("重叠 token，默认 50"))
                        }
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("text")) })
                },
                handler = { args ->
                    runCatching {
                        val text = args.string("text") ?: error("text 缺失")
                        val window = args.int("window") ?: TextChunker.DEFAULT_WINDOW
                        val overlap = args.int("overlap") ?: TextChunker.DEFAULT_OVERLAP
                        val chunks = textChunker.chunk(text, window, overlap)
                        buildJsonObject {
                            put("ok", true)
                            put("count", chunks.size)
                            put("window", window)
                            put("overlap", overlap)
                            put(
                                "chunks",
                                buildJsonArray {
                                    chunks.take(20).forEach { c ->
                                        add(
                                            buildJsonObject {
                                                put("index", c.index)
                                                put("start", c.startOffset)
                                                put("end", c.endOffset)
                                                put(
                                                    "preview",
                                                    c.text.take(120)
                                                )
                                                put("length", c.text.length)
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    }.toToolResult()
                }
            )
        )
    }

    private fun Result<kotlinx.serialization.json.JsonObject>.toToolResult() =
        fold(
            onSuccess = { it },
            onFailure = { e ->
                buildJsonObject {
                    put("ok", false)
                    put("error", e.message ?: e.toString())
                }
            }
        )

    private fun kotlinx.serialization.json.JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun kotlinx.serialization.json.JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

    private fun kotlinx.serialization.json.JsonObject.long(key: String): Long? =
        this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

    private fun stringProp(desc: String) = buildJsonObject {
        put("type", "string")
        put("description", desc)
    }

    private fun intProp(desc: String) = buildJsonObject {
        put("type", "integer")
        put("description", desc)
    }
}
