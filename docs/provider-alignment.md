# 提供商对齐（Provider Alignment）

> 状态：**P0 ✅ + P1 测速 ✅**  
> OpenAI 兼容模型列表自动拉取 / 选模型 UX / 短 completion 测速  
> 对齐对象：AstrBot `ProviderOpenAIOfficial.get_models` / 控制台「拉取模型列表」  
> **不**改动 ChatRouter / needsTools→云端 等本地推理路由规则

---

## 目标

1. OpenAI 兼容提供商可从远端 `GET {apiUrl}/models` 拉取模型 id 列表  
2. 进入提供商详情且 URL 可用时 **自动尝试** 拉一次；失败只提示，**不清空**已有 `platform.model`  
3. 选模型更好用：搜索过滤、完整列表（软上限 200 + 搜索）、当前选中高亮、勾选测速、仍可手输  
4. 对勾选的少量模型做 **极短 completion 探测**（中性 token，非问候语）  
5. 与 Chat / 本地推理路由不打架（仅增强云端提供商配置体验）

---

## 支持矩阵

| ClientType | Chat 路径（既有） | 远程 `/models` | 测速 probe |
|------------|-------------------|----------------|------------|
| OPENAI | Responses API | ✅ | ✅ `chat/completions` |
| GROQ | Groq Chat Completions | ✅ | ✅ |
| OLLAMA | OpenAI Chat Completions | ✅ | ✅ |
| OPENROUTER | OpenAI Chat Completions | ✅ | ✅ |
| CUSTOM | OpenAI Chat Completions | ✅ | ✅ |
| ANTHROPIC | Anthropic Messages | ❌（手动输入） | ❌ |
| GOOGLE | Gemini streamGenerate | ❌（手动输入） | ❌ |
| LANXIN | 兰心 `/api/v1/chat` | ❌（手动输入） | ❌ |

判断逻辑集中在 `ProviderModelListSupport`，与 `ChatRepositoryImpl` 的 `compatibleType` 分支一致。

---

## 配置路径

```
设置 → 提供商列表 → 某平台详情
  ├─ API URL / API Key / 模型（手输，既有）
  ├─ 「从 API 拉取模型列表」（OpenAI 兼容类型可见；进入详情自动尝试一次）
  │     └─ 成功后展示 id 列表 + 搜索框
  │           ├─ 点选 → 写回 platform.model（高亮当前）
  │           └─ 勾选 → 参与测速（默认含当前 model，最多 3 个）
  └─ 「测速（短 completion）」
        └─ 结果仅本页展示：成功/失败 + 总耗时 ms；失败不写坏配置
```

### 列表

- 端点：`ProviderModelListSupport.modelsEndpoint(apiUrl)` → `{apiUrl}/models`  
- 鉴权：可选 Bearer token（与 chat 相同）  
- 失败：可读错误（401 / URL / 不支持类型 / 网络）；保留手输；**不**清空已有 model  
- 列表：搜索过滤；UI 一次最多展示 200 条匹配项，其余靠搜索收窄（不再卡死 24 条预览）

### 测速（P1）

- 端点：`OpenAiModelProbeSupport.chatCompletionsEndpoint(apiUrl)` → `{apiUrl}/chat/completions`  
- 非流式 `stream=false`，`max_tokens=16`，`temperature=0`  
- **Probe prompt（硬性中性，禁止问候语）**：

  ```
  Reply with exactly this token and nothing else: ping-lx-1
  ```

  - 期望回显：`ping-lx-1`（常量 `OpenAiModelProbeSupport.PROBE_EXPECTED_TOKEN`）  
  - **禁止**使用 `hello` / `你好` / `hi` / `hey` 等问候作 probe（单测回归）  
- 目标选择：`resolveProbeTargets` — 优先当前 `platform.model`，再并入勾选项，上限 `MAX_PROBE_MODELS = 3`，并发 `MAX_PROBE_CONCURRENCY = 2`  
- 指标：总耗时（request 往返 ms）；成功 = HTTP 2xx 且 content 含期望 token  
- 结果仅设置页本地 `probeState`；**永不**因失败改写 model / token / url

---

## 核心类型

| 文件 | 职责 |
|------|------|
| `OpenAiModelListClient` | HTTP GET + 解析；失败为 sealed Error |
| `OpenAiModelsResponse` | OpenAI `list` DTO |
| `ProviderModelListSupport` | 类型矩阵 / endpoint / 去重排序 |
| `OpenAiModelProbeSupport` | probe 文案常量、endpoint、过滤、目标选择、错误 humanize |
| `OpenAiModelProbeClient` | 非流式 short completion 探测 |
| `PlatformSettingViewModel` | 自动拉取 + filter/check + probe 状态机 |
| `PlatformSettingScreen` | 搜索 / 高亮 / 勾选 / 测速入口 |

解析兼容（列表）：

1. 标准 envelope：`{"object":"list","data":[{"id":"…"}]}`  
2. 裸数组：`["model-a","model-b"]`（部分网关）

---

## 与 ChatRouter 的边界

- **本切片只动**：云端提供商配置 / 模型 id 选择 / 设置页本地测速  
- **不碰**：`ChatRouter.decide`、`needsTools`、离线本地兜底、`LocalInferenceProvider`  
- 对话时仍用 `PlatformV2.model` 字符串；拉取与测速只是帮用户把该字段写对并验证连通

---

## 验证

- 单测：  
  - `OpenAiModelListClientTest`  
  - `ProviderModelListSupportTest`  
  - `OpenAiModelProbeSupportTest`（含问候词禁令）  
  - `OpenAiModelProbeClientTest`（body / 解析）  
- CI：`.github/workflows/provider-align-verify.yml`

---

## 非目标（后续）

- Anthropic / Google 原生 models 接口  
- 模型能力标签（vision / tools / reasoning）自动探测  
- 默认提供商路由策略重做  
- 首 token 延迟（当前仅总耗时）  
- 本机全量编译
