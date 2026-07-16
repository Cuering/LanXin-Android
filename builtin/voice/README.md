# builtin/voice — 离线语音（ASR）

Phase 6.4 端侧离线语音识别（Sherpa-ONNX 骨架）。实现位于：

`app/src/main/kotlin/com/lanxin/android/builtin/voice/`

设计文档：[`docs/voice-asr.md`](../../docs/voice-asr.md)

## 状态

- ✅ 接口 + Stub + 配置 + 权限门控 + 设置页 + 单测（6.4 骨架）
- ✅ 产品方案：总开关默认关；小模型优先；不后台偷录；文本交给 Chat 发送链路
- 🔜 Chat「按住说话」完整 UI（`VoiceInputCoordinator` 已预留）
- 🔜 真实 sherpa-onnx so

## 入口

设置 → **离线语音识别**（开关 / 模型路径 / 语言 / 状态 / 试转写 / 申请麦克风权限）

## 6.4 边界

- 仍无真实 `libsherpa-onnx` / 模型打包
- 试转写使用 stub PCM，不强制真机麦克风
- 不实现本地 LLM tool_call
