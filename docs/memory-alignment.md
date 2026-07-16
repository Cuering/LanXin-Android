# Phase 5.7 — 记忆全量对齐 + 坚果云同步

> 分支：`feat/phase5-7-memory-align`  
> 状态：进行中

## 目标

将 Android App 端 `plugins/memory/` 记忆模块与 AstrBot 侧 `memory_system_manager` 插件功能全面对齐。

## 改动范围

**只改 `plugins/memory/` 内现有文件**，不新建插件、不动 AstrBot 核心。

## 功能清单

### 1. Decide 门控
`MemoryInjector.inject()` 开头判断：空消息、纯表情、极短确认（好/好的/嗯/行/ok/1/是/不是/继续）、含"不用搜"→ 原样返回，不走记忆通道。

### 2. 判断包加载
- 扫描 `filesDir/judgment_packs/*.json`
- `MemoryEntity` 加 `type=judgment` 常量（复用现有字段）
- 结构：`id/name/priority/applies_when/does_not_apply_when/rules/boundaries`
- assets 预置 2 个示例包首次拷贝

### 3. 场景匹配
`applies_when` 命中候选，`does_not_apply_when` 排除，无 applies 时弱兜底（低分）。

### 4. 单主判断包
注入时从 judgment 条目里选 0~1 个最优，拼到记忆块前面。

### 5. 静默注入
格式 `[判断准则:xxx]...[准则结束·静默应用·勿向用户朗读]`，在记忆块前。

### 6. Trace
Log skipped/injected/no_match，不暴露给用户。

### 7. 注入预算
`MAX_INJECT_CHARS=1800`，优先判断包再高分记忆，超预算裁剪。

### 8. 记忆衰减/清理
注入时更新 `lastAccessedAt`；WorkManager 日维护过期清理（90天未访问 + 非 permanent）。

### 9. LLM 提取
对话后异步提取 1-3 条记忆原子，走服务端 LLM 通道。

### 10. 进化索引
Room 表 `evolution_entries`；短查询(<15字)附最近准则。

### 11. 用户画像
Room 表 `user_profile`；注入摘要裁剪。

### 12. 任务续接
未完成任务下次会话注入续接提示。

### 13. 会话追踪
新会话/交接记录。

### 14. 智能建议
基于记忆分析 → 最多 1 条短建议。

### 15. 对话归档
`dialog_archive` 表 + 周期归档。

## 同步架构

### 唯一开关
`syncEnabled: Boolean` 默认 true，**唯一开关**。

### Provider 接口
```kotlin
interface SyncProvider {
    suspend fun push(data: Map<String, ByteArray>): Result<Unit>
    suspend fun pull(): Result<Map<String, ByteArray>>
}
```

### 实现
- `AstrBotSyncProvider` — 复用 5.1 LWW 思路
- `NutstoreSyncProvider` — WebDAV（默认 `https://dav.jianguoyun.com/dav/lanxin/`，用户名+应用密码，OkHttp PROPFIND/PUT/GET）

### 设置页
- 总开关 on/off
- Provider 选择（AstrBot / 坚果云）
- 坚果云配置表单（地址/用户名/应用密码）+ 测试连接 + 同步间隔

## Room 变更

version 2 migration，新增表：
- `user_profiles` — 用户画像
- `evolution_entries` — 进化条目
- `task_resumes` — 任务续接
- `dialog_archive` — 对话归档

## WorkManager

- `MemoryMaintenanceWorker` — 日 01:00 衰减/清理/进化/归档
- `MemoryExtractionWorker` — OneTime LLM 提取
- `MemorySyncWorker` — 周期同步；`syncEnabled=false` 不调度

## 文件列表

| 文件 | 改动 |
|------|------|
| `data/memory/MemoryEntity.kt` | 加 `type=judgment` 常量 |
| `data/memory/MemoryDao.kt` | 新增 judgment/evolution/profile/task/dialog 查询 + 实体 |
| `data/memory/MemoryDatabase.kt` | DB v2 + 新表 |
| `data/memory/MemoryRepository.kt` | 衰减/清理/画像/进化/任务/归档方法 |
| `domain/memory/MemoryInjector.kt` | Decide + 判断包 + 静默注入 + Trace + 预算 |
| `sync/NutstoreSyncProvider.kt` | 坚果云 WebDAV 实现 |
| `presentation/ui/memory/MemorySettingsScreen.kt` | 设置页（同步开关/provider/坚果云配置） |
| `workers/MemoryMaintenanceWorker.kt` | 日维护 Worker |
| `assets/judgment_packs/` | 示例判断包 |
| `README.md` | 更新文档 |
| `ARCHITECTURE.md` | Phase 5.7 章节 |
