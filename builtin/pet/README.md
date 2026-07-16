# builtin/pet — 桌宠 / 语音陪伴

Phase 6 主线 M1：妹居风格桌宠悬浮层 + VoiceSession 状态机。实现位于：

`app/src/main/kotlin/com/lanxin/android/builtin/pet/`

设计文档：[`docs/meiju-style-pet.md`](../../docs/meiju-style-pet.md)  
Debug 开源资源（Live2D 官方 sample + sherpa ASR/TTS）：[`docs/debug-assets.md`](../../docs/debug-assets.md)

## 状态

- ✅ 悬浮层 Service + WebView 占位 HTML
- ✅ DesktopPetBridge / AndroidVoiceBridge
- ✅ VoiceSession：IDLE → LISTENING → THINKING → SPEAKING → IDLE
- ✅ Stub TTS + Stub 回复；设置页试运行
- ✅ Debug 资源封装：`scripts/download-debug-assets.sh`（大文件不进 git）
- 🔜 M2 真 ASR · M3 真 TTS · M4 Live2D · M5 场景感知

## 入口

设置 → **桌宠 / 语音陪伴**

## Debug 资源（走开源，默认）

```bash
bash scripts/download-debug-assets.sh
```

| 能力 | 推荐 | 配置键 |
|------|------|--------|
| Live2D | Niziiro Mao / Haru（官方 Sample） | `live2d_model_path` → `*.model3.json` |
| ASR | sherpa zipformer zh-14M | `offline_asr_model_path` |
| TTS | matcha-icefall-zh-baker / melo | `tts_model_dir` |

缺失时使用 `desktop-pet.html` 占位 + stub 引擎。详见 `docs/debug-assets.md`。

## 红线

不提交妹居 so / 模型 / moc3 / 商业人设。参考包仅本机：`/AstrBot/data/workspaces/妹居2.2.2版本.apk`
