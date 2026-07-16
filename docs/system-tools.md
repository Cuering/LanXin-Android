# Phase 7 — System Tools / Device Skills

> 状态：**7.1 骨架 ✅** · **7.2 闹钟 Intent + 日历读/写 🚧**（`feat/phase7.2-calendar-alarm`）  
> 模块：`builtin/systemtools` + `app/.../builtin/systemtools/*`  
> 产品线：**桌宠 + 操控手机 = 陪伴操控一体**（与 Phase 6 VoiceSession 同一会话，非旁路 App）  
> 与 ChatRouter `needsTools`、桌宠 VoiceSession、MCP 共用统一 `DeviceTool` 契约。

## 1. 目标与范围

让兰心通过**统一 Tool/Skill 接口**调用手机能力——系统日历、闹钟、笔记、用户文件。

| 域 | 能力 | 优先策略 |
|----|------|----------|
| **Calendar** | 读即将到来的事件 / 写简单事件 | 读：`CalendarContract.Instances`（`READ_CALENDAR`）；写：**优先 INSERT Intent**（`Events.CONTENT_URI`），`mode=stub` 内存 |
| **Alarm** | 设置精确闹钟 / Intent 设系统闹钟 / 打开闹钟列表 | **默认** `AlarmManager.setAlarmClock`；`mode=intent` → **`startActivity(SET_ALARM)`**；`alarm_show` → **`startActivity(SHOW_ALARMS)`** |
| **Notes** | 创建 / 列表 / 追加 | Android **无统一系统笔记 API** → 内置轻量笔记 + 可选 `CREATE_DOCUMENT` / 分享 Intent |
| **User File** | 列表 / 读文本 / 写 / 分享 / 删 | **仅用户可访问文件**：SAF + MediaStore + `getExternalFilesDir`；禁止 root / 改 `/system` |

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
│   ├── AlarmIntentBuilder.kt      # AlarmClock Intent 规格
│   ├── CalendarIntentBuilder.kt   # INSERT 日历事件 Intent 规格
│   ├── AlarmClockGateway.kt
│   ├── CalendarGateway.kt         # list + create 结果密封类
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
│   └── SystemToolsPreferences.kt
├── receiver/
│   └── SystemToolsAlarmReceiver.kt
├── di/
│   └── SystemToolsModule.kt
└── presentation/
    ├── SystemToolsScreen.kt       # 设置 + 权限状态/引导
    └── SystemToolsViewModel.kt
```

## 3. 工具清单

### 已实现（7.1 + 7.2）

| 工具 | 副作用 | 确认 | 说明 |
|------|--------|------|------|
| `alarm_set` | LAUNCH_INTENT | 是* | **默认** `setAlarmClock`；`mode=intent` → **真 startActivity SET_ALARM** |
| `alarm_show` | LAUNCH_INTENT | 否 | **真 startActivity SHOW_ALARMS** |
| `calendar_list_upcoming` | READ | 否 | `CalendarContract.Instances`；无权限 → Denied |
| `calendar_create_event` | WRITE | 是* | **默认 INSERT Intent**；`mode=stub` 内存写入 |
| `note_create` | WRITE | 是* | 内存笔记 |
| `note_list` | READ | 否 | 列表 |
| `note_append` | WRITE | 是* | 追加 |

\* 当设置「写操作需确认」开启（默认）时，须 `confirmed=true`（经 `DeviceToolGate`）。

### 规划（后续）

| 工具 | 阶段 | 说明 |
|------|------|------|
| `file_*` | 7.4 | SAF |
| （删除） | 7.4 | 必须 `EXPLICIT_APPROVE` |

## 4. 里程碑

| 阶段 | 内容 | 状态 |
|------|------|------|
| **7.1 骨架** | DeviceTool + 门闸 + Stub + 设置 | ✅ |
| **7.2 闹钟 + 日历** | Intent 真启动 + setAlarmClock；日历 Instances + INSERT Intent + 确认流 + 权限引导 | 🚧 本 PR |
| **7.3 笔记** | 应用内笔记 CRUD | 🔜 |
| **7.4 文件** | SAF | 🔜 |
| **7.5 一体接入** | ChatRouter + VoiceSession tool 钩子 | 🔜 |
| **7.6 打磨** | UX / 审计 | 🔜 |

### 7.2 交付清单

- [x] `CalendarGateway` + `AndroidCalendarReader`（Instances，无权限不崩溃）
- [x] `CalendarIntentBuilder` + create → **INSERT Intent startActivity**
- [x] `AlarmClockGateway` + `AndroidAlarmSetter`（setAlarmClock + Receiver）
- [x] `SystemToolsIntentLauncher`：alarm_set(mode=intent) / alarm_show **真启动**
- [x] 写操作经 `DeviceToolGate.confirmed`
- [x] 设置页日历 / 精确闹钟权限状态 + 跳转
- [x] Manifest：`READ_CALENDAR` + Receiver
- [x] 单测：AlarmSetter / Calendar create intent / Gate 确认流
- [x] 文档 + CI surface check

## 5. 权限表

| 能力 | Manifest / 运行时 | 状态 | 备注 |
|------|-------------------|------|------|
| 精确闹钟 | `SCHEDULE_EXACT_ALARM` | 7.2 | 设置页引导 |
| 闹钟 Intent | 无 | 7.2 | startActivity |
| 读日历 | `READ_CALENDAR` | 7.2 | Denied 不崩溃 |
| 写日历 | **Intent INSERT**（默认） | 7.2 | 不申请 WRITE_CALENDAR |
| 笔记 | 私有 stub | stub | |
| 用户文件 | SAF | 未实现 | |

## 6. 隐私与安全

1. **默认全关**：总开关 + 分项。
2. **写/删需确认**：`DeviceToolGate` + `confirmed`。
3. **禁止**：root、改系统分区、无障碍乱点、静默扫敏感路径。
4. **不下模型**。

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

## 9. 明确不做（本 PR）

- 笔记 CRUD 持久化（7.3）
- 用户文件 SAF（7.4）
- VoiceSession 一体接入（7.5）
- WRITE_CALENDAR ContentProvider 直写
- 服务器下载 / auto-merge
