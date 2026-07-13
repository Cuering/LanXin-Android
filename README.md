# LanXin Android

在手机上运行的 AI 助理 APP。基于 GPT Mobile 改造，引入插件化架构，借鉴 AstrBot 设计思路。

## 功能

- 多模型提供商：OpenAI / Anthropic / Google / Groq / Ollama 等
- 本地聊天历史（`plugins/chat`）
- 本地记忆系统（`plugins/memory`）
- 自动更新 / 版本回退 + 数据备份还原（`core/updater`）
- 日志系统 + 日志查看插件（`core/log` + `plugins/logger`）
- Material You / 深色模式

## 架构（三层）

```
core/        原生内核（engine / provider / config / log / updater …）
builtin/     内置功能（persona / statistics … Phase 2）
plugins/     可拔插插件（memory / chat / logger）
app/         壳应用（Compose UI 入口）
```

详见 [ARCHITECTURE.md](./ARCHITECTURE.md)。

## 构建

```bash
./gradlew assembleDebug
./gradlew test
```

- Min SDK 31 / Target SDK 37
- Kotlin + Jetpack Compose + Hilt + Room + Ktor

## 模块文档

| 文档 | 说明 |
|------|------|
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 架构定稿 |
| [core/log/README.md](./core/log/README.md) | 日志底层 |
| [core/updater/README.md](./core/updater/README.md) | 更新与备份 |
| [plugins/memory/README.md](./plugins/memory/README.md) | 记忆插件 |
| [plugins/chat/README.md](./plugins/chat/README.md) | 对话插件 |
| [plugins/logger/README.md](./plugins/logger/README.md) | 日志查看 UI |

## License

见 [LICENSE](./LICENSE)。
