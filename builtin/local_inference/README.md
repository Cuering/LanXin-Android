# builtin/local_inference — 本地推理

Phase 6.1–6.3 端侧 LLM。实现位于：

`app/src/main/kotlin/com/lanxin/android/builtin/localinference/`

设计文档：[`docs/local-inference.md`](../../docs/local-inference.md)

## 状态

- ✅ 接口 + Stub + 配置 + UI + 单测（6.1）
- ✅ 产品方案：总开关默认关；轻量 0.5B/1.5B + 标准 7B Q4（16G 推荐）；本地无 tool_call，记忆/KB 走注入
- ✅ **6.2 离线兜底**：NetworkStatus + RouteCoordinator + ChatRepository 接入
- ✅ **6.3 ChatRouter**：统一路由决策 + reason 码 + needsTools 优先云端
- 🔜 真实 MNN so

## 入口

设置 → **本地推理**（含路由预览：有网/无网 · 本地就绪 · 纯对话/需工具 · 路由目标）

## 6.3 边界

- 仍无真实 `libMNN.so` / 模型打包
- 仍无本地 tool_call（需要工具的任务走云端）
