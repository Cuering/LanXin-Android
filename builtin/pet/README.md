# builtin/pet — 桌宠 / 语音陪伴

Phase 6 主线：**M1 ✅** 骨架 · **M2a ✅** 路径就绪 · **M2b ✅** Live2D 壳 · **M2b 打磨 ✅** 表情/口型。

实现：`app/src/main/kotlin/com/lanxin/android/builtin/pet/`

- 设计：[`docs/meiju-style-pet.md`](../../docs/meiju-style-pet.md)
- 产品一体：与 Phase 7 系统工具合并为「陪伴操控一体」（见 ARCHITECTURE Phase 7.4）
- Debug 资源：[`docs/debug-assets.md`](../../docs/debug-assets.md)

## 状态

- ✅ 悬浮层 Service + WebView 占位 HTML
- ✅ DesktopPetBridge / AndroidVoiceBridge
- ✅ VoiceSession：IDLE → LISTENING → THINKING → SPEAKING → IDLE
- ✅ Stub TTS + Stub 回复；设置页试运行
- ✅ Debug 资源脚本：`scripts/fetch-debug-assets.sh` / `download-debug-assets.sh`
- ✅ **M2a**：`PetPathReadiness` + 设置页「已就绪 / 未就绪」+ 本地脑 1.5B 键说明
- ✅ **M2b**：`Live2dDisplayController` + WebView 渲染壳 + 缺资源 fallback
- ✅ **M2b 打磨**：`PetExpressionController` + `SET_EXPRESSION` + 口型动画 + 停止复位会话
- 🔜 M2c sherpa 可 load · M3 真 TTS · M4 合规 L2D · M5 场景

## 入口

设置 → **桌宠 / 语音陪伴**

## Debug 资源（走开源）

```bash
bash scripts/fetch-debug-assets.sh   # 推荐
# 或 bash scripts/download-debug-assets.sh
```

| 能力 | 推荐 | 配置键 |
|------|------|--------|
| Live2D | Niziiro Mao（官方 Sample） | `live2d_model_path` |
| ASR | sherpa zipformer zh-14M | `offline_asr_model_path` |
| TTS | matcha-icefall-zh-baker | `tts_model_dir` |
| 本地脑 | Qwen2.5-1.5B（自备） | `local_inference_model_path` |

**下载只在开发者机器 / 按需 CI**；不在 AstrBot 服务器拉模型。缺失时设置页引导脚本，App 内不 curl。

## 红线

不提交妹居 so / 模型 / moc3 / 商业人设。参考包仅本机。
