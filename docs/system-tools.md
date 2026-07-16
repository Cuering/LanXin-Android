# Phase 7 — System Tools / Device Skills

> 状态：**7.1 骨架 ✅** · **7.2 日历读取 + 精确闹钟 🚧**（`feat/phase7.2-calendar-alarm`）  
> 模块：`builtin/systemtools` + `app/.../builtin/systemtools/*`  
> 产品线：**桌宠 + 操控手机 = 陪伴操控一体**（与 Phase 6 VoiceSession 同一会话，非旁路 App）  
> 与 ChatRouter `needsTools`、桌宠 VoiceSession、MCP 共用统一 `DeviceTool` 契约。

## 1. 目标与范围

让兰心通过**统一 Tool/Skill 接口**调用手机能力——系统日历、闹钟、笔记、用户文件。

| 域 | 能力 | 优先策略 |
|----|------|----------|
| **Calendar** | 读即将到来的事件 / 写简单事件 | 读：`CalendarContract.Instances`（`READ_CALENDAR`）；写：**优先 Intent** 少权限，或 stub |
| **Alarm** | 设置精确闹钟 / Intent 设系统闹钟 / 打开闹钟列表 | **`AlarmManager.setAlarmClock`**（默认）+ `PendingIntent` 广播；可选 `mode=intent` → `AlarmClock.ACTION_SET_ALARM`；应用内提醒继续 `builtin/scheduler` |
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

CalendarGateway ──► AndroidCalendarReader (Instances) | StubCalendarGateway
AlarmClockGateway ──► AndroidAlarmSetter (setAlarmClock) | Fake

SystemToolsPlugin (LanXinPlugin)
    └── registerTool → MCP / Chat tool_call

ChatRouter.needsTools ──► 云端（本地暂无 tool_call）
VoiceSession 预留 tool 钩子（后续接同一 Registry）
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
│   ├── AlarmClockGateway.kt       # setAlarmClock 抽象 + 时间解析
│   ├── CalendarGateway.kt         # 日历 Gateway + 查询参数
│   ├── SystemToolsPermissionStatus.kt
│   └── SystemToolsSettings.kt
├── data/
│   ├── AndroidCalendarReader.kt   # ContentResolver + Instances
│   ├── AndroidAlarmSetter.kt      # setAlarmClock + PendingIntent
│   ├── AndroidSystemToolsPermissionChecker.kt
│   ├── StubCalendarGateway.kt
│   ├── StubNotesStore.kt
│   ├── StubDeviceTools.kt         # DeviceTool 实现 + Registry
│   └── SystemToolsPreferences.kt
├── receiver/
│   └── SystemToolsAlarmReceiver.kt
├── di/
│   └── SystemToolsModule.kt
└── presentation/
    ├── SystemToolsScreen.kt       # 设置 → 系统能力 + 权限引导
    └── SystemToolsViewModel.kt

docs/system-tools.md
builtin/systemtools/README.md
```

## 3. 工具清单

### 已实现（7.1 + 7.2）

| 工具 | 副作用 | 确认 | 说明 |
|------|--------|------|------|
| `alarm_set` | LAUNCH_INTENT | 是* | **默认** `setAlarmClock`；`mode=intent` 构建 `SET_ALARM` 规格 |
| `alarm_show` | LAUNCH_INTENT | 否 | `SHOW_ALARMS` 规格 |
| `calendar_list_upcoming` | READ | 否 | `CalendarContract.Instances`；无 `READ_CALENDAR` → Denied 提示 |
| `calendar_create_event` | WRITE | 是* | stub 写入（Intent 少权限路径后续） |
| `note_create` | WRITE | 是* | 内存笔记 |
| `note_list` | READ | 否 | 列表 |
| `note_append` | WRITE | 是* | 追加 |

\* 当设置「写操作需确认」开启（默认）时，须 `confirmed=true`。

### 规划（后续）

| 工具 | 阶段 | 说明 |
|------|------|------|
| `file_pick` | 7.4 | SAF 选文件 / 树 |
| `file_list` | 7.4 | persistable URI 列表 |
| `file_read_text` | 7.4 | 文本摘要（限字节） |
| `file_write` | 7.4 | 写用户选中目录 / 私有 |
| `file_share` | 7.4 | 分享 Intent |
| （删除） | 7.4 | 必须 `EXPLICIT_APPROVE` |

完整 ID 见 `DeviceToolIds.ALL`。

## 4. 里程碑（对齐 ARCHITECTURE Phase 7.1–7.6）

| 阶段 | 内容 | 状态 |
|------|------|------|
| **7.1 骨架** | `DeviceTool` + 门闸 + Fake/Stub + 单测 + 设置总开关 + 本文档 + CI | ✅ |
| **7.2 闹钟 + 日历** | `setAlarmClock` + 权限引导；日历读 Instances + 确认流 | 🚧 本 PR |
| **7.3 笔记** | 应用内笔记 CRUD + 导出/分享 | 🔜 |
| **7.4 文件** | SAF 授权目录列表、读文本、写/分享/删（确认） | 🔜 |
| **7.5 对话/桌宠一体接入** | ChatRouter `needsTools` + VoiceSession 听→想→**办**→说 | 🔜 |
| **7.6 打磨** | 权限引导 UX、隐私文案、失败降级、审计日志（可选） | 🔜 |

### 7.2 交付清单（本 PR）

- [x] `CalendarGateway` + `AndroidCalendarReader`（Instances，无权限不崩溃）
- [x] `AlarmClockGateway` + `AndroidAlarmSetter`（`setAlarmClock` + Receiver）
- [x] `alarm_set` 默认精确闹钟；`mode=intent` 保留 AlarmClock 规格
- [x] 设置页展示日历 / 精确闹钟权限 + 跳转系统设置
- [x] Manifest：`READ_CALENDAR` + `SystemToolsAlarmReceiver`
- [x] 单测：`AlarmSetterTest` / 日历权限 Denied / 查询参数
- [x] 文档 7.2 标 🚧；CI workflow 覆盖新路径

## 5. 权限表

| 能力 | Manifest / 运行时 | 状态 | 备注 |
|------|-------------------|------|------|
| 精确闹钟 | `SCHEDULE_EXACT_ALARM`（已有） | 7.2 | API 31+ 需 `canScheduleExactAlarms`；设置页引导 |
| 闹钟 Intent | 无 | 7.1+ | `mode=intent` |
| 读日历 | `READ_CALENDAR` | 7.2 | 未授予 → `permission_denied_read_calendar` |
| 写日历 | `WRITE_CALENDAR` 或 Intent | stub | **优先 Intent**（后续） |
| 笔记 | 无 / 私有存储 | stub 内存 | |
| 用户文件 | SAF（用户授权） | 未实现 | 禁止静默全盘扫描 |

## 6. 隐私与安全

1. **默认全关**：总开关 + 分项；用户主动开启。
2. **写/删需确认**：`DeviceToolGate` + `confirmed` 参数。
3. **禁止**：root、改系统分区、无障碍乱点第三方、静默扫敏感路径。
4. **不做**：真机厂商笔记深度集成（本 Phase 范围外）。
5. **不下模型**：本模块无网络模型下载。

## 7. 与现有模块关系

| 模块 | 关系 |
|------|------|
| `builtin/platform` | 剪贴板 / 已装应用 / 系统信息 / 私有 file_ops / 通用 app_intent；**不**替代日历闹钟 |
| `builtin/scheduler` | 应用内精确闹钟 / WorkManager；系统工具闹钟走 `setAlarmClock` + 本模块 Receiver |
| `builtin/local_inference` ChatRouter | `needsTools=true` 时走云端 tool 链，可调用本插件工具（7.5 完整批准 UI） |
| `builtin/pet` VoiceSession | 预留 tool 钩子注释（`VoiceSessionCoordinator` TODO phase7.5），接 `DeviceToolRegistry` |

## 8. 配置键（DataStore）

| Key | 默认 |
|-----|------|
| `system_tools_master_enabled` | `false` |
| `system_tools_calendar_enabled` | `false` |
| `system_tools_alarm_enabled` | `false` |
| `system_tools_notes_enabled` | `false` |
| `system_tools_user_file_enabled` | `false` |
| `system_tools_require_confirm_on_write` | `true` |

## 9. 单测

```bash
./gradlew :app:testDebugUnitTest --tests "com.lanxin.android.builtin.systemtools.*"
```

- `AlarmIntentBuilderTest` — Intent 规格 / 边界
- `AlarmSetterTest` — setAlarmClock / 权限 Denied / intent 模式 / 时间解析
- `CalendarListUpcomingStubTest` — list / create / 权限 Denied / 参数
- `DeviceToolGateTest` — 开关与确认
- `DeviceToolIdsTest` / `SystemToolsConfigTest`

## 10. 明确不做（本 PR）

- 笔记 CRUD 持久化（7.3）
- 用户文件 SAF（7.4）
- VoiceSession 一体接入（7.5）
- 厂商笔记深度集成 / 无障碍乱点 / 系统目录修改
- 服务器下载任何东西
- auto-merge
