# builtin/scheduler — 定时任务模块

Phase 2 内置模块：周期 / 一次性任务自动执行。

## 能力

- **BASIC**：通过 `TaskActionRegistry` 注册回调执行（禁止代码注入）
- **ACTIVE_AGENT**：通知栏提醒 + Deep Link 唤起对话（可自动发送 prompt）

## 调度策略

| 延迟 | 后端 |
|------|------|
| ≥ 15 分钟 | WorkManager `OneTimeWorkRequest` |
| 0 ~ 15 分钟 | AlarmManager `setExactAndAllowWhileIdle` → Receiver enqueue Worker |
| ≤ 0 | 立即 enqueue 0-delay Worker |

周期任务执行后递归计算 `nextRunAt` 再调度，**不使用** `PeriodicWorkRequest`。

## MCP 工具

| 工具 | 说明 |
|------|------|
| `task_create` | 创建并调度 |
| `task_list` | 列表 / 过滤 |
| `task_update` | 更新并重新调度 |
| `task_delete` | 删除 |
| `task_run_now` | 立即执行 |
| `task_pause` / `task_resume` | 暂停 / 恢复 |

## 内置 BASIC action

- `http_request` — url / method / headers / body
- `app_broadcast` — 应用内广播
- `log_event` — 本地日志
- `toast_notify` — Toast

## 权限

- `POST_NOTIFICATIONS`（Android 13+）
- `SCHEDULE_EXACT_ALARM`（Android 12+）

## 代码位置

```
app/src/main/kotlin/com/lanxin/android/builtin/scheduler/
├── SchedulerPlugin.kt
├── data/           # Room Entity / Dao / Database
├── domain/         # Models / Cron / Repository / Engine
├── di/             # Hilt Module
├── worker/         # Worker / AlarmReceiver / BootReceiver
├── registry/       # TaskActionRegistry + 内置 handlers
└── presentation/   # TaskList / TaskEdit
```

规划文档见同目录 `SCHEDULER_PLAN.md`。
