# 同步引擎 (builtin/sync)

> Phase 5.1 底座 + Phase 5.2 LWW 冲突策略（Android ↔ AstrBot knowledge / memory）

## 状态

| 项 | 状态 |
|----|------|
| 协议草案 | ✅ `docs/sync-protocol.md`（§4 LWW 完整） |
| SyncClient / SyncRepository | ✅ |
| Outbox（内存） | ✅ |
| Memory 映射 + UI 入队 | ✅ |
| Knowledge 适配 | 🔜 列表层 LWW 可测；落库后置 |
| **LWW 冲突策略** | ✅ Phase 5.2：`compare` / `pick` / `decide` / `mergeById` |
| push.applied ≡ pull merge | ✅ 统一 `applyRemoteItem` 入口 |
| 可观测性 | ✅ `SyncCycleResult.skipped` / `conflictResolved` |
| 设置页 / 自动调度 | 🔜 |
| AstrBot 服务端 | 文档 endpoint，本仓不改系统源码 |

## 模块结构

```
app/.../builtin/sync/
├── domain/
│   ├── SyncModels.kt        # SyncItem / pull-push DTO / SyncCycleResult
│   ├── SyncClient.kt        # HTTP 抽象
│   ├── SyncRepository.kt    # 引擎门面
│   ├── LwwResolver.kt       # LWW 比较 + decide（5.2）
│   └── SyncItemMapper.kt    # Memory ↔ SyncItem
├── data/
│   ├── HttpSyncClient.kt
│   ├── DefaultSyncRepository.kt  # push → pull → 统一 LWW apply
│   ├── InMemorySyncOutbox.kt
│   └── SyncPreferences.kt   # device_id / token / server_time
└── di/SyncModule.kt
```

## 同步路径

```
Memory UI 增删改
    → DefaultSyncRepository.enqueue*
    → InMemorySyncOutbox
    → syncOnce():
         push outbox
           └─ applied[] → applyRemoteItem → LwwResolver.decide
         pull since
           └─ items[]  → applyRemoteItem → LwwResolver.decide  (同一入口)
         → MemoryRepository 写库 / skipped
    → HTTP POST {baseUrl}/api/sync/push|pull
```

**不走聊天通道**，不污染会话 / ChatViewModel。

## LWW 比较顺序（协议 §4）

1. `updated_at` 较大者胜  
2. 相等时 `deleted=true` 优先（防复活）  
3. 仍相等 `source` 字典序较大者胜  
4. 仍相等 `preferRemote`（默认 true）

## 配置键（DataStore）

| Key | 说明 |
|-----|------|
| `sync_device_id` | 设备 UUID |
| `sync_base_url` | AstrBot 根 URL |
| `sync_token` | Bearer token |
| `sync_user_id` | 可选用户/工作区 |
| `sync_enabled` | 总开关 |
| `sync_last_server_time` | 增量水位 |

## 单测

```bash
./gradlew :app:testDebugUnitTest --tests "com.lanxin.android.builtin.sync.*"
```

重点：`LwwResolverTest`（相等时间戳、tombstone 防复活、source 决胜、preferRemote、knowledge `mergeById`）。

## 参考

- 协议：`docs/sync-protocol.md`
- 架构：`ARCHITECTURE.md` Phase 5.1 / 5.2
