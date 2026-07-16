# builtin/local_inference — 本地推理

Phase 6.1–6.2 端侧 LLM。实现位于：

`app/src/main/kotlin/com/lanxin/android/builtin/localinference/`

设计文档：[`docs/local-inference.md`](../../docs/local-inference.md)

## 状态

- ✅ 接口 + Stub + 配置 + UI + 单测（6.1）
- ✅ 产品方案：总开关默认关；轻量 0.5B/1.5B + 标准 7B Q4（16G 推荐）；本地无 tool_call，记忆/KB 走注入
- ✅ **6.2 离线兜底**：NetworkStatus + RouteCoordinator + ChatRepository 接入
- 🔜 真实 MNN / 6.3 ChatRouter

## 入口

设置 → **本地推理**（含路由预览与离线兜底说明）
