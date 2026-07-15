# 同步引擎 (builtin/sync)

> Phase 5.1 — Android ↔ AstrBot knowledge / memory 同步底座（MVP）

## 状态

| 项 | 状态 |
|----|------|
| 协议草案 | ✅ `docs/sync-protocol.md` |
| SyncClient / SyncRepository | ✅ |
| Outbox（内存） | ✅ |
| Memory 映射 + UI 入队 | ✅ |
| Knowledge 适配 | 🔜 接口预留 |
| LWW helper | ✅（5.2 完整策略） |
| 设置页 / 自动调度 | 🔜 |
| AstrBot 服务端 | 文档 endpoint，本仓不改系统源码 |

## 模块结构

```
app/.../builtin/sync/
├── domain/
│   ├── SyncModels.kt        # SyncItem / pull-push DTO
│   ├── SyncClient.kt        # HTTP 抽象
│   ├── SyncRepository.kt    # 引擎门面
│   ├── LwwResolver.kt       # LWW 比较
│   └── SyncItemMapper.kt    # Memory ↔ SyncItem
├── data/
│   ├── HttpSyncClient.kt
│   ├── DefaultSyncRepository.kt
│   ├── InMemorySyncOutbox.kt
│   └── SyncPreferences.kt   # device_id / token / server_time
└── di/SyncModule.kt
```

## 同步路径

```
Memory UI 增删改
    → DefaultSyncRepository.enqueue*
    → InMemorySyncOutbox
    → syncOnce(): push → pull → LWW merge → MemoryRepository
    → HTTP POST {baseUrl}/api/sync/push|pull
```

**不走聊天通道**，不污染会话 / ChatViewModel。

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
./gradlew test --tests "com.lanxin.android.builtin.sync.*"
```

## 参考

- 协议：`docs/sync-protocol.md`
- 架构：`ARCHITECTURE.md` Phase 5.1
