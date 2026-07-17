# Phase 7 — System Tools / Device Skills

> 状态：**7.1 骨架 ✅** · **7.2 闹钟 Intent + 日历读/写 ✅** · **7.3 笔记 Room + SAF ✅** · **7.4 用户文件 SAF 🚧**（`feat/phase7.4-file-manager`）  
> 模块：`builtin/systemtools` + `app/.../builtin/systemtools/*`  
> 产品线：**桌宠 + 操控手机 = 陪伴操控一体**（与 Phase 6 VoiceSession 同一会话，非旁路 App）  
> 与 ChatRouter `needsTools`、桌宠 VoiceSession、MCP 共用统一 `DeviceTool` 契约。

## 1. 目标与范围

让兰心通过**统一 Tool/Skill 接口**调用手机能力——系统日历、闹钟、笔记、用户文件。

| 域 | 能力 | 优先策略 |
|----|------|----------|
| **Calendar** | 读即将到来的事件 / 写简单事件 | 读：`CalendarContract.Instances`（`READ_CALENDAR`）；写：**优先 INSERT Intent**（`Events.CONTENT_URI`），`mode=stub` 内存 |
| **Alarm** | 设置精确闹钟 / Intent 设系统闹钟 / 打开闹钟列表 | **默认** `AlarmManager.setAlarmClock`；`mode=intent` → **`startActivity(SET_ALARM)`**；`alarm_show` → **`startActivity(SHOW_ALARMS)`** |
| **Notes** | CRUD / 导出 / 导入 | Android **无统一系统笔记 API** → **Room 私有库** + SAF `CREATE_DOCUMENT` / `OpenDocument` / 分享 Intent |
| **User File** | 列表 / 读文本 / 写 / 分享 / 删 | **仅用户可访问文件**：SAF + 应用 `imports` 目录；禁止 root / 改 `/system` |

### 一体工作流（听 → 想 → 办 → 说）

```
用户对桌宠说话 / 聊天输入 / MCP
        │
        ▼
  听（ASR / 文本）→ 想（ChatRouter / PetChatResponder）
        │
        ├─ 纯闲聊 → 回复 → 说（TTS / 字幕）
        │
        └─ 要办事 → DeviceToolGate（开关 + 确认）
                      ├─ DeviceToolRegistry（alarm / calendar / notes / files）
                      └─ 结果回灌 → 想（总结）→ 说
```

工具给 **Chat 与 VoiceSession 共用**，不是旁路独立 App。

## 2. 架构

```
DeviceTool (interface)
    ├── name / capability / permissions / sideEffect / confirmationLevel
    └── invoke(args, confirmed)

DeviceToolGate
    ├── master switch
    ├── per-capability switch
    └── write/delete confirmation

CalendarGateway ──► AndroidCalendarReader (Instances + INSERT Intent) | StubCalendarGateway
AlarmClockGateway ──► AndroidAlarmSetter (setAlarmClock) | Fake
SystemToolsIntentLauncher ──► AndroidSystemToolsIntentLauncher (startActivity)
NotesStore ──► RoomNotesStore | StubNotesStore
NotesSafGateway ──► AndroidNotesSafGateway (SAF write/read + share)
UserFileCatalog ──► InMemoryUserFileCatalog
UserFileIoGateway ──► AndroidUserFileIoGateway (SAF + imports)

SystemToolsPlugin (LanXinPlugin)
    └── registerTool → MCP / Chat tool_call
```

### 代码位置

```
app/src/main/kotlin/com/lanxin/android/builtin/systemtools/
├── SystemToolsPlugin.kt
├── domain/
│   ├── DeviceModels.kt
│   ├── DeviceTool.kt
│   ├── DeviceToolGate.kt
│   ├── AlarmIntentBuilder.kt
│   ├── CalendarIntentBuilder.kt
│   ├── AlarmClockGateway.kt
│   ├── CalendarGateway.kt
│   ├── NotesStore.kt
│   ├── UserFileStore.kt          # Catalog + IoGateway + sort/mime helpers
│   ├── SystemToolsIntentLauncher.kt
│   ├── SystemToolsPermissionStatus.kt
│   └── SystemToolsSettings.kt
├── data/
│   ├── AndroidCalendarReader.kt
│   ├── AndroidAlarmSetter.kt
│   ├── AndroidSystemToolsIntentLauncher.kt
│   ├── AndroidSystemToolsPermissionChecker.kt
│   ├── StubCalendarGateway.kt
│   ├── StubNotesStore.kt
│   ├── StubDeviceTools.kt
│   ├── SystemToolsPreferences.kt
│   ├── notes/
│   │   ├── NoteEntity.kt
│   │   ├── NoteDao.kt
│   │   ├── NotesDatabase.kt
│   │   ├── RoomNotesStore.kt
│   │   └── AndroidNotesSafGateway.kt
│   └── files/
│       ├── InMemoryUserFileCatalog.kt
│       ├── AndroidUserFileIoGateway.kt
│       └── UserFileDeviceTools.kt   # file_pick/list/read/write/share/delete
├── receiver/
│   └── SystemToolsAlarmReceiver.kt
├── di/
│   └── SystemToolsModule.kt
└── presentation/
    ├── SystemToolsScreen.kt
    └── SystemToolsViewModel.kt
```

## 3. 工具清单

### 已实现（7.1 + 7.2 + 7.3 + 7.4）

| 工具 | 副作用 | 确认 | 说明 |
|------|--------|------|------|
| `alarm_set` | LAUNCH_INTENT | 是* | **默认** `setAlarmClock`；`mode=intent` → **真 startActivity SET_ALARM** |
| `alarm_show` | LAUNCH_INTENT | 否 | **真 startActivity SHOW_ALARMS** |
| `calendar_list_upcoming` | READ | 否 | `CalendarContract.Instances`；无权限 → Denied |
| `calendar_create_event` | WRITE | 是* | **默认 INSERT Intent**；`mode=stub` 内存写入 |
| `note_create` | WRITE | 是* | Room 创建 |
| `note_list` | READ | 否 | 列表（倒序） |
| `note_append` | WRITE | 是* | 追加正文 |
| `note_update` | WRITE | 是* | 覆盖标题/正文 |
| `note_delete` | DELETE | **始终** | `EXPLICIT_APPROVE` |
| `note_export` | WRITE | 是* | `mode=share|saf|preview`；`format=json|markdown` |
| `note_import` | WRITE | 是* | `uri` 或 `json_text`；`strategy=merge|replace` |
| `file_pick` | WRITE | 是* | 登记 SAF Uri；`import=true`（默认）复制到应用 `imports/` |
| `file_list` | READ | 否 | 合并 imports + 目录登记；`sort=date|name|type|size` |
| `file_read_text` | READ | 否 | 读文本（`max_chars` 默认 50000） |
| `file_write` | WRITE | 是* | `mode=app` 写 imports；`mode=saf` 写 content Uri |
| `file_share` | LAUNCH_INTENT | 否 | 分享 Uri 或纯文本 |
| `file_delete` | DELETE | **始终** | imports 实体 / SAF `DocumentsContract.deleteDocument` + 目录登记；`EXPLICIT_APPROVE` |

\* 当设置「写操作需确认」开启（默认）时，须 `confirmed=true`（经 `DeviceToolGate`）。  
删除始终需 `confirmed=true`。

## 4. 里程碑

| 阶段 | 内容 | 状态 |
|------|------|------|
| **7.1 骨架** | DeviceTool + 门闸 + Stub + 设置 | ✅ |
| **7.2 闹钟 + 日历** | Intent 真启动 + setAlarmClock；日历 Instances + INSERT Intent + 确认流 + 权限引导 | ✅ |
| **7.3 笔记** | Room CRUD + SAF 导出/导入 + 设置状态 | ✅ |
| **7.4 文件** | SAF 选取/导入 + imports 列表/读/写/分享/删 | 🚧 |
| **7.5 一体接入** | ChatRouter + VoiceSession tool 钩子 | 🔜 |
| **7.6 打磨** | UX / 审计 | 🔜 |

### 7.4 交付清单

- [x] `UserFileCatalog` + `InMemoryUserFileCatalog`
- [x] `UserFileIoGateway` + `AndroidUserFileIoGateway`（SAF + imports，禁系统路径）
- [x] `takePersistableUriPermission` + `DocumentsContract.deleteDocument`
- [x] `file_pick` / `list` / `read_text` / `write` / `share` / `delete`
- [x] 排序：`date` / `name` / `type` / `size`（纯函数可单测）
- [x] 设置页：OpenDocument 导入、CreateDocument 写文本、最近文件列表/分享
- [x] 单测：`UserFileStoreTest` + Gate 确认 / Registry
- [x] 文档 + CI surface check（7.4 路径）

## 5. 权限表

| 能力 | Manifest / 运行时 | 状态 | 备注 |
|------|-------------------|------|------|
| 精确闹钟 | `SCHEDULE_EXACT_ALARM` | 7.2 | 设置页引导 |
| 闹钟 Intent | 无 | 7.2 | startActivity |
| 读日历 | `READ_CALENDAR` | 7.2 | Denied 不崩溃 |
| 写日历 | **Intent INSERT**（默认） | 7.2 | 不申请 WRITE_CALENDAR |
| 笔记 | 应用私有 Room | 7.3 | 无额外权限 |
| 笔记 SAF | CreateDocument / OpenDocument | 7.3 | 用户显式选文件 |
| 用户文件 | SAF + 应用 imports | 7.4 | 用户显式选文件；无 MANAGE_EXTERNAL_STORAGE |

## 6. 隐私与安全

1. **默认全关**：总开关 + 分项。
2. **写/删需确认**：`DeviceToolGate` + `confirmed`；删除 `EXPLICIT_APPROVE`。
3. **禁止**：root、改系统分区、无障碍乱点、静默扫敏感路径。
4. **不下模型**。
5. **笔记不落公有存储**，仅 Room + 用户主动 SAF。
6. **用户文件**：仅 SAF 用户授权 Uri + 应用 `getExternalFilesDir/imports`；写/删经 Gate；`file_delete` 对 content Uri 走 `DocumentsContract.deleteDocument`，对 imports 走本地删；不申请 `MANAGE_EXTERNAL_STORAGE`。

## 7. 配置键（DataStore）

| Key | 默认 |
|-----|------|
| `system_tools_master_enabled` | `false` |
| `system_tools_calendar_enabled` | `false` |
| `system_tools_alarm_enabled` | `false` |
| `system_tools_notes_enabled` | `false` |
| `system_tools_user_file_enabled` | `false` |
| `system_tools_require_confirm_on_write` | `true` |

## 8. 单测

```bash
./gradlew :app:testDebugUnitTest --tests "com.lanxin.android.builtin.systemtools.*"
```

- `AlarmIntentBuilderTest` / `AlarmSetterTest`（含 intent 真启动 Fake）
- `CalendarListUpcomingStubTest`（list / create intent / stub / Denied）
- `DeviceToolGateTest`（开关 + 确认流）
- `DeviceToolIdsTest` / `SystemToolsConfigTest`
- `NotesStoreTest`（CRUD / Codec / Gate / export-import）
- `UserFileStoreTest`（排序 / catalog / pick-write-list-read-delete / Gate）

## 9. 明确不做（本 PR）

- VoiceSession 一体接入（7.5）
- WRITE_CALENDAR ContentProvider 直写
- 厂商笔记 App 私有协议
- 服务器下载 / auto-merge
- MANAGE_EXTERNAL_STORAGE 全盘扫描
- DocumentTree 持久树递归（可后续增强）
