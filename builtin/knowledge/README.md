# 知识库模块 (builtin/knowledge)

> Phase 3 P0：打通向量管道（ONNX + GTE-small int8 + ObjectBox embed/检索）

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

## 当前状态 (P0)

- [x] OnnxEmbeddingService (GTE-small int8, 384d, mean-pooling + L2)
- [x] ObjectBoxVectorStore (HNSW 余弦近邻检索)
- [x] VectorPipeline (embed + upsert + search 门面)
- [x] KnowledgePlugin (5 个 MCP 工具：status/embed/index/search/latency)
- [x] Hilt DI 注入 (SingletonComponent)
- [ ] 编译生成 MyObjectBox（需要本地 / CI 编译一次）
- [ ] 双路 RRF 融合检索 (P1)
- [ ] 文档导入 txt/md/pdf (P2)
- [ ] 自动知识抽取 (P3)

## 模型文件

| 文件 | 路径 | 大小 |
|------|------|------|
| model_int8.onnx | `app/src/main/assets/models/gte-small/` | 33MB |
| tokenizer.json | `app/src/main/assets/models/gte-small/` | 2MB |

模型下载方式见 `scripts/download_gte_small.sh`

## 编译注意

ObjectBox 需要编译时 Gradle 插件处理注解生成 `MyObjectBox`。
首次 pull 后本地跑一次 `./gradlew :app:compileDebugKotlin` 即可。
或通过 GitHub Actions (workflow: Compile KB P0) 在 CI 上自动生成。

## MCP 工具列表

| 工具 | 说明 |
|------|------|
| `kb_status` | 查询知识库/向量管道状态 |
| `kb_embed` | 文本 → 384 维向量 (返回预览 + L2 norm) |
| `kb_index` | 文本向量化并写入 ObjectBox |
| `kb_search` | 语义检索 Top-K |
| `kb_latency` | 端到端延迟测量 |
