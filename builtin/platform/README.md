# builtin/platform — 手机平台专属工具

封装**适合在 Android 端执行**的 MCP 工具。

## MCP 工具

| 工具 | 说明 | 默认 |
|------|------|------|
| `clipboard_get` / `clipboard_set` | 系统剪贴板 | 开（无敏感写确认） |
| `app_install_check` | 已安装应用 / 精确查包 | 开 |
| `system_info` | 设备 / 网络 / 电量 | 开 |
| `file_read` / `file_write` / `file_list` | 应用私有目录 / content URI | 开（仅私有） |
| `web_search` | DuckDuckGo 搜索 | **默认关**（设置 → 联网搜索） |
| `app_intent` | Intent 唤起其它 App | 开 |

## 联网搜索（WebSearch）

- 配置：`WebSearchPreferences`（DataStore 键 `web_search_*`）
- 门闸：`WebSearchGate`（关 → 工具列表隐藏 + 调用拒绝）
- UI：设置 → 联网搜索
- 文档：`docs/websearch.md`
- **不改** ChatRouter / needsTools 首轮语义

## 代码位置

```
app/src/main/kotlin/com/lanxin/android/builtin/platform/
├── PlatformPlugin.kt
├── di/PlatformModule.kt
├── data/WebSearchPreferences.kt
├── domain/WebSearchConfig.kt · WebSearchSettings.kt · WebSearchGate.kt
├── presentation/WebSearchScreen.kt · WebSearchViewModel.kt
└── tools/
    ├── ClipboardTool.kt
    ├── AppInstallCheckTool.kt
    ├── SystemInfoTool.kt
    ├── FileOpsTool.kt
    ├── WebSearchTool.kt
    └── AppIntentTool.kt
```
