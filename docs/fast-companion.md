# 快速陪伴版（全屏陪伴 + 精简记忆 + GPU）

> 分支：`feat/fast-companion-memory-gpu`  
> 范围：**只增强全屏陪伴**；Chat 主线记忆注入 / 云端路由 **不动**。

## 目标

在红米 K70（Adreno + OpenCL）上，全屏陪伴尽量快：

| 能力 | 行为 |
|------|------|
| 本地脑 | 就绪则走 MNN；失败 stub |
| 精简记忆 | 最多 2 条 / ~280 字；**仅稀疏 BM25**，无语义向量、无判断准则包 |
| 语音 | 既有麦克风开关 + ASR/TTS；文字轮 `skipTts` |
| 进页预热 | `CompanionViewModel.ensureReady` → `LocalInferenceBootstrap.ensureReady` |
| GPU | JNI load 先 `backend_type=opencl`，失败回落 `cpu` |

## 延迟旋钮

| 项 | 值 |
|----|-----|
| `COMPANION_MAX_TOKENS` | **64** |
| `COMPANION_TIMEOUT_MS` | **20s** |
| 记忆条数 / 字数 | 2 / 280 |
| 线程 | `thread_num=6` |
| precision / memory | `low` / `low` |
| `reuse_kv` | `true`（配置侧） |

## 代码入口

- `LocalAwarePetChatResponder`：想 → 精简记忆 → 本地短答  
- `CompanionMemoryEnricher` / `MemoryInjector.injectForCompanion`  
- `mnn_lanxin_jni.cpp`：OpenCL → CPU  
- `CompanionViewModel.ensureReady`：预热本地脑  

## 机型建议（K70）

1. 模型用 **light**（0.5B / 1.5B Q4），别默认 7B  
2. 确认 APK 带 `libMNN_CL.so`（`cpu_opencl_vulkan` 包）  
3. 进陪伴页等预热完成再首句  
4. logcat 搜 `MnnLanxinJni`：`nativeLoadModel ok label=opencl` 即 GPU 路径  

## 记忆文字修改

- 记忆列表：点卡片 / ✎ →「编辑记忆」Dialog，**直接改文字**（及类型、重要性）  
- 聊天记忆引用芯片 → `memory_edit/{id}` → 同 Dialog  
- 保存：`MemoryRepository.updateMemory` **reindex 该条**，陪伴/聊天检索读到新文案  
- 另有 `updateMemoryContent(id, text)` 仅改正文 API  

进度与架构细节见 **`docs/architecture-fast-companion.md`**（防丢进度）。

## 非目标（本分支）

- 不改 Chat 的 `injectMemoryIntoLastUserMessage` 全量路径  
- 不做本地 tool_call  
- 不做真 token 流式（仍整段 generate；后续可单开）  
