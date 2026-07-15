# plugins/memory — 记忆系统

> 插件 ID: `lanxin.memory`  
> 状态：已迁入 ✅

## 职责

- 本地记忆仓库（Room：`lanxin_memory.db`）
- 记忆 CRUD、导入导出（JSON / Markdown + 类型过滤）
- 聊天前检索注入（`MemoryInjector`）

## 结构

```
plugins/memory/
├─ MemoryPlugin.kt
├─ data/memory/
│   ├─ MemoryEntity / MemoryDao / MemoryDatabase
│   ├─ MemoryRepository
│   └─ MemoryExportModels
├─ domain/memory/
│   ├─ MemoryInjector      # 稀疏 BM25 + 语义向量 + RRF
│   └─ ImportStrategy
├─ presentation/ui/memory/
└─ di/MemoryModule.kt
```

## 导入策略

| 策略 | 行为 |
|------|------|
| REPLACE | 清空后全量导入 |
| MERGE_BY_ID | 按 id 跳过已存在 |
| MERGE_DEDUP | 按 content+type 去重 |

## 检索注入（MemoryInjector）

`ChatViewModel` 通过 `MemoryInjector.inject(question)` 在发送前拼接记忆上下文。

### 检索流程

```
用户问题
  │
  ├─ 路1 稀疏 BM25（Bm25Index，内存指纹）
  │     └─ 失败 / sparseEnabled=false → Room LIKE 降级
  ├─ 路2 语义向量（VectorPipeline，source=memory）
  │     └─ semanticEnabled=false 或异常 → 跳过
  ↓
  RRF 融合：score = Σ 1/(k + rank), k=60
  ↓
  Top-N 注入 prompt
```

### 开关

| 属性 | 默认 | 说明 |
|------|------|------|
| `enabled` | `true` | 总开关；关闭后不做任何注入 |
| `semanticEnabled` | `true` | 语义向量路；关闭后仅走稀疏/关键词 |
| `sparseEnabled` | `true` | BM25 稀疏路；关闭后直接 Room LIKE |

### 稀疏路说明（P5a）

- 使用 `builtin/knowledge` 的 `Bm25Index` + 内容指纹缓存
- 指纹变化时重建内存索引
- 无 BM25 命中或异常时 fallback 到原 LIKE 模糊匹配

## 导出（P4）

- JSON 全量 / 按类型过滤
- Markdown dump（分类标题）
