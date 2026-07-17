# 提供商对齐（Provider Alignment）

> 状态：**P0 ✅** OpenAI 兼容模型列表 + 配置路径说明  
> 对齐对象：AstrBot `ProviderOpenAIOfficial.get_models` / 控制台「拉取模型列表」  
> **不**改动 ChatRouter / needsTools→云端 等本地推理路由规则

---

## 目标

1. OpenAI 兼容提供商可从远端 `GET {apiUrl}/models` 拉取模型 id 列表  
2. 配置路径清晰：设置 → 提供商详情 →「从 API 拉取模型列表」→ 点选应用  
3. 与 Chat / 本地推理路由不打架（仅增强云端提供商配置体验）

---

## 支持矩阵

| ClientType | Chat 路径（既有） | 远程 `/models` |
|------------|-------------------|----------------|
| OPENAI | Responses API | ✅ |
| GROQ | Groq Chat Completions | ✅ |
| OLLAMA | OpenAI Chat Completions | ✅ |
| OPENROUTER | OpenAI Chat Completions | ✅ |
| CUSTOM | OpenAI Chat Completions | ✅ |
| ANTHROPIC | Anthropic Messages | ❌（手动输入） |
| GOOGLE | Gemini streamGenerate | ❌（手动输入） |
| LANXIN | 兰心 `/api/v1/chat` | ❌（手动输入） |

判断逻辑集中在 `ProviderModelListSupport`，与 `ChatRepositoryImpl` 的 `compatibleType` 分支一致。

---

## 配置路径

```
设置 → 提供商列表 → 某平台详情
  ├─ API URL / API Key / 模型（手输，既有）
  └─ 「从 API 拉取模型列表」（OpenAI 兼容类型可见）
        └─ 成功后展示 id 列表，点选 → 写回 platform.model
```

- 端点：`ProviderModelListSupport.modelsEndpoint(apiUrl)` → `{apiUrl}/models`  
- 鉴权：可选 Bearer token（与 chat 相同）  
- 失败：展示短错误文案，保留手输；**不**清空已有 model  
- 列表过长：UI 预览前 24 条，其余提示手输

---

## 核心类型

| 文件 | 职责 |
|------|------|
| `OpenAiModelListClient` | HTTP GET + 解析；失败为 sealed Error |
| `OpenAiModelsResponse` | OpenAI `list` DTO |
| `ProviderModelListSupport` | 类型矩阵 / endpoint / 去重排序 |
| `PlatformSettingViewModel.fetchRemoteModels` | UI 状态机 |
| `PlatformSettingScreen` | 拉取入口 + 点选列表 |

解析兼容：

1. 标准 envelope：`{"object":"list","data":[{"id":"…"}]}`  
2. 裸数组：`["model-a","model-b"]`（部分网关）

---

## 与 ChatRouter 的边界

- **本切片只动**：云端提供商配置 / 模型 id 选择  
- **不碰**：`ChatRouter.decide`、`needsTools`、离线本地兜底、`LocalInferenceProvider`  
- 对话时仍用 `PlatformV2.model` 字符串；拉取只是帮用户把该字段写对

---

## 验证

- 单测：`OpenAiModelListClientTest`、`ProviderModelListSupportTest`  
- CI：`.github/workflows/provider-align-verify.yml`

---

## 非目标（后续）

- Anthropic / Google 原生 models 接口  
- 模型能力标签（vision / tools / reasoning）自动探测  
- 默认提供商路由策略重做  
- 本机全量编译
