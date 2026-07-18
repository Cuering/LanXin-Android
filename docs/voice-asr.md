# 离线语音识别 ASR（Phase 6.4 + P0 真引擎）

> 分支：`feat/p0-sherpa-asr-native`  
> 状态：**P0 ✅ 官方 AAR 进包 + `SherpaOnnxBridge` 真调用 + Hilt 真引擎（失败降级 stub）**

## 目标

落地端侧 **Sherpa-ONNX 离线 ASR**：

- 默认关闭；未 load 不占用麦克风
- **运行时 so 进 APK**（官方 AAR，构建期下载，不进 git）
- **模型权重外置** `LanXin/asr/...`（不进 git）
- 无 so / 无模型时 CI 与设置页仍可用（stub 降级）

## 能力边界

| 能力 | 状态 |
|------|------|
| `AsrEngine` 接口（load / unload / transcribe / streamPartial / isReady / isAvailable） | ✅ |
| `StubAsrEngine`（单测 / 对照） | ✅ 保留 |
| `SherpaAsrEngine`（Hilt 默认绑定） | ✅ P0 |
| `SherpaOnnxBridge` 真 `loadModel` / `transcribe` / `unload` | ✅ P0 |
| 官方 `sherpa-onnx-static-link-onnxruntime-*.aar` 构建期下载 | ✅ P0 |
| DataStore 配置（启用 / 模型路径 / 语言 / 采样率） | ✅ |
| `MicPermissionGate` + RECORD_AUDIO | ✅ |
| `VoiceInputCoordinator` | ✅ |
| 设置页「离线语音识别」+ 试转写 | ✅ |
| Chat「按住说话」完整 UI | 🔜 |
| 模型文件入库 | ❌ 禁止（外置下载） |
| 云端 ASR 优先 | ❌ 本阶段聚焦离线 |

## 产品规则

1. `offline_asr_enabled` **默认 `false`**
2. 关闭：不 load native、不初始化引擎、**不打开麦克风**
3. 开启后才可 load 模型；关闭时立即 unload
4. **不在后台偷偷录音**；仅用户显式触发
5. 权限拒绝：温柔文案，引导系统设置
6. ASR 输出文本交给现有发送消息链路；**不**走本地 tool_call

## 引擎选择（Hilt）

```
VoiceModule.bindAsrEngine → SherpaAsrEngine
                              │
                              ├─ stub:// 路径 → READY + stub 文本（单测）
                              ├─ native 可用且模型布局可识别 → READY + 真转写（isStub=false）
                              └─ 路径合法但 so/load 失败 → READY + stub 降级
                                   lastError = native_degraded:…
```

## 模块结构

```
app/src/main/kotlin/com/lanxin/android/builtin/voice/
├── domain/ … AsrEngine, VoiceInputCoordinator, …
├── data/
│   ├── StubAsrEngine.kt          # 对照 / 单测
│   ├── SherpaAsrEngine.kt        # Hilt 绑定
│   ├── SherpaOnnxBridge.kt       # JNI / AAR API
│   └── …
├── di/VoiceModule.kt
└── presentation/ …

app/libs/                         # gitignore *.aar；构建期下载
third_party/sherpa-onnx/NOTICE    # 许可证声明
```

## 运行时打包

| 项 | 说明 |
|----|------|
| AAR | `sherpa-onnx-static-link-onnxruntime-1.13.4.aar` |
| 任务 | `./gradlew :app:downloadSherpaOnnxAar` |
| 环境变量 | `SHERPA_ONNX_AAR`（本地文件）/ `SHERPA_ONNX_AAR_URL` |
| ABI | `arm64-v8a`, `x86_64`（`ndk.abiFilters`） |
| so 清单（static-link 变体） | `libsherpa-onnx-jni.so`（内链 ORT；部分 ABI 可能另带 `libonnxruntime.so`） |
| 与 ORT Mobile | `packaging.jniLibs.pickFirsts` 处理重复 `libonnxruntime.so` |
| 许可证 | Apache-2.0 → `third_party/sherpa-onnx/NOTICE` |

**验收 APK 内引擎：**

```bash
# 解压或 aapt/apkanalyzer
unzip -l app-debug.apk | grep -E 'sherpa|onnxruntime'
# 期望含 libsherpa-onnx-jni.so（arm64-v8a / x86_64）
```

## 配置键（DataStore）

| Key | 说明 | 默认 |
|-----|------|------|
| `offline_asr_enabled` | 启用离线 ASR | `false` |
| `offline_asr_model_path` | 模型目录绝对路径 | 空 |
| `offline_asr_language` | 语言 | `zh` |
| `offline_asr_sample_rate_hz` | PCM 采样率 | `16000` |

## 模型布局（外置 `LanXin/asr/...`）

### 流式 zipformer transducer（默认推荐）

```
LanXin/asr/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/
  encoder-*.onnx   # 优先 *int8*
  decoder-*.onnx
  joiner-*.onnx
  tokens.txt
```

### 离线 paraformer

```
LanXin/asr/sherpa-onnx-paraformer-zh-small-2024-03-09/
  model*.onnx / paraformer*.onnx
  tokens.txt
```

下载：

```bash
bash scripts/download-debug-asr.sh
# 或 App 内 Debug 资源下载 → 写 offline_asr_model_path
```

单测虚拟路径：`stub://demo-asr`。

## 真机步骤

1. 安装含 sherpa so 的 Debug APK  
2. 下载模型到公共 `LanXin/asr/<dir>/`（或 App externalFiles 回退）  
3. 设置 → 离线语音识别 → 打开开关 → 填模型目录  
4. 加载成功：`engineState=READY`；`lastError` 空（native）或 `native_degraded:…`（降级）  
5. 试转写：native 成功时 `isStub=false` 且为识别文本；否则 `[asr-stub] …`

## Chat 接入（边界）

```
录音 PCM → VoiceInputCoordinator.transcribePcm → 文本
         → 不调用 LocalLlmEngine tool_call
```

## 单测

```bash
./gradlew :app:downloadSherpaOnnxAar
./gradlew :app:testDebugUnitTest \
  --tests "com.lanxin.android.builtin.voice.*"
```

- JVM **无 so**：`isNativeAvailable()==false`；`SherpaAsrEngine` + `StubAsrEngine` 不崩  
- instrumented：真机 load 外置模型（可选，见 CI 说明）

## 参考

- https://github.com/k2-fsa/sherpa-onnx  
- Release AAR: https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.4  
- [`docs/debug-assets.md`](./debug-assets.md)
