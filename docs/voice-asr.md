# 离线语音识别 ASR（Phase 6.4）

> 分支：`feat/phase6-4-offline-asr`  
> 状态：**6.4 🚧 骨架（接口 + stub + 配置 + 设置页 + 权限门控）**

## 目标

落地端侧 **Sherpa-ONNX 离线 ASR** 抽象层与 App 接入点，对齐 Phase 6.1 本地推理骨架风格：

- 默认关闭；未 load 不占用麦克风
- 无真实 so / 大模型也可 CI 绿
- Chat 只消费转写文本，不实现本地 LLM tool_call

## 能力边界

| 能力 | 状态 |
|------|------|
| `AsrEngine` 接口（load / unload / transcribe / streamPartial / isReady / isAvailable） | ✅ 6.4 |
| `StubAsrEngine`（无 sherpa so，路径校验 + stub 文本） | ✅ 6.4 |
| `SherpaOnnxBridge` JNI 接入点 | ✅ 6.4 预留 |
| DataStore 配置（启用 / 模型路径 / 语言 / 采样率） | ✅ 6.4 |
| `MicPermissionGate` + RECORD_AUDIO 申请流程 | ✅ 6.4 |
| `PcmAudioRecorder` stub PCM 链路 | ✅ 6.4 |
| `VoiceInputCoordinator`（Chat 可调用 API） | ✅ 6.4 |
| 设置页「离线语音识别」+ 试转写 | ✅ 6.4 |
| Chat「按住说话」完整 UI | 🔜 TODO（API 已预留） |
| 真实 sherpa-onnx so + 模型文件 | ❌ 后续（不进 git） |
| 云端 ASR 优先 | ❌ 本阶段聚焦离线骨架 |
| Bert-VITS2 TTS / 桌宠 | ❌ 6.5–6.7 |

## 产品规则

1. `offline_asr_enabled` **默认 `false`**
2. 关闭：不 load native/so、不初始化引擎、**不打开麦克风**
3. 开启后才可 load 模型；关闭时立即 unload
4. **不在后台偷偷录音**；仅用户显式触发（设置试转写 / 未来按住说话）
5. 权限拒绝：温柔文案，引导系统设置
6. ASR 输出文本交给现有发送消息链路；**不**走本地 tool_call，不阻塞 6.3 ChatRouter

## 模块结构

```
app/src/main/kotlin/com/lanxin/android/builtin/voice/
├── domain/
│   ├── AsrEngine.kt
│   ├── AsrSettings.kt
│   ├── AsrModels.kt
│   ├── MicPermissionGate.kt
│   └── VoiceInputCoordinator.kt
├── data/
│   ├── StubAsrEngine.kt
│   ├── SherpaOnnxBridge.kt
│   ├── AsrPreferences.kt
│   ├── AndroidMicPermissionChecker.kt
│   └── PcmAudioRecorder.kt
├── di/
│   └── VoiceModule.kt
└── presentation/
    ├── VoiceAsrScreen.kt
    └── VoiceAsrViewModel.kt

builtin/voice/README.md
docs/voice-asr.md   ← 本文
```

架构命名：`builtin/voice/`（ARCHITECTURE 约定）；实现包名 `com.lanxin.android.builtin.voice`。

## 配置键（DataStore）

| Key | 说明 | 默认 |
|-----|------|------|
| `offline_asr_enabled` | 启用离线 ASR | `false` |
| `offline_asr_model_path` | 模型路径（目录或文件） | 空 |
| `offline_asr_language` | 语言 | `zh` |
| `offline_asr_sample_rate_hz` | PCM 采样率 | `16000` |

## 模型档位建议（小模型优先）

| 档位 | 推荐 | 体量 | 定位 |
|------|------|------|------|
| **轻量（默认推荐）** | sherpa-onnx zipformer / paraformer **小** 中文流式或离线 | ~30–80MB | 省电、验证链路、日常指令 |
| **标准** | 中等中文 + 英文 bilingual | ~100–200MB | 更好准确率 |
| 不推荐默认 | 超大 multilingual 全量 | 过大 | 仅极客自测 |

放置目录建议：

```
{filesDir}/models/offline-asr/
├── light/      # 小模型
├── standard/   # 中等
└── tokens / encoder / decoder 等附属文件
```

单测可用虚拟路径：`stub://demo-asr`（`SherpaOnnxBridge` 认作合法）。

## 路径约定与分发

- **禁止**提交真实 `.so`、ONNX 权重、tokens 大文件到 git
- 真机：`adb push` 到 `filesDir/models/offline-asr/` 或外部存储后在设置页填绝对路径
- 后续可文档化下载脚本；本 PR 不打包进 APK

## Chat 接入（边界）

```
用户按住说话（TODO UI）
        │  preflightForRecording() → 权限 / 引擎
        ▼
录音 → PCM（PcmAudioRecorder / AudioRecord）
        │
        ▼
VoiceInputCoordinator.transcribePcm(...)
        │
        ▼
识别文本 → 填入 Chat 输入框 / 直接 sendMessage
        │
        └─ 不调用 LocalLlmEngine tool_call；路由仍走 ChatRouter
```

P0：设置页「试转写」用 **stub PCM**（不强制真麦），演示引擎契约。

## Sherpa-ONNX 接入路线（后续）

1. 引入 `libsherpa-onnx-jni.so` 或官方 AAR + CMake  
2. 实现 `SherpaOnnxBridge.loadModel` / `transcribe`  
3. 新增 `SherpaAsrEngine`，Hilt `@Binds` 替换 stub  
4. 真机验证流式 partial  

参考：

- https://github.com/k2-fsa/sherpa-onnx  
- 妹居 MeiJu 语音封装  

## 非目标（6.4）

- 真实 so / 模型入库  
- 完整 Chat 按住说话 UI（可标 TODO）  
- TTS / 桌宠 / 场景感知  
- 修改 AstrBot 系统源码  
- 云端 ASR  

## 单测

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.lanxin.android.builtin.voice.*"
```
