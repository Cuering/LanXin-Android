# Architecture：快速陪伴 + 记忆编辑

> 文档角色：**进度锚点 / 架构备忘**（独立于 `main` 已合功能）  
> 代码分支意图：`feat/fast-companion-memory-gpu`  
> 更新：2026-07-21

## 1. 目标

全屏陪伴（Companion）在 **红米 K70 等 Adreno 机** 上尽量低延迟：

1. 本地脑 OpenCL 优先，失败回落 CPU  
2. 进页预热，避免首句冷启动  
3. 精简记忆注入（短预算、仅稀疏检索）  
4. 更短 `maxTokens` / 超时  
5. **记忆文字可编辑**（列表 / 聊天引用进编辑 Dialog），改完 **reindex**

**明确非目标**

- 不改 Chat 全量 `injectMemoryIntoLastUserMessage`  
- 本地不做 tool_call  
- 本分支不做真 token 流式（仍整段 generate）

## 2. 组件图

```
CompanionScreen / CompanionViewModel
        │ ensureReady()
        ▼
LocalInferenceBootstrap.ensureReady
        │
        ▼
MnnLocalLlmEngine → MnnNativeBridge (JNI)
        │ load: OpenCL(thread=6,low) → CPU fallback
        ▼
用户说话 → LocalAwarePetChatResponder.respond
        │
        ├─ CompanionMemoryEnricher.enrich
        │         └─ MemoryInjector.injectForCompanion
        │                   (BM25/LIKE, ≤2 条, ≤280 字, 无判断包)
        │
        └─ LocalInferenceProvider.completeAsApiState
                  maxTokens=64, timeout=20s
                  → LocalReplySanitizer
                  → 失败 / 超时 → StubPetChatResponder
```

## 3. 记忆编辑路径

```
MemoryScreen
  ├─ MemoryCard 点击 / 编辑图标 → openEditDialog
  ├─ 聊天 ChatRef 记忆芯片 → Route.MEMORY_EDIT/{id} → openEditById
  └─ AddMemoryDialog（标题「编辑记忆」）
           │ 改 content / type / importance
           ▼
MemoryViewModel.updateMemory
           ▼
MemoryRepository.updateMemory  (+ reindex 该条)
MemoryRepository.updateMemoryContent(id, text)  // 仅改文字 API
           ▼
Room MemoryDao.updateMemory
           +
MemoryIndexRebuilder.reindex  // 语义/稀疏索引跟上新文案
```

| 入口 | 行为 |
|------|------|
| 记忆列表卡片 | 点卡片或 ✎ → Dialog 改文字 |
| 聊天引用芯片 | 导航 `memory_edit/{id}` → 同 Dialog |
| 保存 | trim 非空校验；snackbar「记忆文字已更新」 |

## 4. 关键文件

| 路径 | 职责 |
|------|------|
| `app/.../cpp/mnn_lanxin_jni.cpp` | OpenCL → CPU load；`set_config` runtime |
| `.../pet/domain/LocalAwarePetChatResponder.kt` | 快速陪伴主路径 |
| `.../pet/domain/CompanionMemoryEnricher.kt` | 精简记忆端口 |
| `.../pet/presentation/CompanionScreen.kt` | 进页预热 bootstrap |
| `.../memory/domain/memory/MemoryInjector.kt` | `injectForCompanion` |
| `.../memory/data/memory/MemoryRepository.kt` | update + reindex |
| `.../memory/presentation/ui/memory/*` | 编辑 UI |
| `docs/fast-companion.md` | 产品/延迟旋钮摘要 |
| `docs/architecture-fast-companion.md` | **本文件** |

## 5. 延迟旋钮（当前值）

| 项 | 值 |
|----|-----|
| `COMPANION_MAX_TOKENS` | 64 |
| `COMPANION_TIMEOUT_MS` | 20_000 |
| 记忆条数 / 字数 | 2 / 280 |
| JNI `thread_num` | 6 |
| precision / memory | low / low |
| `reuse_kv` | true（config 侧） |
| backend | opencl → cpu |

## 6. 交付进度清单

| 项 | 状态 |
|----|------|
| OpenCL 优先 + CPU 回落 | ✅ 本地 diff |
| 进页 `ensureReady` 预热 | ✅ |
| `injectForCompanion` + Enricher | ✅ |
| maxTokens 64 / timeout 20s | ✅ |
| 单测：enrich 进 prompt、旋钮上限 | ✅ |
| 记忆列表/引用可编辑文字 | ✅（原有 UI + 文案增强） |
| `updateMemory` / `addMemory` reindex | ✅ |
| `updateMemoryContent` API | ✅ |
| 单测：编辑 reindex | ✅ |
| architecture 文档 | ✅ 本文件 |
| 合入 main / PR | ⏳ 未推 |

## 7. 验证建议

```bash
# 单测（JVM）
./gradlew :app:testDebugUnitTest --tests '*LocalAwarePetChatResponderTest*'
./gradlew :app:testDebugUnitTest --tests '*MemoryImportReindexTest*'

# 真机 logcat
# MnnLanxinJni: nativeLoadModel ok label=opencl
```

K70：light 档 0.5B/1.5B + `libMNN_CL.so`；进陪伴页等预热后再首句。

## 8. 后续可选

- 真 token 流式（体感）  
- 陪伴侧可点「精简记忆」跳转编辑  
- 设置页暴露 thread/backend（现 JNI 写死）  
- Chat 注入预算与陪伴共享配置源  
