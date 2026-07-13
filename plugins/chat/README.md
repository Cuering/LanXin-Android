# plugins/chat — 对话管理

> 插件 ID: `lanxin.chat`  
> Phase 1：从 `app/data` 搬迁至插件结构

## 职责

- 本地聊天历史（Room：`chat` / `chat_v2`）
- 消息 CRUD、搜索、导出
- 实现 `ChatHistoryProvider`（定义于 `core/engine`）
- 备份贡献：`chat` / `chat_v2` 数据库文件

## 结构

```
plugins/chat/
├── ChatPlugin.kt
├── data/
│   ├── ChatDatabase.kt / ChatDatabaseV2.kt
│   ├── ChatDatabaseV2Migrations.kt
│   ├── ChatRepository.kt / ChatRepositoryImpl.kt
│   ├── ChatHistoryProviderImpl.kt
│   ├── AttachmentUploadCoordinator.kt
│   ├── GroqReasoningParser.kt
│   ├── dao/
│   └── entity/
└── di/
    ├── DatabaseModule.kt
    ├── ChatRepositoryModule.kt
    └── ChatModule.kt
```

## 接口

```kotlin
interface ChatHistoryProvider {
    suspend fun search(query: String): List<MessageV2>
    suspend fun export(): File
}
```

## 注意

- `completeChat` 等网络对话逻辑仍在 `ChatRepositoryImpl`，provider 实现仍在 `data/network`（core/provider 暂未拆分）
- UI（ChatScreen / ChatViewModel）仍在 `app/presentation`，通过 Hilt 注入 `ChatRepository`
