# builtin/pet — 桌宠 / 语音陪伴

Phase 6 主线 M1：妹居风格桌宠悬浮层 + VoiceSession 状态机。实现位于：

`app/src/main/kotlin/com/lanxin/android/builtin/pet/`

设计文档：[`docs/meiju-style-pet.md`](../../docs/meiju-style-pet.md)

## 状态

- ✅ 悬浮层 Service + WebView 占位 HTML
- ✅ DesktopPetBridge / AndroidVoiceBridge
- ✅ VoiceSession：IDLE → LISTENING → THINKING → SPEAKING → IDLE
- ✅ Stub TTS + Stub 回复；设置页试运行
- 🔜 M2 真 ASR · M3 真 TTS · M4 Live2D · M5 场景感知

## 入口

设置 → **桌宠 / 语音陪伴**

## 红线

不提交妹居 so / 模型 / moc3 / 商业人设。参考包仅本机：`/AstrBot/data/workspaces/妹居2.2.2版本.apk`
