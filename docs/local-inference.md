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

## 产品方案（已定）

### 总开关

- `local_inference_enabled` **默认 `false`**
- 关闭：不加载 native/so、不占模型内存、不初始化引擎
- 开启：才 load 模型；关闭时立即 unload

### 模型分档（用户自备路径，不进插件市场）

| 档位 | 推荐模型 | 体量 | 内存建议 | 定位 |
|------|----------|------|----------|------|
| **轻量** | Qwen2.5-**0.5B / 1.5B**-Instruct（MNN 量化） | 小 | 8G+ 可试 | 省电、离线兜底、验证链路 |
| **标准（效果向）** | Qwen2.5-**7B**-Instruct **Q4/类 4bit**（MNN） | 约 3.5～5G 权重 + KV | **12G+，16G 推荐** | 认真离线聊、读注入上下文 |
| 不推荐默认 | 7B 满精度 / 14B+ | 过大 | 16G 也易卡 | 仅极客自测 |

放置目录建议：

```
{filesDir}/models/local-llm/
├── light/    # 0.5B / 1.5B
├── standard/ # 7B Q4
└── tokenizer 等附属文件
```

大权重 **禁止提交 git**（已 ignore `*.mnn` 等）。设置页填绝对路径或后续做「选文件」。

### 本地能力边界（与插件关系）

| 能力 | 本地模式 |
|------|----------|
| 纯对话生成 | ✅ |
| **记忆 / 知识库** | ✅ **App 侧检索 + 注入**（不靠模型 tool_call） |
| 插件 UI / 数据 / 管理 | ✅ 照常可用 |
| **tool_call / MCP 工具链** | ❌ 本地不做；完整工具走云端 |
| 注入预算 | 比云端更紧：约 600～1200 字；7B 可略宽于 1.5B |

说明：**不做 tool_call ≠ 插件不能用**。插件系统仍在；仅「模型在对话里主动调度工具」留给云端。

### 与 6.2 / 6.3

- 6.2：仅当开关已开且模型 ready 时，无网才 fallback 本地
- 6.3：ChatRouter 云端 ↔ 本地；需要工具的任务优先云端


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

### 下载与选用（示例，非绑定）

1. 按上表选档：试水 **1.5B**，效果向 **7B Q4**（MNN 转换产物）
2. `adb push model.mnn /sdcard/Download/` 后复制到 `{filesDir}/models/local-llm/...`
3. 设置 → 本地推理：打开总开关 → 填路径 →「加载模型」
4. 16G 内存机可优先尝试 7B 量化；发热/降频属正常，可切回轻量档

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
