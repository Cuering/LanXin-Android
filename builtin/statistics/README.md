# builtin/statistics — 数据统计

> 插件 ID: `lanxin.statistics`  
> 状态：✅ 已完成（Phase 2）

## 职责

- 统计对话轮数（消息数 / 模型调用数）
- 本地估算 token 用量（对齐 AstrBot：无真实 usage 时估算）
- 按日活跃度聚合 + 按提供商汇总
- 设置页可视化 + MCP 工具查询

## 结构

代码位于 `app/src/main/kotlin/com/lanxin/android/builtin/statistics/`：

```
builtin/statistics/
├── README.md
└── (实现) app/.../builtin/statistics/
    ├── StatisticsPlugin.kt           # LanXinPlugin + MCP 工具
    ├── data/
    │   ├── StatisticsEntity.kt       # ProviderStat / DailyStat Entity
    │   ├── StatisticsDao.kt
    │   └── StatisticsDatabase.kt     # Room DB lanxin_statistics.db
    ├── domain/
    │   ├── StatisticsModels.kt       # 领域模型
    │   ├── TokenEstimator.kt         # 本地 token 估算
    │   └── StatisticsRepository.kt
    ├── presentation/
    │   ├── StatisticsScreen.kt
    │   └── StatisticsViewModel.kt
    └── di/StatisticsModule.kt
```

## 对齐 AstrBot

| AstrBot | 兰心 Android |
|---------|--------------|
| `PlatformStat`（按时间桶消息数） | `DailyStat`（按天聚合消息/调用/token） |
| `ProviderStat`（每次调用明细 + token） | `ProviderStat`（本地 Room 明细 + 估算 token） |
| `StatService.get_stat` | `StatisticsRepository.getSummary` |
| `StatService.get_provider_token_stats` | `stats_provider_tokens` MCP / Repository 查询 |
| WebUI 统计页 | Compose `StatisticsScreen` |

## 数据模型

### ProviderStat（调用明细）

| 字段 | 说明 |
|------|------|
| providerId | 平台名 / uid |
| providerModel | 模型名 |
| chatId | 可选会话 ID |
| status | completed / error |
| tokenInput / tokenOutput | token 数 |
| isEstimated | 是否估算（当前恒为 true） |
| startTimeMs / endTimeMs | 耗时 |

### DailyStat（日聚合）

| 字段 | 说明 |
|------|------|
| day | `yyyy-MM-dd` |
| messageCount | 用户消息轮次 |
| callCount | 模型调用次数 |
| successCount | 成功次数 |
| tokenInput / tokenOutput | 当日 token |

## Token 估算

`TokenEstimator`：

- CJK ≈ 1 token / 字
- 其它字符 ≈ 1 token / 4 字符
- 非空文本最少 1

> 当前流式 API 未透出 usage，故统一本地估算；后续若 provider 返回 usage 可直接写入。

## 写入链路

`ChatViewModel.runChatWithTools()` 结束后：

1. 汇总本轮 user 输入 + system prompt + assistant 输出
2. `StatisticsRepository.recordChatTurn(...)`
3. 写 `provider_stats` 明细，并 bump 当日 `daily_stats`
4. 多平台并行时仅 `platformIndex == 0` 计 1 次 message

## MCP 工具

| 工具 | 作用 |
|------|------|
| `stats_summary` | 概览：总量 / 今日 / 按日 / 按提供商 |
| `stats_provider_tokens` | 近期明细 + 提供商汇总 |
| `stats_clear` | 清空本地统计（需 `confirm=true`） |

## 存储

| 数据 | 方案 |
|------|------|
| 调用明细 + 日聚合 | Room `lanxin_statistics.db` |

本地优先，无云端依赖。

## 入口

设置页 →「数据统计」→ `StatisticsScreen`
