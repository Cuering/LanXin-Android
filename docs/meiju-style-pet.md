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
| **M2b 打磨** | 会话相位→表情/口型 + 缺资源引导 + 悬浮生命周期 | ✅ main `#63` |
| **仓内 Live2D Mao** | 官方 Sample 进 assets，release 默认可开箱 | ✅ main |
| **陪伴页 BGM** | 右下角 🎵 + `LanXin/music/` + 节拍轻晃 | ✅ 本分支 |
| **M2c** | sherpa ASR/TTS 可 load 文件则 READY（无 so 仍 stub） | 后续 |
| **M3** | 真 TTS + 口型 | 后续 |
| **M4** | 自有/授权 Live2D | 后续 |
| **M5** | 场景识别：摄像头快照 + 确认 Gate → 现有背景/mood（最小） | ✅ 本分支 · `docs/scene-sensing.md` |

### 2.1 陪伴页背景音乐

- 右下角半透明 🎵；面板：播放/暂停、上一/下一、音量、导入、扫描
- 目录：`LanXin/music/`（与 live2d/asr/tts 同根）
- 内置 CC0 `test-loop.wav`（`BuiltInMusicAssets.ensureTestTrackInstalled`）
- MediaPlayer 播放列表（无节拍跟随 / Visualizer）

### 2.2 陪伴页换背景

- 右下角 🖼（音乐上方）；面板：6 个预设渐变 + 导入图片
- 预设零资源（Compose 渐变）；自定义图落盘 `LanXin/backgrounds/`
- DataStore：`companion_bg_preset_id` / `companion_bg_custom_path`
- 渲染在 Compose 底层，WebView 透明叠上（不进 HTML/Cubism）

### 2.3 文本 → 表情 / 动作

- **mood 标签优先**：`[[mood=joy]]` 等 → `MoodTagMapper`（枚举从 Mao exp/motion **反推**，不发明资源）
- **关键词兜底**：`TextExpressionMotionMapper`（#97）
- 命中后覆盖 `SET_EXPRESSION`（exp_01…08）+ 可选 `PLAY_MOTION`（Idle / TapBody）
- mood：smile / listen / think / speak / sorry / idle / joy / music / tap（+ 少量别名）
- 关键词：apology / joy / music / tap_invite / think_tone / greeting / sad / idle_variant
- 气泡 / TTS / 历史 `stripTags`；接线悬浮层 / 陪伴页 / 设置页
- 提示词：正文前可加 `[[mood=…]]`（仅允许表内 mood），本地脑/云端同协议
- 同一 `roundId:ruleId` 只推一次 motion，防 snapshot 重复 collect 连播

## 3. M2a 交付

### 3.1 路径就绪

- `PetPathReadiness`：Live2D 文件 / ASR·TTS 非空目录 / `stub://` → **已就绪**
- Live2D 仓内 Sample（逻辑路径 / 已安装）→ **已就绪（内置示例）**
- ASR/TTS 空或无效 → **未就绪 / 路径无效**，明细引导 **App 内一键下载**（脚本可选）
- Live2D 解析优先级：
  1. 用户配置 `live2d_model_path`
  2. 仓内官方 Sample（`BuiltInLive2dAssets` / `filesDir/builtin-live2d/Mao/`）
  3. `LanXin/**`（App 内下载或脚本旁路；公共存储优先）
  4. `meiju-ref/**`（仅本机 debug）

### 3.2 设置页

- 展示 Live2D / ASR / TTS 来源标签 + 就绪短标签
- **Live2D 模型列表与切换**（`Live2dModelCatalog`）
  - 扫描 `LanXin/live2d/*/`（含 `*.model3.json`）+ 内置 Mao + 兼容 `filesDir/debug-assets/live2d/`
  - 点选写 `live2d_model_path`；桌宠运行中推送 `RELOAD_LIVE2D`
  - 导入 model3 / 文件夹 → 落盘 `LanXin/live2d/<name>/`
  - 可选「同步内置到目录」将 Mao 导出到同目录，文件管理器可找
- **App 内一键下载**（推荐）：Live2D Mao / ASR zipformer-14M / TTS matcha-baker
  - 镜像：jsDelivr / HF / hf-mirror 优先，官方 GitHub 回退（旧 ghproxy 已弃用）
  - 进度、可取消、失败短文案；落盘 `LanXin/**` 后写配置键
  - Live2D 仓内 Mao 仍默认就绪；下载可作更新/覆盖
- 本地脑：App 内一键下载到 `LanXin/models/local-llm/light/`（可选）
- 可选：高级手填路径 / 开发者脚本说明

### 3.3 脚本（可选旁路）

| 脚本 | 说明 |
|------|------|
| `scripts/vendor-live2d-mao.sh` | 将官方 CubismWebSamples Mao 同步到 `app/src/main/assets/pet/live2d/Mao/` |
| `scripts/fetch-debug-assets.sh` | 开发者机 ASR/TTS（可选 Live2D 覆盖）入口 |
| `scripts/download-debug-assets.sh` | 同上完整下载 |
| 分项 `download-debug-live2d/asr/tts.sh` | 分项 |

**硬性**：大文件不进 git；**禁止** AstrBot 服务器 `/data/download` 当交付缓存。推荐手机 App 内下载。

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
- Live2D 官方 Sample Mao **仓内** `assets/pet/live2d/Mao/` → `docs/live2d-mao-sample.md`
- ASR/TTS 走开源脚本 → `docs/debug-assets.md`（大包不进仓）

## 8. 单测

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.lanxin.android.builtin.pet.*" \
  --tests "com.lanxin.android.builtin.voice.StubTtsEngineTest"
# 含 BuiltInLive2dAssetsTest / Live2dDisplayControllerTest /
# PetPathReadinessTest / PetExpressionControllerTest
```

## 9. M2b 交付

### 9.1 显示模式

- `Live2dDisplayController`：空路径 → **占位**；model3 可读有效 → **LIVE2D_SHELL**；无效 → **FALLBACK**
- `FloatingPetService` 推送 `LOAD_LIVE2D`（路径 + file:// URL + mode）
- `desktop-pet.html`：壳内 Canvas 叠纹理 / 几何呼吸；fetch model3 失败自动降级
- 设置页展示「显示：Live2D 壳 / 占位 / 降级」

### 9.2 设置与 M2a 一致

- `live2d_model_path` 可空 → 内置 Mao；`PetPathReadiness` 标「已就绪（内置示例）」；ASR/TTS 仍靠脚本
- 换路径不改 VoiceSession 状态机
- 设置页模型列表切换 / 导入后运行中桌宠立即 `LOAD_LIVE2D`

### 9.3 非目标（M2b）

- ~~完整 Cubism Core~~ → **P3 已落地**（见 §11）
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

- 音素级口型（后续）
- 下大模型、妹居资源入库
- 重做 VoiceSession / Chat

## 11. P3 Cubism 真渲染

- 仓内 `assets/pet/lib/`：Cubism Core + PixiJS 6 + pixi-live2d-display cubism4
- `desktop-pet.html`：`LOAD_LIVE2D` → **LIVE2D_REAL**（`Live2DModel.from`）→ 失败 **LIVE2D_SHELL** 纹理壳 → 再失败占位
- 表情 / 口型：`SET_EXPRESSION` → exp + `ParamMouthOpenY`；BGM 节拍轻晃保留
- 模型仍外置 `LanXin/live2d/` + 内置 Mao 兜底；大资源不进 git
- 文档：[`live2d-cubism-render.md`](./live2d-cubism-render.md)
- CI：`.github/workflows/p3-live2d-cubism-verify.yml`
