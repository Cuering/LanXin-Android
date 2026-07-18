# builtin/local_inference — 本地推理

Phase 6.1–6.3 端侧 LLM + **P2 MNN 真引擎**。实现位于：

`app/src/main/kotlin/com/lanxin/android/builtin/localinference/`

设计文档：[`docs/local-inference.md`](../../docs/local-inference.md)

## 状态

- ✅ 接口 + Stub + 配置 + UI + 单测（6.1）
- ✅ 产品方案：总开关默认关；轻量 0.5B/1.5B + 标准 7B Q4（16G 推荐）；本地无 tool_call，记忆/KB 走注入
- ✅ **6.2 离线兜底**：NetworkStatus + RouteCoordinator + ChatRepository 接入
- ✅ **6.3 ChatRouter**：统一路由决策 + reason 码 + needsTools 优先云端
- ✅ **P2 MNN 真引擎**：官方 3.6.0 预编译 so（构建期下载）+ `libmnn_lanxin.so` JNI + `MnnLocalLlmEngine`（native 失败降级 stub）

## 入口

设置 → **本地推理**（含路由预览：有网/无网 · 本地就绪 · 纯对话/需工具 · 路由目标）

## P2 边界

- so **不进 git**：`downloadMnnNative` → `jniLibs/`（见 `app/libs/README.md`）
- 模型权重外置：`LanXin/models/local-llm/`（`config.json` + `*.mnn` + tokenizer）
- 仍无本地 tool_call（需要工具的任务走云端）
- x86_64 为 stub so；真推理目标 **arm64-v8a**
- 许可证：Apache-2.0（`third_party/mnn/NOTICE`）
