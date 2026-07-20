# 本地推理引擎（Phase 6.1–6.3 + P2 MNN）

> 分支：`feat/p2-mnn-local-llm`  
> 状态：**6.1 ✅ 骨架 · 6.2 ✅ 离线兜底 · 6.3 ✅ ChatRouter · P2 ✅ MNN 真引擎进 APK**

## 目标

落地端侧 LLM 推理抽象层，并在 **无网络且本地就绪** 时自动 fallback 到本地小模型；
Phase 6.3 将云端 ↔ 本地切换提升为 **一等公民 ChatRouter**；
**P2** 将官方 MNN 预编译 so + 薄 JNI 打进 APK，真机可跑 `Llm::createLLM` / `response`。

## 能力边界

| 能力 | 状态 |
|------|------|
| `LocalLlmEngine` 接口（load / unload / generate / stream / isReady / isAvailable） | ✅ 6.1 |
| `StubLocalLlmEngine`（无 MNN so，路径校验 + stub 回复） | ✅ 6.1（单测保留） |
| `MnnNativeBridge` JNI 接入点 | ✅ **P2 真实现**（load/generate/unload） |
| `MnnLocalLlmEngine`（native 失败降级 stub） | ✅ **P2** · Hilt 默认绑定 |
| DataStore 配置（启用 / 模型路径 / maxTokens / preferLocal） | ✅ 6.1 |
| `LocalInferenceProvider` → `ApiState` 流 | ✅ 6.1 |
| `InferenceRouteSelector` 纯逻辑路由（现委托 ChatRouter） | ✅ 6.1 / 6.3 |
| 设置页 UI「本地推理」 | ✅ 6.1 |
| `NetworkStatusProvider`（ConnectivityManager） | ✅ **6.2** |
| `InferenceRouteCoordinator` 设置+引擎+网络 | ✅ **6.2** / 6.3 门面 |
| `ChatRepositoryImpl` 接入 fallback | ✅ **6.2** |
| 无网 + 本地就绪 → 自动本地 | ✅ **6.2** |
| 无网 + 本地不可用 → 明确错误引导 | ✅ **6.2** |
| 状态文案「本地离线生成中…」 | ✅ **6.2** |
| 设置页路由预览 | ✅ **6.2** / 6.3 增强 |
| **`ChatRouter` 统一决策 + reason 码** | ✅ **6.3** |
| **needsTools → 优先云端** | ✅ **6.3** |
| **forceLocal 会话级强制本地** | ✅ **会话本地模型** |
| **真实 MNN so + 薄 JNI 进 APK** | ✅ **P2** |
| 量化模型权重打包 | ❌ 外置用户自备（不进 git） |
| 本地 tool_call | ❌ 不做（产品边界） |
| 真 token 流式回调 | 🔜 后续（现整段 generate 后一次 emit） |

## Phase 6.3 ChatRouter

### 单一入口

```
ChatRouteContext(preferLocal, localReady, network, needsTools, cloudAvailable?)
        │
        ▼
ChatRouter.decide() → InferenceRouteDecision(target, reason)
        │
        ├─ LOCAL / CLOUD / UNAVAILABLE
        └─ reason: prefer_local | offline_local | need_tools_cloud |
                   offline_local_unavailable | default_cloud | ...
```

### 产品规则（严格）

0. **forceLocal**（新建会话勾选「本地模型」）→ 就绪 LOCAL（`force_local`）/ 未就绪 UNAVAILABLE（`force_local_unavailable`）；**覆盖 needsTools / preferLocal / 默认云端**
1. 开关默认关 / 引擎未 ready → 不走本地  
2. **需要 tool_call / MCP 工具**（工具 follow-up 轮 / 显式 needsTools）且云端可选 → **CLOUD**（`need_tools_cloud`）；首轮纯对话可 preferLocal  
3. preferLocal + ready（且无 tool 强制）→ LOCAL（`prefer_local`）  
4. 无网 + ready → LOCAL（`offline_local`）  
5. 无网 + 未就绪 → UNAVAILABLE（`offline_local_unavailable`，引导设置）  
6. 有网默认云端（`default_cloud`）

### 与 6.2 关系

- 6.2：`InferenceRouteCoordinator` + Repository 最小侵入  
- 6.3：决策收敛到 `ChatRouter`；Selector/Coordinator 为薄委托 / 门面；Chat 传入 `needsTools`

### Chat 接入

```
ChatViewModel.runChatWithTools
        │  needsTools = 有注册工具；本地时 remainingRounds=0
        │  forceLocal = 会话包含本地模型哨兵
        ▼
ChatRepositoryImpl.completeChat(..., needsTools, forceLocal)
        │  InferenceRouteCoordinator.decide(needsTools, forceLocal) → ChatRouter
        ├─ LOCAL        → LocalInferenceProvider.completeAsApiState
        ├─ UNAVAILABLE  → ApiState.Error（引导去设置）
        └─ CLOUD        → completeChatCloud（OpenAI/…）
```

### 新建会话可选本地模型

新建会话对话框始终展示「本地模型」行：
- **就绪**（引擎已 load + 开关开）→ 可选
- **未就绪** → 灰显，文案引导去设置

勾选本地模型后，会话 `enabledPlatforms` 包含哨兵 uid `__local_model__`，触发 `forceLocal` 路由，覆盖 needsTools / preferLocal / 默认云端。

`ChatViewModel.forceLocal`：从会话槽位解析，不走全局开关。`resolvePlatformForUid` 识别本地哨兵 uid 时合成 `PlatformV2` 供 completeChat 槽位使用。

`localReady` **仅** `engine.isReady`（已 load），不是「开关开了就算」。

### Reason 码

| Code | 含义 |
|------|------|
| `force_local` | 会话显式选中本地模型且就绪 |
| `force_local_unavailable` | 会话强制本地但引擎未就绪 |
| `prefer_local` | 用户偏好本地且就绪 |
| `offline_local` | 无网 + 本地就绪 |
| `need_tools_cloud` | 需要工具 → 云端 |
| `offline_local_unavailable` | 无网 + 本地未就绪 |
| `default_cloud` | 有网默认云端 |
| `local_only_available` | 仅本地可选 |
| `no_provider` | 均不可用 |
| `cloud_default_no_router` | 未注入路由组件 |

## Phase 6.2 行为（保留）

### 网络检测

`ConnectivityNetworkStatusProvider`：`activeNetwork` + `NET_CAPABILITY_INTERNET` + `VALIDATED`  

### 错误文案（无网且本地不可用）

> 当前无网络，且本地推理不可用。请到「设置 → 本地推理」打开开关、填写模型路径并加载模型后再试。

## 产品方案（已定）

### 总开关（产品硬约束）

- **保留独立开关**，勿与其它「智能能力」合并或删除
- `local_inference_enabled` **默认 `false`**（**不**抬成默认 ON）
- 关闭：不加载 native/so、不占模型内存、不初始化引擎；关闭时立即 unload
- 开启 + 路径/资源就绪：设置页 **自动 `engine.load`**；对话路径 `DefaultLocalInferenceProvider` 调用前也会 auto-load
- 路径空 / load 失败：明确 snackbar 或 `ApiState.Error`（引导一键下载/导入）
- 路径回落 / heal：桌宠设置 `healModelPathsIfNeeded` 探测 `LanXin/models/local-llm/light/` 并回写 DataStore

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

大权重 **禁止提交 git**。设置页填绝对路径。

### 本地能力边界（与插件关系）

| 能力 | 本地模式 |
|------|----------|
| 纯对话生成 | ✅ |
| **记忆 / 知识库** | ✅ **App 侧检索 + 注入**（不靠模型 tool_call） |
| 插件 UI / 数据 / 管理 | ✅ 照常可用 |
| **tool_call / MCP 工具链** | ❌ 本地不做；完整工具走云端 |
| 注入预算 | 比云端更紧：约 600～1200 字；7B 可略宽于 1.5B |

## 模块结构

```
app/src/main/kotlin/com/lanxin/android/builtin/localinference/
├── domain/
│   ├── LocalLlmEngine.kt
│   ├── LocalInferenceProvider.kt
│   ├── LocalInferenceSettings.kt
│   ├── LocalInferenceModels.kt
│   ├── ChatRouter.kt                 ← 6.3 统一决策
│   ├── InferenceRouteSelector.kt     ← 委托 ChatRouter
│   ├── InferenceRouteCoordinator.kt  ← 运行时门面
│   ├── NetworkStatusProvider.kt
│   └── ChatLocalFallback.kt
├── data/
│   ├── StubLocalLlmEngine.kt         ← 单测保留
│   ├── MnnLocalLlmEngine.kt          ← P2 默认引擎（native / stub 降级）
│   ├── MnnNativeBridge.kt            ← P2 真 JNI
│   ├── LocalInferencePreferences.kt
│   ├── DefaultLocalInferenceProvider.kt
│   └── ConnectivityNetworkStatusProvider.kt
├── di/
│   └── LocalInferenceModule.kt       ← @Binds MnnLocalLlmEngine
└── presentation/
    ├── LocalInferenceScreen.kt
    └── LocalInferenceViewModel.kt

app/src/main/cpp/
├── CMakeLists.txt                    ← arm64 链 libllm；x86_64 stub
├── mnn_lanxin_jni.cpp
└── mnn_lanxin_jni_stub.cpp

third_party/mnn/
├── include/                          ← MNN + llm headers
└── NOTICE                            ← Apache-2.0

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
{filesDir}/models/local-llm/light/
├── config.json
├── *.mnn
└── tokenizer.*
```

单测可用虚拟路径：`stub://demo-model`（`MnnNativeBridge` 认作合法，不走 native）。

## P2 MNN 真引擎

### 构建期

- `app/build.gradle.kts` → `downloadMnnNative`：拉取 MNN **3.6.0** 官方 Android zip  
  → 解压 `arm64-v8a/*.so` 到 `app/src/main/jniLibs/arm64-v8a/`（**gitignore**）  
- 覆盖：`MNN_NATIVE_ZIP` / `MNN_NATIVE_URL`  
- CMake 编 `libmnn_lanxin.so`；x86_64 用 stub，Kotlin 安全降级  

### 运行时

1. `MnnNativeBridge.tryLoadNative` 按序 load：`c++_shared` → `MNN` → `MNN_Express` → `MNN_CL` / `MNN_Vulkan` / `MNNOpenCV` / `MNNAudio` → `llm` → `mnn_lanxin`（与 libllm NEEDED 对齐；仅 c++_shared 允许缺失）  
2. `nativeLoadModel(path)` → `Llm::createLLM` + `load`（目录下 `config.json` 或 `llm.mnn`）  
3. `nativeGenerate` → `Llm::response`  
4. 失败：`MnnLocalLlmEngine` 路径合法则 **READY + isStub 降级**（`native_degraded:`），不崩  


### 接入路线（历史 / 已完成）

1. ~~引入 `libMNN.so` + CMake / prefab~~ ✅ P2  
2. ~~实现 `MnnNativeBridge.loadModel` / `generate`~~ ✅ P2  
3. ~~新增 `MnnLocalLlmEngine`，Hilt `@Binds` 替换 stub~~ ✅ P2  
4. 真机验证 token 流式 🔜  

## 非目标

- 打包任何大模型文件进 APK / git  
- 本地 tool_call  
- 修改 AstrBot 系统源码  

## 单测

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.lanxin.android.builtin.localinference.*" \
  --tests "com.lanxin.android.data.repository.ChatRepositoryImplTest"
```

覆盖要点（#106 本地脑链路）：

| 用例 | 断言 |
|------|------|
| `LocalInferenceConfigTest` / `DefaultLocalInferenceProviderTest` | **默认 `enabled=false`** |
| `DefaultLocalInferenceProviderTest` · disabled | 明确「未启用」错误，不 load |
| 同上 · path empty | 明确「路径为空」 |
| 同上 · enabled + stub path | **auto-load** → Success |
| `StubLocalLlmEngineTest` / `MnnLocalLlmEngineTest` | load 契约（disabled / empty / ready） |
