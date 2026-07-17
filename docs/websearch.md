# 联网搜索（WebSearch / web_search）

> 状态：**配置 + 门闸 ✅**（`feat/websearch-config`）  
> 模块：`builtin/platform` · 工具 `web_search` · DataStore 键前缀 `web_search_`  
> 与 ChatRouter `needsTools`：**不打架**——有工具 ≠ 首轮强制云端；仅 tool_call 循环才 `needsTools=true`。

## 1. 目标

补齐「**配置开关 + 实际可用工具链**」：

| 层 | 行为 |
|----|------|
| **设置** | 设置 → 联网搜索：总开关 / 默认条数 / 区域码；**默认关** |
| **Agent 可见性** | 关：`WebSearchGate.filterTools` 从 prompt 工具列表移除 `web_search` |
| **执行** | 关：handler 返回 `code=web_search_disabled`；开：DuckDuckGo Instant Answer + lite HTML |
| **路由** | 不改 ChatRouter；不在首轮因注册了 web_search 而 `needsTools=true` |

## 2. 架构

```
设置页 WebSearchScreen
        │ DataStore (web_search_*)
        ▼
  WebSearchSettings / WebSearchPreferences
        │
        ├─ ChatViewModel.resolvePersonaFilteredTools
        │       └─ WebSearchGate.filterTools  → 系统提示工具列表
        │
        └─ PlatformPlugin.web_search handler
                └─ WebSearchGate.denyIfDisabled → WebSearchTool.search
```

### 代码位置

```
app/src/main/kotlin/com/lanxin/android/builtin/platform/
├── PlatformPlugin.kt              # 注册 web_search + 门闸
├── data/WebSearchPreferences.kt   # DataStore
├── domain/
│   ├── WebSearchConfig.kt
│   ├── WebSearchSettings.kt
│   └── WebSearchGate.kt
├── presentation/
│   ├── WebSearchScreen.kt
│   └── WebSearchViewModel.kt
├── di/PlatformModule.kt           # Binds WebSearchSettings
└── tools/WebSearchTool.kt         # 既有搜索实现
```

## 3. 默认与隐私

- **默认关**（安全默认）
- 查询发往 DuckDuckGo；无 API Key
- 不缓存结果到远程服务器
- 不下载大模型、不改 needsTools 产品规则

## 4. 单测 / CI

- `WebSearchGateTest`：开关过滤 + deny
- `WebSearchLiteHtmlTest`：lite HTML 解析
- `PlatformToolHelpersTest`：工具名含 `web_search`
- Workflow：`.github/workflows/websearch-verify.yml`

## 5. 非目标（本 PR）

- Tavily / 其它付费搜索后端
- 结果落库 / 知识库自动入库
- 本机全量 Gradle 编译（验证走 CI）
