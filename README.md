# LanXin Android

在手机上运行的 AI 助理 APP。基于 GPT Mobile 改造，引入插件化架构，借鉴 AstrBot 设计思路。

## 功能

- 多模型提供商：OpenAI / Anthropic / Google / Groq / Ollama 等
- 本地聊天历史（`plugins/chat`）
- 本地记忆系统（`plugins/memory`）
- 知识库 - 向量检索 + BM25 稀疏检索（`builtin/knowledge`）
- 人格设定：切换 / 自定义 AI system prompt（`builtin/persona`）
- 数据统计：对话轮数、token 估算、按日活跃度（`builtin/statistics`）
- 定时任务：周期/一次性 BASIC 回调与 ACTIVE_AGENT 提醒（`builtin/scheduler`）
- 手机平台工具：剪贴板 / 已安装应用 / 系统信息（`builtin/platform`）
- 系统能力：日历 / 闹钟 / 笔记 / 用户文件 + DeviceToolBridge 对话/桌宠一体（`builtin/systemtools`，默认关，Phase 7.5）
- 自动更新 / 版本回退 + 数据备份还原（`core/updater`）
- 日志系统 + 日志查看插件（`core/log` + `plugins/logger`）
- Skill 加载器（`app/skill`）
- Material You / 深色模式

## 架构（三层）

```
core/        原生内核（engine / provider / config / log / updater …）
builtin/     内置功能（persona ✅ / knowledge ✅ / statistics ✅ / scheduler ✅ / platform ✅）
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
- Wrapper 默认使用 [官方 Gradle 分发](https://services.gradle.org/distributions/)，便于 GitHub Actions 稳定拉取；`setup-java` / `setup-gradle` 会缓存 wrapper dists
- 国内本机若官方源较慢，可在 `~/.gradle/init.gradle` 自行配置镜像（例如腾讯云），勿改仓库内 `gradle-wrapper.properties`

## 模块文档

| 文档 | 说明 |
|------|------|
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 架构定稿 |
| [core/log/README.md](./core/log/README.md) | 日志底层 |
| [core/updater/README.md](./core/updater/README.md) | 更新与备份 |
| [plugins/memory/README.md](./plugins/memory/README.md) | 记忆插件 |
| [plugins/chat/README.md](./plugins/chat/README.md) | 对话插件 |
| [plugins/logger/README.md](./plugins/logger/README.md) | 日志查看 UI |
| [builtin/persona/README.md](./builtin/persona/README.md) | 人格设定 |
| [builtin/knowledge/README.md](./builtin/knowledge/README.md) | 知识库系统 |
| [builtin/statistics/README.md](./builtin/statistics/README.md) | 数据统计 |
| [builtin/scheduler/README.md](./builtin/scheduler/README.md) | 定时任务 |
| [builtin/platform/README.md](./builtin/platform/README.md) | 手机平台工具 |
| [docs/debug-assets.md](./docs/debug-assets.md) | Debug：免费 Live2D + 开源 ASR/TTS 资源 |
| [builtin/pet/README.md](./builtin/pet/README.md) | 桌宠 / 语音陪伴（Phase 6） |
| [docs/meiju-style-pet.md](./docs/meiju-style-pet.md) | 妹居风格架构对照（资源不入库） |
| [docs/system-tools.md](./docs/system-tools.md) | Phase 7 系统能力（日历/闹钟/笔记/文件） |
| [docs/scene-sensing.md](./docs/scene-sensing.md) | 摄像头场景识别（默认关 + 确认 Gate） |
| [builtin/systemtools/README.md](./builtin/systemtools/README.md) | 系统能力模块 |
| [docs/dynamic-plugins.md](./docs/dynamic-plugins.md) | 动态插件加载 / 管理 / 市场 / 签名 |
| [docs/claw-host.md](./docs/claw-host.md) | 机器人 / Claw 常驻宿主 |

### Debug 语音 / Live2D 资源

- **Live2D 官方 Sample Mao** 已 vendor 进仓：`app/src/main/assets/pet/live2d/Mao/`（~4.2MB，Sample Data Terms）。默认优先级：用户配置 → 内置 → debug-assets → 妹居参考。详见 [docs/live2d-mao-sample.md](./docs/live2d-mao-sample.md)。
- **ASR / TTS 大包不进 git**。开发者机调试：

```bash
bash scripts/fetch-debug-assets.sh
# 或 bash scripts/download-debug-assets.sh
```

详见 [docs/debug-assets.md](./docs/debug-assets.md)。

## License

见 [LICENSE](./LICENSE)。
