# plugins/memory — 记忆系统

> 插件 ID: `lanxin.memory`  
> 状态：**Phase 5.7 对齐中** ✅ 核心已完成

## 职责

- 本地记忆仓库（Room：`lanxin_memory.db`）
- 记忆 CRUD、导入导出（JSON / Markdown + type/lifecycle 过滤）
- 聊天前检索注入（`MemoryInjector`）
- 判断包加载与场景化注入（Phase 5.7）
- 记忆衰减/清理/归档（Phase 5.7）
- 跨设备同步 + 坚果云 WebDAV（Phase 5.7）

## 结构

```
plugins/memory/
├─ MemoryPlugin.kt
├─ data/memory/
│   ├─ MemoryEntity / MemoryDao / MemoryDatabase
│   ├─ MemoryRepository
│   └─ MemoryExportModels
├─ domain/memory/
│   ├─ MemoryInjector      # Decide + 判断包 + 静默注入 + Trace + 预算
│   └─ ImportStrategy
├─ presentation/ui/memory/
│   ├─ AddMemoryDialog
│   ├─ MemoryCard
│   ├─ MemoryScreen
│   └─ MemorySettingsScreen  ← Phase 5.7 新增
├─ sync/
│   └─ NutstoreSyncProvider  ← Phase 5.7 新增
├─ workers/
│   └─ MemoryMaintenanceWorker  ← Phase 5.7 新增
├─ di/MemoryModule.kt
└─ assets/judgment_packs/  ← Phase 5.7 新增
    ├── lanxin_companion.json
    ├── work_efficiency.json
    └── README.md
```

## 检索注入（MemoryInjector）

`ChatViewModel` 通过 `MemoryInjector.inject(question)` 在发送前拼接记忆上下文。

### 检索流程

```
用户问题
  │
  ├─ Decide 门控（Phase 5.7）
  │     ├─ 空/单字符 → skip
  │     ├─ 纯表情 → skip
  │     ├─ 确认词（好/好的/嗯/行/ok/1/是/不是/继续）→ skip
  │     └─ 含"不用搜" → skip
  │
  ├─ 判断包加载（Phase 5.7）
  │     ├─ 扫描 filesDir/judgment_packs/*.json
  │     ├─ applies_when 命中加分 / does_not_apply_when 排除
  │     └─ 选 0~1 个主包 → 静默注入
  │
  ├─ 路1 稀疏 BM25（Bm25Index，内存指纹）
  │     └─ 失败 / sparseEnabled=false → Room LIKE 降级
  ├─ 路2 语义向量（VectorPipeline，source=memory）
  │     └─ semanticEnabled=false 或异常 → 跳过
  ↓
  RRF 融合：score = Σ 1/(k + rank), k=60
  ↓
  统一拼装（MAX_INJECT_CHARS=1800）
  ├─ 判断包块（优先）
  ├─ 记忆块（Top-K）
  └─ 追加用户原消息
```

### 判断包注入格式

```
[判断准则:工作高效偏好]
- 哥哥偏好简洁高效，先给结论再补关键步骤
- 能清单/表格就不用长段落
...
[准则结束·静默应用·勿向用户朗读]

[我的记忆]
- [事实] 哥哥喜欢用 GitHub PR 流程
...
[记忆结束]

<用户原消息>
```

### 注入预算

- `MAX_INJECT_CHARS = 1800`
- 优先级：判断包 > 高分记忆 > 低分记忆
- 超预算时裁剪最后一块

### Trace

Log 级别：
- `[Trace] decided=skip question=...`
- `[Trace] judgment selected: xxx score=N`
- `[Trace] injected memories count=N`
- `[Trace] memory truncated by budget, used=N`

## 导入策略

| 策略 | 行为 |
|------|------|
| REPLACE | 清空后全量导入 |
| MERGE_BY_ID | 按 id 跳过已存在 |
| MERGE_DEDUP | 按 content+type 去重 |

## 导出（P4）

- JSON 全量 / 按类型过滤
- Markdown dump（分类标题）

## Phase 5.7 新增

| 能力 | 说明 |
|------|------|
| Decide 门控 | 跳过极短确认/纯表情/不用搜 |
| 判断包加载 | 扫描 judgment_packs/*.json + Room judgment |
| 场景匹配 | applies_when / does_not_apply_when |
| 单主判断包 | 0~1 个主包，不混装 |
| 静默注入 | 不复述内部结构给用户 |
| Trace | Log 记录 skipped/injected/no_match |
| 注入预算 | MAX_INJECT_CHARS=1800 |
| 记忆衰减 | 自适应半衰期计算 + 过期清理 |
| 进化索引 | evolution_entries Room 表 |
| 用户画像 | user_profiles Room 表 |
| 任务续接 | task_resumes Room 表 |
| 对话归档 | dialog_archive Room 表 |
| 坚果云同步 | WebDAV SyncProvider |
| 设置页 | 同步开关 + Provider 选择 + 坚果云配置 |

## 开关

| 开关 | 默认 | 说明 |
|------|------|------|
| `enabled` | true | 总开关；关闭后不做任何注入 |
| `semanticEnabled` | true | 语义向量路 |
| `sparseEnabled` | true | BM25 稀疏路 |
| `syncEnabled` | true | **唯一开关**：云端同步（AstrBot/坚果云） |
