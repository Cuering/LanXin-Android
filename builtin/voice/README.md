# builtin/voice — 离线语音（ASR + TTS）

Phase 6.4 端侧离线语音识别（Sherpa-ONNX 骨架）+ M1 TTS 接口骨架。实现位于：

`app/src/main/kotlin/com/lanxin/android/builtin/voice/`

设计文档：

- [`docs/voice-asr.md`](../../docs/voice-asr.md) — ASR
- [`docs/meiju-style-pet.md`](../../docs/meiju-style-pet.md) — 桌宠语音会话主线（TTS 消费方）

## 状态

- ✅ ASR：接口 + Stub + 配置 + 权限门控 + 设置页 + 单测（6.4）
- ✅ TTS：`TtsEngine` + `StubTtsEngine` + DataStore（M1，供 VoiceSession）
- ✅ 产品：总开关默认关；不后台偷录
- 🔜 真 sherpa-onnx / Bert-VITS2 so（不进 git）
- 🔜 桌宠主路径优先于 Chat「按住说话」填输入框

## 入口

- 设置 → **离线语音识别**（ASR）
- 设置 → **桌宠 / 语音陪伴**（会话 + stub 听想说）

## 边界

- 无真实 so / 模型打包
- TTS stub 返回空 PCM + 字幕；真播放见 M3
