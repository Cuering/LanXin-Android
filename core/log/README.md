# core/log — 日志底层

参考 AstrBot `astrbot/core/log.py`。

## 组件

| 类 | 职责 |
|----|------|
| `LogManager` | 初始化、getLogger()、文件轮转写入 |
| `LogBroker` | 最近 500 条缓存 + Flow 订阅分发 |
| `LanXinLogger` | 轻量 logger（d/i/w/e/c） |
| `LogLevel` | DEBUG / INFO / WARNING / ERROR / CRITICAL |
| `LogEntry` | 单条日志模型 |

## 用法

```kotlin
// Application.onCreate
logManager.initialize(context)

val logger = logManager.getLogger("MyTag")
logger.info("hello")
logger.error("boom", throwable)

// 订阅实时日志
logBroker.events.collect { entry -> ... }
```

## 文件

- 目录：`context.filesDir/logs/`
- 当前文件：`lanxin.log`
- 轮转：超过 5MB 重命名为 `lanxin-<ts>.log`，最多保留 5 个历史文件

## UI

日志浏览 UI 在 `plugins/logger/`，不放 core。
