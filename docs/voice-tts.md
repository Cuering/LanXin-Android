# 离线语音合成 TTS（P1 真引擎）

> 分支：`feat/p1-tts-native`  
> 状态：**P1 ✅ 复用 sherpa-onnx AAR OfflineTts + `SherpaTtsBridge` + Hilt 真引擎（失败降级 stub）**

## 目标

落地端侧 **Sherpa-ONNX Offline TTS**：

- 默认关闭（`tts_enabled=false`）
- **运行时 so 进 APK**（与 P0 ASR **同一**官方 AAR，构建期下载，不进 git）
- **模型权重外置** `LanXin/tts/...`（不进 git）
- 无 so / 无模型时 CI 与桌宠会话仍可用（stub 降级：空 PCM + 字幕）

## 能力边界

| 能力 | 状态 |
|------|------|
| `TtsEngine` 接口（load / unload / synthesize / isReady / isAvailable） | ✅ |
| `StubTtsEngine`（单测 / 对照） | ✅ 保留 |
| `SherpaTtsEngine`（Hilt 默认绑定） | ✅ P1 |
| `SherpaTtsBridge` 真 `loadModel` / `synthesize` / `unload` | ✅ P1 |
| Matcha（baker）+ VITS 布局探测 | ✅ |
| 官方 AAR 构建期下载 | ✅ P0 已有（P1 复用） |
| DataStore 配置 | ✅ |
| 模型文件入库 | ❌ 禁止（外置下载） |
| Bert-VITS2 独立 so | ❌ 非本阶段 |

## 引擎选择（Hilt）

```
VoiceModule.bindTtsEngine → SherpaTtsEngine
                              │
                              ├─ enabled + 空路径 → READY + stub 字幕
                              ├─ stub:// 路径 → READY + stub
                              ├─ native 可用且布局可识别 → READY + 真 PCM（isStub=false）
                              └─ 路径合法但 so/load 失败 → READY + stub 降级
                                   lastError = native_degraded:…
```

## 模块结构

```
app/src/main/kotlin/com/lanxin/android/builtin/voice/
├── domain/TtsEngine.kt
├── data/
│   ├── StubTtsEngine.kt          # 对照 / 单测
│   ├── SherpaTtsEngine.kt        # Hilt 绑定
│   ├── SherpaTtsBridge.kt        # OfflineTts JNI
│   └── …
└── di/VoiceModule.kt

app/libs/                         # 同 P0 AAR
third_party/sherpa-onnx/NOTICE
```

## 运行时打包

| 项 | 说明 |
|----|------|
| AAR | `sherpa-onnx-static-link-onnxruntime-1.13.4.aar`（与 ASR 相同） |
| so | `libsherpa-onnx-jni.so`（arm64-v8a / x86_64） |
| 任务 | `./gradlew :app:downloadSherpaOnnxAar` |
| ABI | `arm64-v8a`, `x86_64` |

## 配置键（DataStore）

| Key | 说明 | 默认 |
|-----|------|------|
| `tts_enabled` | 启用 TTS | `false` |
| `tts_model_dir` | 模型目录（一等公民） | 空 |
| `tts_model_path` | 兼容旧单路径；`modelDir` 空时回退 | 空 |
| `tts_reference_audio` | 参考音（本阶段 OfflineTts 未用） | 空 |
| `tts_voice_id` | 说话人 id（可解析为 sid 整数） | `lanxin` |

## 模型布局（外置 `LanXin/tts/...`）

### Matcha（推荐：matcha-icefall-zh-baker 女声）

```
LanXin/tts/matcha-icefall-zh-baker/
  model-steps-3.onnx
  tokens.txt
  lexicon.txt
  phone.fst / date.fst / number.fst   # 可选 rule FSTs
  dict/…                              # jieba 字典（中文）
  vocos-22khz-univ.onnx               # 或放在上一级目录
```

Vocoder 官方：

```
https://github.com/k2-fsa/sherpa-onnx/releases/download/vocoder-models/vocos-22khz-univ.onnx
```

### VITS（如 vits-melo-tts-zh_en）

```
LanXin/tts/vits-melo-…/
  model.onnx（或 *melo*.onnx）
  tokens.txt
  lexicon.txt
  espeak-ng-data/   # 若模型需要
```

## Debug 下载

```bash
bash scripts/download-debug-tts.sh
# 另下 vocoder 到同一目录或 LanXin/tts/
```

App 内：设置 → Debug 资源 → TTS matcha-baker（见 `docs/debug-assets.md`）。  
**注意**：当前目录列表不含 vocoder；真合成前请把 `vocos-22khz-univ.onnx` 放到模型目录或 `LanXin/tts/`。

## 产品规则

1. `tts_enabled` **默认 `false`**
2. 关闭：不 load OfflineTts
3. 桌宠会话：未就绪时 `load(getConfig())`；stub 时仍有字幕气泡
4. 大文件不入库

## 测试

```bash
./gradlew :app:downloadSherpaOnnxAar
./gradlew :app:testDebugUnitTest --tests "com.lanxin.android.builtin.voice.*"
```

- JVM **无 so**：`isNativeAvailable()==false`；`SherpaTtsEngine` + `StubTtsEngine` 不崩  
- `native_degraded` 时 `isStub=true`

## 验收 APK 内引擎

```bash
unzip -l app-debug.apk | grep -E 'sherpa|onnxruntime'
# 期望含 libsherpa-onnx-jni.so（arm64-v8a / x86_64）
```
