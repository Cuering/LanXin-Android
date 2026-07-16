# 妹居风格桌宠 + 语音会话（Phase 6 主线 M1）

> 分支：`feat/phase6-pet-voice-session`  
> 状态：**M1 骨架（悬浮层 + WebView 占位 + Bridge + VoiceSession 状态机 + stub TTS/回复）**  
> 参考包（**仅本机分析，禁止入库**）：`/AstrBot/data/workspaces/妹居2.2.2版本.apk`

## 1. 产品主线

**不要**优先做「文本 Chat 按住说话填输入框」。

**要**妹居级体验：

```
Live2D/占位桌宠  +  语音听/说  +  对话  +  （后续）场景感知
```

会话路径：

```
IDLE → LISTENING → THINKING → SPEAKING → IDLE
```

- 输入：ASR 文本（M1 可 stub）
- 思考：`PetChatResponder`（stub；后续 ChatRouter / 云端）
- 输出：TTS + **桌宠气泡**（**不**塞 Chat 输入框）

## 2. 妹居 2.2.2 → 兰心 映射

| 妹居（APK 观察） | 兰心 M1 | 备注 |
|------------------|---------|------|
| `assets/desktop-pet.html` + Pixi Live2D | `assets/pet/desktop-pet.html` 自有占位 | **重写**，不复制商业资源 |
| `FloatingWindowManager` / `AndroidDesktopPet` | `FloatingPetService` + WindowManager | 需 `SYSTEM_ALERT_WINDOW` |
| `DesktopPetBridge` | `DesktopPetBridge` `@JavascriptInterface` | 命名对齐概念 |
| `AndroidVoiceBridge` / `RealtimeVoiceBridge` | `AndroidVoiceBridge` | start/stop/getPhase |
| Sherpa ASR so（assets/tts 路径下） | 复用 6.4 `AsrEngine` / stub | M2 真接入 |
| Bert-VITS2 + `libbertvits2.so` | `TtsEngine` + `StubTtsEngine` | M3 真接入 |
| MNN 本地脑 | 已有 6.1–6.3 LocalLlm + ChatRouter | 思考阶段可后续接 |
| `sister_android.json` 人设结构 | stub 短句回复；自有人设 | **禁止**抄商业角色全文 |
| `assets/assets/L2D/**` moc3 | 不提交；M4 自有/授权模型 | 红线 |
| UsageStats / 截屏 | 接口预留 | M5 / 6.7 |

## 3. 模块边界

```
builtin/pet/          overlay + web assets + bridge + VoiceSession*
app/.../builtin/pet/
  domain/             VoiceSessionStateMachine / Coordinator / BridgeProtocol
  data/               FloatingPetService / Bridges / PetPreferences
  di/                 PetModule
  presentation/       DesktopPetScreen / ViewModel

builtin/voice/        6.4 ASR + M1 TtsEngine / StubTtsEngine
```

## 4. 产品规则

1. `desktop_pet_enabled` **默认 `false`**
2. 关闭：不启 Service、不跑会话、不打开麦克风
3. 悬浮权限：设置页显式引导，不偷偷弹
4. **不**后台偷录、**不**偷偷截屏
5. 会话结果只走桌宠气泡 / Bridge，**不**作为主路径写入 Chat 输入框

## 5. 资源红线

- **禁止**把妹居 APK 内 so / 模型 / moc3 / wav / 商业人设 **提交进 git**
- 可：文档记录结构、接口名；自有 HTML；stub 引擎；本机 `unzip -l` 学习
- `.gitignore` 已覆盖 `*.mnn` / ASR 模型等

### 5.1 Debug 默认走开源（非妹居）

真机/集成测试资源请用官方/开源栈，**不要**默认依赖妹居解包：

| 能力 | 推荐 | 脚本 |
|------|------|------|
| Live2D | Niziiro Mao / Haru（Live2D Sample） | `scripts/download-debug-live2d.sh` |
| ASR | sherpa-onnx zipformer zh-14M | `scripts/download-debug-asr.sh` |
| TTS | matcha-baker / melo（sherpa-onnx） | `scripts/download-debug-tts.sh` |

一键：`bash scripts/download-debug-assets.sh`  
完整许可、体积、路径约定：[`docs/debug-assets.md`](./debug-assets.md)

妹居 APK **仅**本机架构对照；设置页与脚本默认路径全部指向 `debug-assets/`。

## 6. 设置入口

设置 → **桌宠 / 语音陪伴**

- 总开关 / 悬浮权限 / 启动·停止桌宠
- 会话状态预览
- 试运行：stub 一轮「听→想→说」

## 7. 下一刀

| 阶段 | 内容 |
|------|------|
| **M2** | 真 ASR 接到 VoiceSession（Sherpa，复用 6.4） |
| **M3** | 真 TTS（Bert-VITS2 风格）+ 口型驱动 WebView |
| **M4** | 自有/授权 Live2D 替换占位 |
| **M5** | 场景感知（UsageStats / 截屏，显式授权） |

## 8. 单测

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.lanxin.android.builtin.pet.*" \
  --tests "com.lanxin.android.builtin.voice.StubTtsEngineTest"
```

## 9. 非目标（M1）

- 提交妹居 so / 模型 / L2D
- 真截屏 UsageStats
- 完整 Bert-VITS2 / 真 Sherpa 接入
- 复制妹居 Yuki 等商业人设全文
