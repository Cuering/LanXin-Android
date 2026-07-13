# plugins/logger — 日志查看 UI

> 插件 ID: `lanxin.logger`

## 功能

- 浏览日志文件列表（`filesDir/logs/`）
- 实时缓存（LogBroker 内存）
- 按级别过滤（DEBUG / INFO / WARNING / ERROR / CRITICAL）
- 关键字搜索
- 导出 / 分享日志文件

## 入口

设置页 →「日志查看」→ `Route.LOGGER` → `LoggerScreen`

## 依赖

仅依赖 `core/log`（LogManager / LogBroker），不反向依赖其他插件。
