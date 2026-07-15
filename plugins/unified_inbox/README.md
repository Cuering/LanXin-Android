# plugins/unified_inbox — 跨会话历史 + 跨工作区文件

> 插件 ID: `lanxin.unified_inbox`  
> 状态：Phase 1 核心数据层 + 基础 UI ✅

## 职责

- 聚合 `plugins/chat`（chat_v2）所有本地 session 的对话
- Room 本地归档：`lanxin_unified_inbox.db`，索引 `(platform, session_id, time)`
- 搜索/过滤：关键词、平台、会话
- 跨工作区文件浏览：默认 `filesDir/workspaces/`
- MCP 工具：`inbox_search` / `inbox_reindex` / `workspace_list`
- 可选注入：`CrossSessionHistoryInjector`（默认关闭，风格对齐 MemoryInjector）

## 结构

```
app/src/main/kotlin/com/lanxin/android/plugins/unifiedinbox/
├── UnifiedInboxPlugin.kt
├── data/
│   ├── CrossSessionEntity.kt
│   ├── CrossSessionDao.kt
│   ├── CrossSessionDatabase.kt
│   └── CrossSessionRepository.kt
├── domain/
│   ├── CrossSessionIndexer.kt
│   ├── CrossSessionHistoryInjector.kt
│   └── UnifiedFileBrowser.kt
├── presentation/
│   ├── CrossSessionHistoryScreen.kt
│   ├── CrossSessionHistoryViewModel.kt
│   ├── UnifiedFileBrowserScreen.kt
│   └── UnifiedFileBrowserViewModel.kt
└── di/
    └── UnifiedInboxModule.kt
```

## 入口

设置页 →「跨会话历史」→ `Route.UNIFIED_INBOX` → `CrossSessionHistoryScreen`  
历史页内「文件浏览」→ `Route.UNIFIED_FILE_BROWSER`

## 使用

1. 打开跨会话历史页，点右上角刷新图标，从 chat_v2 全量重建索引
2. 用搜索框 / 平台 Chip / 会话 Chip 过滤
3. 文件浏览默认读取 `filesDir/workspaces/`（应用内目录）

## 约束

- 纯 Android 端，不依赖服务器
- 日志：`android.util.Log`（Indexer / Repository / FileBrowser）
- 备份：贡献 `lanxin_unified_inbox.db`

## 后续

- SAF 选择外部工作区根目录
- 图片缩略图预览（Coil）
- 与 ChatViewModel 联动自动增量索引
- 跨会话注入开关 UI
