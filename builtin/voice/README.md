# builtin/voice — 离线语音（ASR + TTS）

实现：`app/src/main/kotlin/com/lanxin/android/builtin/voice/`

设计文档：

- [`docs/voice-asr.md`](../../docs/voice-asr.md) — ASR（P0 真 sherpa 引擎）
- [`docs/meiju-style-pet.md`](../../docs/meiju-style-pet.md) — 桌宠语音会话

## 状态

- ✅ ASR：`SherpaAsrEngine` + 官方 AAR 运行时进 APK；模型外置 `LanXin/asr/`
- ✅ `SherpaOnnxBridge`：`loadModel` / `transcribe` / `unload`；JVM 无 so 安全降级
- ✅ TTS：`StubTtsEngine`（P1 真引擎）
- ✅ 产品：总开关默认关；不后台偷录
- 🔜 Chat「按住说话」UI；TTS / MNN 真引擎（P1/P2）

## 运行时

- 构建：`downloadSherpaOnnxAar` → `app/libs/sherpa-onnx-static-link-onnxruntime-1.13.4.aar`
- 许可证：`third_party/sherpa-onnx/NOTICE`（Apache-2.0）
- 覆盖：`SHERPA_ONNX_AAR` / `SHERPA_ONNX_AAR_URL`

## 入口

- 设置 → **离线语音识别**（ASR）
- 设置 → **桌宠 / 语音陪伴**（会话 + stub TTS）
