# plugins/memory — 记忆系统

> 插件 ID: `lanxin.memory`  
> 状态：已迁入 ✅

## 职责

- 本地记忆仓库（Room：`lanxin_memory.db`）
- 记忆 CRUD、导入导出（JSON）
- 聊天前关键词检索注入（`MemoryInjector`）

## 结构

```
plugins/memory/
├── MemoryPlugin.kt
├── data/memory/
│   ├── MemoryEntity / MemoryDao / MemoryDatabase
│   ├── MemoryRepository
│   └── MemoryExportModels
├── domain/memory/
│   ├── MemoryInjector
│   └── ImportStrategy
├── presentation/ui/memory/
└── di/MemoryModule.kt
```

## 导入策略

| 策略 | 行为 |
|------|------|
| REPLACE | 清空后全量导入 |
| MERGE_BY_ID | 按 id 跳过已存在 |
| MERGE_DEDUP | 按 content+type 去重 |

## 注入

`ChatViewModel` 通过 `MemoryInjector.inject(question)` 在发送前拼接记忆上下文。  
Phase 2.1 计划升级为向量检索。
