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
- ✅ **仓内 Live2D Sample**：`assets/pet/live2d/Mao/`（官方 Niziiro Mao，~4MB）
- ✅ Debug 资源脚本：`scripts/fetch-debug-assets.sh` / `download-debug-assets.sh`（ASR/TTS / 自定义覆盖）
- ✅ **M2a**：`PetPathReadiness` + 设置页「已就绪 / 未就绪」+ 本地脑 1.5B 键说明
- ✅ **M2b**：`Live2dDisplayController` + WebView 渲染壳 + 缺资源 fallback
- ✅ **M2b 打磨**：`PetExpressionController` + `SET_EXPRESSION` + 口型动画 + 停止复位会话
- 🔜 M2c sherpa 可 load · M3 真 TTS · M4 合规 L2D · M5 场景

## 入口

设置 → **桌宠 / 语音陪伴**

## Live2D 内置 Sample

| 项 | 说明 |
|----|------|
| 路径 | `app/src/main/assets/pet/live2d/Mao/` |
| 许可 | [Sample Data Terms](https://www.live2d.com/en/learn/sample/model-terms) |
| 运行时 | `BuiltInLive2dAssets.ensureInstalled` → `filesDir/builtin-live2d/Mao/` |
| 默认 | 配置空时解析为内置逻辑路径 / 已安装绝对路径 |
| 重同步 | `bash scripts/vendor-live2d-mao.sh` |

详见 [`docs/live2d-mao-sample.md`](../../docs/live2d-mao-sample.md)。

## Debug 资源（ASR/TTS 等走开源）

```bash
bash scripts/fetch-debug-assets.sh   # ASR/TTS / 可选覆盖 Live2D
# 或 bash scripts/download-debug-assets.sh
```

| 能力 | 推荐 | 配置键 |
|------|------|--------|
| Live2D | **仓内** Niziiro Mao（优先） | `live2d_model_path`（可空） |
| ASR | sherpa zipformer zh-14M | `offline_asr_model_path` |
| TTS | matcha-icefall-zh-baker | `tts_model_dir` |
| 本地脑 | Qwen2.5-1.5B（自备） | `local_inference_model_path` |

**ASR/TTS 大包不进 git**；仅开发者机脚本（本阶段**无** App 内一键下载）。禁止 AstrBot 服务器缓存模型当交付。

## 红线

不提交妹居 so / 模型 / 商业 moc3 / 人设。官方 Sample Mao 为唯一仓内 Live2D 白名单。
