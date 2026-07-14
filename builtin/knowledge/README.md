# 知识库模块 (builtin/knowledge)

> Phase 3：端侧向量检索 + 文档导入

## 架构

```
用户输入
  │
  ├─ 关键词路：Room LIKE 模糊匹配 → 命中得基础分
  └─ 语义路：embed → ObjectBox Top-10 → 余弦相似度
  │
  ↓ RRF 融合: score = Σ 1 / (k + rank_i), k=60
  ↓ Top-5 注入 prompt
```

文档导入流水线（P2）：

```
SAF 选文件 → DocumentParser(txt/md/pdf)
  → TextChunker(window=512, overlap=50)
  → VectorPipeline.index(chunk)
  → ObjectBox HNSW
```

## 当前状态

- [x] OnnxEmbeddingService (GTE-small int8, 384d, mean-pooling + L2)
- [x] ObjectBoxVectorStore (HNSW 余弦近邻检索)
- [x] VectorPipeline (embed + upsert + search 门面)
- [x] KnowledgePlugin MCP 工具
- [x] MemoryInjector 双路 RRF 融合 (P1)
- [x] 文档导入 UI + txt/md/pdf 解析 + 滑动窗口分段 (P2)
- [ ] 自动知识抽取 (P3)

## 目录结构

```
builtin/knowledge/
├── KnowledgePlugin.kt
├── data/
│   ├── BertTokenizer.kt
│   ├── ObjectBoxVectorStore.kt
│   ├── OnnxEmbeddingService.kt
│   ├── VectorItem.kt
│   ├── TxtDocumentParser.kt
│   ├── MarkdownDocumentParser.kt
│   ├── PdfDocumentParser.kt
│   └── CompositeDocumentParser.kt
├── domain/
│   ├── EmbeddingService.kt
│   ├── VectorStore.kt
│   ├── VectorPipeline.kt
│   ├── DocumentParser.kt
│   ├── TextChunker.kt
│   └── KnowledgeImportService.kt
├── presentation/
│   ├── KnowledgeScreen.kt
│   └── KnowledgeViewModel.kt
└── di/
    └── KnowledgeModule.kt
```

## 模型文件

| 文件 | 路径 | 大小 |
|------|------|------|
| model_int8.onnx | `app/src/main/assets/models/gte-small/` | 33MB |
| tokenizer.json | `app/src/main/assets/models/gte-small/` | 2MB |

模型下载方式见 `scripts/download_gte_small.sh`

## 分段参数

| 参数 | 默认 | 说明 |
|------|------|------|
| window | 512 token | 与 GTE-small max seq 对齐 |
| overlap | 50 token | 保证段落连续性 |
| 估算 | CJK 1 字≈1 token，英文词≈1 token | 不强制跑完整 BertTokenizer |

## MCP 工具列表

| 工具 | 说明 |
|------|------|
| `kb_status` | 查询知识库/向量管道状态 |
| `kb_embed` | 文本 → 384 维向量 (返回预览 + L2 norm) |
| `kb_index` | 文本向量化并写入 ObjectBox |
| `kb_search` | 语义检索 Top-K |
| `kb_latency` | 端到端延迟测量 |
| `kb_chunk` | 预览滑动窗口分段（不入库） |

## UI 入口

设置 → **知识库** → 选择文件导入（SAF OpenDocument）

## 编译注意

ObjectBox 需要编译时 Gradle 插件处理注解生成 `MyObjectBox`。
首次 pull 后本地跑一次 `./gradlew :app:compileDebugKotlin` 即可。
