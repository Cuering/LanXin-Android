# 知识库模块 (builtin/knowledge)

> 端侧向量检索 + BM25 稀疏检索 + 文档导入

## 架构

```
用户输入 / 文档
  │
  ├─ 导入流水线 (P2)
  │     SAF 选文件/文件夹 → DocumentParser (txt/md/pdf)
  │       ├─ Markdown → MarkdownChunker 标题感知分块 (P5b)
  │       └─ 其他   → TextChunker 滑动窗口 (window=512, overlap=50)
  │       → VectorPipeline.index(chunk)
  │         ├─ dense: OnnxEmbedding → ObjectBox HNSW
  │         └─ sparse: SparseStore (Room FTS4 + Bm25Index)
  │
  └─ 检索流水线 (P0 + P5a)
        ├─ dense:  embed → ObjectBox Top-K → 余弦相似度
        ├─ sparse: Tokenizer → BM25 / FTS MATCH
        └─ hybrid: 两路 RRF 融合 score = Σ 1/(k + rank_i), k=60
              → Top-K 注入 prompt / 返回 MCP
```

## 阶段清单

| 阶段 | 内容 | 状态 |
|------|------|------|
| **P0** | 向量管道：embed + upsert + search | ✅ |
| **P1** | MemoryInjector 双路 RRF 融合 | ✅ |
| **P2** | 文档导入：PDF / Markdown / TXT + 滑动窗口分段 | ✅ |
| **P3** | 对话自动知识抽取 | ✅ |
| **P5b** | MarkdownChunker 标题感知分块 | ✅ |
| **P5a** | BM25 稀疏检索 + Room FTS4 + RRF 混合融合 | ✅ |

## 搜索模式

`kb_search` 支持 `mode` 参数：

| mode | 行为 |
|------|------|
| `hybrid`（默认） | dense + sparse 双路，RRF 融合 (k=60) |
| `dense` | 仅向量：OnnxEmbedding + ObjectBox HNSW |
| `sparse` | 仅 BM25：SparseStore / Bm25Index |

```text
kb_search query="..." top_k=5 mode=hybrid source=knowledge
```

## 目录结构

```
builtin/knowledge/
├─ KnowledgePlugin.kt          # MCP：kb_status / embed / index / search / latency / chunk
├─ data/
│   ├─ BertTokenizer.kt
│   ├─ ObjectBoxVectorStore.kt
│   ├─ OnnxEmbeddingService.kt
│   ├─ VectorItem.kt
│   ├─ TxtDocumentParser.kt
│   ├─ MarkdownDocumentParser.kt
│   ├─ PdfDocumentParser.kt
│   ├─ CompositeDocumentParser.kt
│   ├─ AutoKnowledgeSettings.kt
│   └─ sparse/
│       ├─ SparseStore.kt         # FTS 持久化 + 内存 BM25
│       ├─ SparseDatabase.kt
│       ├─ SparseFtsDao.kt
│       └─ SparseFtsEntity.kt
├─ domain/
│   ├─ EmbeddingService.kt / VectorStore.kt / VectorPipeline.kt
│   ├─ DocumentParser.kt / TextChunker.kt / KnowledgeImportService.kt
│   ├─ MarkdownChunker.kt         # P5b 标题感知
│   ├─ AutoKnowledgeService.kt / AutoKnowledgeMath.kt
│   └─ sparse/
│       ├─ Bm25Index.kt
│       ├─ Tokenizer.kt
│       ├─ SparseRrf.kt            # RRF k=60
│       └─ SparseModels.kt
├─ presentation/
│   ├─ KnowledgeScreen.kt
│   └─ KnowledgeViewModel.kt
└─ di/
    ├─ KnowledgeModule.kt
    └─ SparseModule.kt
```

## 模块说明

### P0 向量管道

- **OnnxEmbeddingService**: GTE-small int8，384d，mean-pooling + L2
- **ObjectBoxVectorStore**: HNSW 余弦近邻
- **VectorPipeline**: embed / upsert / search 门面；`searchHybrid` 走 dense+sparse RRF

### P2 文档导入

- 格式：PDF / Markdown / TXT（SAF OpenDocument / OpenDocumentTree 批量）
- **TextChunker**: 滑动窗口 window=512、overlap=50（与 GTE-small max seq 对齐）
- 估算：CJK 1 字≈1 token，英文词≈1 token

### P5b MarkdownChunker

- 按 ATX 标题（`#`～`######`）切 section，跳过 fence 内 `#`
- 维护 heading stack → 章节路径（`父级 > 子级`）
- section ≤ window 整段一块；否则回退 TextChunker
- 可选注入标题路径到 chunk 文本，提升检索

### P5a BM25 稀疏检索

- **SparseStore**: Room FTS4 持久化 content；内存 **Bm25Index** 打分
- FTS MATCH 作为候选过滤；索引不可用时调用方 fallback LIKE
- **SparseRrf**: 多路排名融合，`DEFAULT_K = 60`
- Tokenizer：CJK 单字 / 英文词，含英文停用词

## 模型文件

| 文件 | 路径 | 大小 |
|------|------|------|
| model_int8.onnx | `app/src/main/assets/models/gte-small/` | 33MB |
| tokenizer.json | `app/src/main/assets/models/gte-small/` | 2MB |

模型下载见 `scripts/download_gte_small.sh`。

## 分段参数

| 参数 | 默认 | 说明 |
|------|------|------|
| window | 512 token | 与 GTE-small max seq 对齐 |
| overlap | 50 token | 保证段落连续性 |
| RRF k | 60 | 稀疏/密集融合常数 |

## MCP 工具

| 工具 | 说明 |
|------|------|
| `kb_status` | 查询知识库/向量管道状态 |
| `kb_embed` | 文本 → 384 维向量（预览 + L2 norm） |
| `kb_index` | 文本向量化并写入 ObjectBox |
| `kb_search` | 检索 Top-K，`mode=hybrid/dense/sparse` |
| `kb_latency` | 端到端延迟测量 |
| `kb_chunk` | 预览滑动窗口分段（不入库） |

## UI 入口

设置 → **知识库** → 选择文件（OpenDocument）或选择文件夹批量导入（OpenDocumentTree）

## 编译注意

ObjectBox 需要编译时 Gradle 插件处理注解生成 `MyObjectBox`。  
首次 pull 后本地跑一次 `./gradlew :app:compileDebugKotlin` 即可。
