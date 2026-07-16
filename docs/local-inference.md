# Phase 6.1 — 本地推理引擎（MNN 骨架）

> 分支：`feat/phase6-1-mnn-local-inference`  
> 状态：**进行中（MVP 可合入）**

## 目标

落地端侧 LLM 推理抽象层，使 Chat 可在后续 6.2/6.3 无缝切换云端 ↔ 本地。

## 能力边界

| 能力 | 状态 |
|------|------|
| `LocalLlmEngine` 接口（load / unload / generate / stream / isReady / isAvailable） | ✅ |
| `StubLocalLlmEngine`（无 MNN so，路径校验 + stub 回复） | ✅ |
| `MnnNativeBridge` JNI 接入点 | ✅ 预留 |
| DataStore 配置（启用 / 模型路径 / maxTokens / preferLocal） | ✅ |
| `LocalInferenceProvider` → `ApiState` 流 | ✅ |
| `InferenceRouteSelector` 纯逻辑路由 | ✅（6.2/6.3 扩展） |
| 设置页 UI「本地推理」 | ✅ |
| 真实 MNN so + tokenizer + 量化模型 | ❌ 后续 |
| 无网络自动切本地（6.2） | ❌ 后续 |
| ChatRouter 完整重构（6.3） | ❌ 后续 |

## 模块结构

```
app/src/main/kotlin/com/lanxin/android/builtin/localinference/
├── domain/
│   ├── LocalLlmEngine.kt
│   ├── LocalInferenceProvider.kt
│   ├── LocalInferenceSettings.kt
│   ├── LocalInferenceModels.kt
│   └── InferenceRouteSelector.kt
├── data/
│   ├── StubLocalLlmEngine.kt
│   ├── MnnNativeBridge.kt
│   ├── LocalInferencePreferences.kt
│   └── DefaultLocalInferenceProvider.kt
├── di/
│   └── LocalInferenceModule.kt
└── presentation/
    ├── LocalInferenceScreen.kt
    └── LocalInferenceViewModel.kt

builtin/local_inference/README.md
docs/local-inference.md   ← 本文
```

## 配置键（DataStore）

| Key | 说明 | 默认 |
|-----|------|------|
| `local_inference_enabled` | 启用本地推理 | `false` |
| `local_inference_model_path` | 模型路径（目录或文件） | 空 |
| `local_inference_max_tokens` | 生成上限 | `512` |
| `local_inference_temperature` | 温度 | `0.7` |
| `local_inference_prefer_local` | 路由偏好本地 | `false` |

## 模型文件（勿提交 git）

推荐放置：

```
{filesDir}/models/local-llm/
├── model.mnn          # 或其它权重布局
└── tokenizer.json     # 视实现而定
```

`.gitignore` 已忽略 `*.onnx`；建议额外忽略 `*.mnn` / 大权重。设置页填写**绝对路径**。

单测可用虚拟路径：`stub://demo-model`（`MnnNativeBridge` 认作合法）。

### 下载（示例，非绑定）

1. 自备量化小模型（如 Qwen/Phi 等 MNN 转换产物）
2. `adb push model.mnn /sdcard/Download/` 后复制到 app filesDir
3. 在设置 → 本地推理 填入路径并「加载模型」

## 与 Chat / Provider 对接

```
ChatViewModel / ChatRepository
        │
        ├─ 云端：OpenAI / Anthropic / …（现有）
        │
        └─ 本地候选：LocalInferenceProvider.completeAsApiState()
                    └─ LocalLlmEngine.stream / generate
```

6.1 **不改** `ChatRepositoryImpl.completeChat` 主路径；Provider 可注入供手动或后续 Router 调用。

路由预览（`InferenceRouteSelector`）：

1. `preferLocal && localAvailable` → LOCAL  
2. `!network && localAvailable` → LOCAL（离线兜底预览）  
3. `cloudAvailable` → CLOUD  
4. `localAvailable` → LOCAL  
5. 否则 UNAVAILABLE  

## MNN 接入路线（后续）

1. 引入 `libMNN.so` + CMake / prefab  
2. 实现 `MnnNativeBridge.loadModel` / `generate`  
3. 新增 `MnnLocalLlmEngine`，Hilt `@Binds` 替换 stub  
4. 真机验证 token 流式  

参考：

- [alibaba/MNN](https://github.com/alibaba/MNN)  
- 妹居 MeiJu（若可访问）：本地推理封装  
- 本仓 knowledge 的 ONNX 懒加载模式（`OnnxEmbeddingService`）

## 非目标（6.1）

- 打包任何大模型文件进 APK / git  
- ASR / TTS / 桌宠（6.4–6.7）  
- 修改 AstrBot 系统源码  
