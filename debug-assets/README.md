# Debug assets（本地大文件，不进 git）

本目录存放 **Debug 用** Live2D / ASR / TTS 模型。

- **禁止** commit 真实 `.onnx` / 妹居商业资源 / ASR·TTS 大包
- **禁止**把本目录当 AstrBot 服务器缓存交付
- Live2D 官方 Sample **已进** `app/src/main/assets/pet/live2d/Mao/`；本目录仅作脚本旁路 / 覆盖
- 在**开发者机器**拉取 ASR/TTS（可选 Live2D 覆盖）：

```bash
bash scripts/fetch-debug-assets.sh
# 或
bash scripts/download-debug-assets.sh
```

完整说明：[`docs/debug-assets.md`](../docs/debug-assets.md)

## 约定目录

```
debug-assets/
├── live2d/          # Mao.model3.json + 纹理等
├── asr/             # sherpa-onnx ASR 解压目录
└── tts/             # sherpa-onnx TTS 解压目录
```

真机：`adb push` 到 App `filesDir/debug-assets/`，设置页应显示「已就绪」。
