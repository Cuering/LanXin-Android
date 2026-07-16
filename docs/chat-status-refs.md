# Chat UX：动态状态文案 + 记忆/知识引用

## 目标

在助手回复生成过程中提供**会变化的状态文字**（不是完整任务进度条），
并在本轮实际注入的记忆 / 知识库条目上展示**可点击引用**，跳转到原文。

## 状态文案 ≠ 进度条

- 类似深度思考（ThinkingBlock）风格：一行文案 + 可选 spinner
- 阶段示例：`Preparing` → `SearchingMemory` → `SearchingKnowledge` → `Generating` →（可选）`CallingTools` → `Done`
- **不**做多步时间线 / Claude 式完整 tool 进度条（P0 不强制）
- 流式正文开始后状态收起为「已完成」或隐藏

实现：

| 符号 | 说明 |
|------|------|
| `ChatGenerationPhase` | 阶段枚举 |
| `ChatGenerationStatusLogic` | 文案与切换纯逻辑（单测） |
| `ChatStatusBlock` | Compose 展示 |
| `ChatViewModel.turnUxStates` | 按 turn 挂载 phase / refs |

## 引用可点

- 模型：`ChatRef(type, id, title, snippet)`，`type ∈ MEMORY | KNOWLEDGE`
- 数据来自现有注入链路，避免平行系统：
  - UnifiedSearch 开：`injectWithHits()` → memory / knowledge hits
  - 关：`MemoryInjector.injectWithMatches()` → 有 id 的记忆实体
- 无命中：不显示引用区
- 点击记忆 → `Route.MEMORY_EDIT` → 现有 Memory 列表 + 编辑 Dialog（**可改可删**，不重做一套）
- 点击知识 → `Route.KNOWLEDGE_DETAIL` 只读详情（snippet + externalId，预留全文）

## 持久化

**P0：仅当前会话内存展示**（`turnUxStates`），不写 Room / 不做 migration。
进程杀死后引用区不保留；后续若需历史可点，再小步 migration。

## 边界

- 不改 AstrBot 核心
- 本地无 tool_call；检索注入仍在 App 侧
- 多平台并行时仅 platformIndex==0 驱动状态文案
- 历史 turn 的 phase 不展示（仅 refs 仍可显示若会话内还在 map 中）

## 测试

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.lanxin.android.presentation.ui.chat.ChatGenerationStatusLogicTest"
```
