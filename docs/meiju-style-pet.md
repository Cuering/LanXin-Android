# 妹居风格桌宠 + 语音会话（Phase 6 主线）

> 分支：`feat/m2b-pet-expression-polish`（M2b 打磨）  
> 状态：**M1 ✅**（#48）· **M2a ✅**（#49）· **M2b ✅**（#57）· **M2b 打磨 ✅ 表情/口型 + 降级引导**  
> 参考包（**仅本机分析，禁止入库**）：`/AstrBot/data/workspaces/妹居2.2.2版本.apk`

## 1. 产品主线

> **与 Phase 7 交叉：** 桌宠 VoiceSession 与系统工具（日历/闹钟/笔记/文件）合并为 **「陪伴操控一体」**——感官在桌宠，行动在 ToolRegistry。见 `ARCHITECTURE.md` Phase 7.4、`docs/system-tools.md`。


**不要**优先做「文本 Chat 按住说话填输入框」。

**要**妹居级体验：

```
Live2D/占位桌宠  +  语音听/说  +  对话  +  （后续）场景感知
```

会话路径：

```
IDLE → LISTENING → THINKING → SPEAKING → IDLE
```

- 输入：ASR 文本（M1/M2a 可 stub；真引擎 M2c）
- 思考：`PetChatResponder`（stub；后续 ChatRouter / 云端 / 本地 1.5B）
- 输出：TTS + **桌宠气泡**（**不**塞 Chat 输入框）

## 2. 阶段状态

| 阶段 | 内容 | 状态 |
|------|------|------|
| **M1** | 悬浮层 + WebView 占位 + Bridge + VoiceSession + stub TTS | ✅ main `#48` |
| **M2a** | 真资源路径闭环 + 设置「已就绪/缺失」+ fetch 脚本文案 + 本地脑 1.5B 键说明 | ✅ main `#49` |
| **M2b** | Live2D 真显示（WebView 渲染壳 + model3/纹理，失败降级） | ✅ main `#57` |
| **M2b 打磨** | 会话相位→表情/口型 + 缺资源引导 + 悬浮生命周期 | ✅ 本分支 |
| **M2c** | sherpa ASR/TTS 可 load 文件则 READY（无 so 仍 stub） | 后续 |
| **M3** | 真 TTS + 口型 | 后续 |
| **M4** | 自有/授权 Live2D | 后续 |
| **M5** | 场景感知（显式授权） | 后续 |

## 3. M2a 交付

### 3.1 路径就绪

- `PetPathReadiness`：Live2D 文件 / ASR·TTS 非空目录 / `stub://` → **已就绪**
- 空或无效 → **未就绪 / 路径无效**，明细指向 `scripts/fetch-debug-assets.sh`
- Debug 自动解析优先级：用户配置 → `filesDir/debug-assets/**`（开源）→ `meiju-ref/**`（仅本机）

### 3.2 设置页

- 展示 Live2D / ASR / TTS 来源标签 + 就绪短标签
- 缺失时按钮文案说明跑 **GitHub 仓库脚本**（**不在 App / AstrBot 服务器下载**）
- 预留 `local_inference_model_path` 与 **Qwen2.5-1.5B** 说明（链 `docs/local-inference.md`）

### 3.3 脚本

| 脚本 | 说明 |
|------|------|
| `scripts/fetch-debug-assets.sh` | M2 推荐入口（封装 `download-debug-assets.sh`） |
| `scripts/download-debug-assets.sh` | 一键 Live2D + ASR + TTS |
| 分项 `download-debug-live2d/asr/tts.sh` | 同上 |

**硬性**：下载只在开发者机器或按需 CI；禁止 AstrBot 服务器 `/data/download` 当交付。

## 4. 妹居 2.2.2 → 兰心 映射

| 妹居（APK 观察） | 兰心 | 备注 |
|------------------|------|------|
| `assets/desktop-pet.html` + Pixi Live2D | `assets/pet/desktop-pet.html` 自有占位 | **重写**，不复制商业资源 |
| `FloatingWindowManager` | `FloatingPetService` | `SYSTEM_ALERT_WINDOW` |
| `DesktopPetBridge` / `AndroidVoiceBridge` | 同名 Bridge | M1 |
| Sherpa ASR | 6.4 `AsrEngine` + 路径就绪 M2a | M2c 真 load |
| Bert-VITS2 | `TtsEngine` + stub | M3 |
| MNN 本地脑 | 6.1–6.3；默认选型 **1.5B** | 权重自备 |
| 商业 L2D / 人设 | **禁止入库** | 红线 |

## 5. 模块边界

```
builtin/pet/          overlay + web assets + bridge + VoiceSession*
app/.../builtin/pet/
  domain/             StateMachine / Coordinator / PathReadiness / DebugOpenSourcePaths
  data/               FloatingPetService / Bridges / PetPreferences
  presentation/       DesktopPetScreen / ViewModel

builtin/voice/        ASR + TtsEngine / StubTtsEngine
```

## 6. 产品规则

1. `desktop_pet_enabled` **默认 `false`**
2. 关闭：不启 Service、不跑会话、不打开麦克风
3. 悬浮权限：设置页显式引导
4. **不**后台偷录、**不**偷偷截屏
5. 会话结果只走桌宠气泡，**不**作主路径写入 Chat 输入框
6. 模型目录：App `filesDir` / `externalFiles`，不进系统目录

## 7. 资源红线

- **禁止**妹居 so / moc3 / mnn / wav / 商业人设 **提交 git**
- **禁止**在 AstrBot 服务器 curl 模型当交付
- Debug 走开源：Live2D Mao + sherpa ASR/TTS → `docs/debug-assets.md`

## 8. 单测

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.lanxin.android.builtin.pet.*" \
  --tests "com.lanxin.android.builtin.voice.StubTtsEngineTest"
# 含 Live2dDisplayControllerTest / PetExpressionControllerTest
```

## 9. M2b 交付

### 9.1 显示模式

- `Live2dDisplayController`：空路径 → **占位**；model3 可读有效 → **LIVE2D_SHELL**；无效 → **FALLBACK**
- `FloatingPetService` 推送 `LOAD_LIVE2D`（路径 + file:// URL + mode）
- `desktop-pet.html`：壳内 Canvas 叠纹理 / 几何呼吸；fetch model3 失败自动降级
- 设置页展示「显示：Live2D 壳 / 占位 / 降级」

### 9.2 设置与 M2a 一致

- 仍用 `live2d_model_path` + `PetPathReadiness` + fetch 脚本引导
- 换路径不改 VoiceSession 状态机

### 9.3 非目标（M2b）

- 完整 Cubism Core / 口型同步（M3）
- 妹居商业 moc3 / 资源入库
- 重做 VoiceSession
- CI 下载大模型
- auto-merge / force-push main

## 10. M2b 打磨交付

### 10.1 表情 / 口型闭环

- `PetExpressionController`：`VoiceSessionPhase` → `Expression` + `mouthOpen` / `mouthAnimating`
- Bridge：`SET_EXPRESSION`；`SESSION_STATE` 同步携带 expression 字段
- `desktop-pet.html`：占位嘴型 CSS 动画 + Live2D 壳 canvas 嘴型；SPEAKING 时开合
- 设置页展示当前「表情：…」与缺资源引导（仍可占位演示听→想→说）

### 10.2 生命周期

- `FloatingPetService` collect 会话快照推表情；`onDestroy` 复位会话、`__lanxinPetTeardown` 停 rAF、destroy WebView

### 10.3 非目标

- Cubism Core 真表情参数 / 音素级口型（M3）
- 下大模型、妹居资源入库
- 重做 VoiceSession / Chat
