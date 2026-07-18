# builtin/voice — 离线语音（ASR + TTS）

实现：`app/src/main/kotlin/com/lanxin/android/builtin/voice/`

设计文档：

- [`docs/voice-asr.md`](../../docs/voice-asr.md) — ASR（P0 真 sherpa 引擎）
- [`docs/voice-tts.md`](../../docs/voice-tts.md) — TTS（P1 真 OfflineTts）
- [`docs/meiju-style-pet.md`](../../docs/meiju-style-pet.md) — 桌宠语音会话

## 状态

- ✅ ASR：`SherpaAsrEngine` + 官方 AAR 运行时进 APK；模型外置 `LanXin/asr/`
- ✅ `SherpaOnnxBridge`：`loadModel` / `transcribe` / `unload`；JVM 无 so 安全降级
- ✅ TTS：`SherpaTtsEngine` + `SherpaTtsBridge`（OfflineTts）；模型外置 `LanXin/tts/`
- ✅ `StubTtsEngine` / `StubAsrEngine` 保留单测
- ✅ 产品：总开关默认关；不后台偷录
- 🔜 Chat「按住说话」UI

## 运行时

- 构建：`downloadSherpaOnnxAar` → `app/libs/sherpa-onnx-static-link-onnxruntime-1.13.4.aar`
- ASR + TTS **共用** `libsherpa-onnx-jni.so`
- 许可证：`third_party/sherpa-onnx/NOTICE`（Apache-2.0）
- 覆盖：`SHERPA_ONNX_AAR` / `SHERPA_ONNX_AAR_URL`

## 入口

- 设置 → **离线语音识别**（ASR）
- 设置 → **桌宠 / 语音陪伴**（会话 + TTS）
