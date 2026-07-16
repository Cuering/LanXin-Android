# Debug assets（本地大文件，不进 git）

本目录用于存放 **Debug 用** Live2D / ASR / TTS 模型。

- **禁止** commit 真实 `.onnx` / `.moc3` / so / 妹居商业资源
- 使用仓库根目录脚本拉取开源/官方样例：

```bash
bash scripts/download-debug-assets.sh          # 全部（Live2D 说明 + ASR + TTS）
bash scripts/download-debug-live2d.sh          # 仅 Live2D（官方 sample 约定）
bash scripts/download-debug-asr.sh             # 仅 ASR
bash scripts/download-debug-tts.sh             # 仅 TTS
```

完整说明见 [`docs/debug-assets.md`](../docs/debug-assets.md)。

## 约定目录

```
debug-assets/
├── live2d/          # model3.json + 纹理等
├── asr/             # sherpa-onnx ASR 解压目录
└── tts/             # sherpa-onnx TTS 解压目录
```

真机可用 `adb push` 到 App `filesDir`，或在设置页填写本机绝对路径。
